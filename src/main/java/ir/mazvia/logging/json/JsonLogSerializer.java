package ir.mazvia.logging.json;

import ir.mazvia.logging.context.LogContext;
import ir.mazvia.logging.model.LogEvent;
import ir.mazvia.logging.model.ServiceIdentity;
import ir.mazvia.logging.model.StackFrameInfo;
import ir.mazvia.logging.model.ThrowableInfo;
import ir.mazvia.logging.spi.LogSerializer;
import ir.mazvia.logging.spi.SensitiveDataMasker;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * تبدیل {@link LogEvent} به یک خط JSON با ساختار ثابت (قالب استاندارد پلتفرم).
 *
 * <p>ترتیب و نام فیلدها: {@code timestamp, level, stream, index_prefix, service, module, env, instance,
 * logger, thread, caller?, message, trace_id?, span_id?, request_id?, correlation_id?, ctx?, fields?, error?}.
 * همهٔ متن‌ها و مقادیر قبل از نوشتن از masker می‌گذرند. هر رشته حداکثر {@value #MAX_STRING_CHARS}
 * کاراکتر است. این کلاس هرگز exception نمی‌اندازد؛ در خطای داخلی یک رویداد حداقلی و بی‌خطر می‌نویسد.
 */
public final class JsonLogSerializer implements LogSerializer {

    static final int MAX_STRING_CHARS = 32_768;
    private static final int MAX_ERROR_DEPTH = 25;
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final ServiceIdentity identity;
    private final SensitiveDataMasker masker;

    public JsonLogSerializer(ServiceIdentity identity, SensitiveDataMasker masker) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.masker = Objects.requireNonNull(masker, "masker");
    }

    @Override
    public byte[] serialize(LogEvent event) {
        StringBuilder sb = new StringBuilder(512);
        try {
            write(sb, event);
        } catch (RuntimeException e) {
            sb.setLength(0);
            writeFallback(sb, event, e);
        }
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void write(StringBuilder sb, LogEvent e) {
        JsonWriter w = new JsonWriter(sb, MAX_STRING_CHARS);
        w.beginObject();
        w.name("timestamp").value(TIMESTAMP.format(e.timestamp()));
        w.name("level").value(e.level().name());
        w.name("stream").value(e.stream().name());
        w.name("index_prefix").value(identity.indexPrefixFor(e.stream()));
        w.name("service").value(identity.service());
        w.name("module").value(identity.module());
        w.name("env").value(identity.environment());
        w.name("instance").value(identity.instanceId());
        w.name("logger").value(e.loggerName());
        w.name("thread").value(e.threadName());
        if (e.caller() != null) {
            w.name("caller").value(e.caller().toShort());
        }
        w.name("message").value(masker.maskText(e.message()));

        Map<String, String> ctx = e.context();
        for (String key : List.of(LogContext.TRACE_ID, LogContext.SPAN_ID, LogContext.REQUEST_ID,
                LogContext.CORRELATION_ID)) {
            String v = ctx.get(key);
            if (v != null) {
                w.name(key).value(v);
            }
        }
        boolean extrasOpen = false;
        for (Map.Entry<String, String> entry : ctx.entrySet()) {
            if (LogContext.STANDARD_KEYS.contains(entry.getKey())) {
                continue;
            }
            if (!extrasOpen) {
                w.name("ctx").beginObject();
                extrasOpen = true;
            }
            w.name(entry.getKey()).value(masker.maskValue(entry.getKey(), entry.getValue()));
        }
        if (extrasOpen) {
            w.endObject();
        }

        if (!e.fields().isEmpty()) {
            w.name("fields").beginObject();
            for (Map.Entry<String, Object> f : e.fields().entrySet()) {
                String key = String.valueOf(f.getKey());
                w.name(key).value(masker.maskValue(key, f.getValue()));
            }
            w.endObject();
        }

        if (e.throwable() != null) {
            w.name("error");
            writeError(w, e.throwable(), 0);
        }
        w.endObject();
    }

    private void writeError(JsonWriter w, ThrowableInfo t, int depth) {
        w.beginObject();
        w.name("type").value(t.type());
        if (t.message() != null) {
            w.name("message").value(masker.maskText(t.message()));
        }
        StackFrameInfo o = t.origin();
        if (o != null) {
            w.name("origin").beginObject();
            w.name("class").value(o.className());
            w.name("method").value(o.methodName());
            if (o.fileName() != null) {
                w.name("file").value(o.fileName());
            }
            w.name("line").value(o.lineNumber());
            w.endObject();
        }
        if (!t.fingerprint().isEmpty()) {
            w.name("fingerprint").value(t.fingerprint());
        }
        if (!t.frames().isEmpty()) {
            w.name("frames").beginArray();
            for (StackFrameInfo f : t.frames()) {
                w.value(f.toJavaStyle());
            }
            w.endArray();
        }
        if (t.omittedFrames() > 0) {
            w.name("omitted_frames").value(t.omittedFrames());
        }
        if (t.omittedCauses() > 0) {
            w.name("omitted_causes").value(t.omittedCauses());
        }
        if (t.cause() != null && depth < MAX_ERROR_DEPTH) {
            w.name("cause");
            writeError(w, t.cause(), depth + 1);
        }
        if (!t.suppressed().isEmpty() && depth < MAX_ERROR_DEPTH) {
            w.name("suppressed").beginArray();
            for (ThrowableInfo s : t.suppressed()) {
                writeError(w, s, depth + 1);
            }
            w.endArray();
        }
        w.endObject();
    }

    /** خروجی حداقلی؛ عمداً پیام اصلی را نمی‌نویسد (ممکن است mask نشده باشد). */
    private void writeFallback(StringBuilder sb, LogEvent e, RuntimeException cause) {
        JsonWriter w = new JsonWriter(sb, MAX_STRING_CHARS);
        w.beginObject();
        w.name("timestamp").value(TIMESTAMP.format(e.timestamp()));
        w.name("level").value(e.level().name());
        w.name("stream").value(e.stream().name());
        w.name("index_prefix").value(identity.indexPrefixFor(e.stream()));
        w.name("service").value(identity.service());
        w.name("logger").value(e.loggerName());
        w.name("message").value("[log serialization failed: " + cause.getClass().getName() + "]");
        w.endObject();
    }
}
