package ir.mazvia.logging.model;

import ir.mazvia.logging.api.LogLevel;
import ir.mazvia.logging.api.LogStream;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * رویداد لاگ  ( immutable class model )
 *
 * @param stream   کانال منطقی (تعیین‌کنندهٔ فایل و پسوند index)؛ null یعنی {@link LogStream#APP}
 * @param caller   محل فراخوانی لاگ (کلاس، متد، خط)؛ فقط برای سطوحی که سیاست تعیین می‌کند
 * @param context  شناسه‌های هم‌بستگی (trace_id, span_id, request_id, ...) از LogContext
 * @param fields   فیلدهای ساختاریافتهٔ همین رویداد (ترتیب درج حفظ می‌شود)
 */
public record LogEvent(
        Instant timestamp,
        LogLevel level,
        LogStream stream,
        String loggerName,
        String message,
        String threadName,
        StackFrameInfo caller,
        Map<String, String> context,
        Map<String, Object> fields,
        ThrowableInfo throwable) {

    public LogEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(loggerName, "loggerName");
        stream = stream == null ? LogStream.APP : stream;
        message = message == null ? "" : message;
        threadName = threadName == null ? "" : threadName;
        context = immutableCopy(context);
        fields = immutableCopy(fields);
    }

    private static <V> Map<String, V> immutableCopy(Map<String, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
