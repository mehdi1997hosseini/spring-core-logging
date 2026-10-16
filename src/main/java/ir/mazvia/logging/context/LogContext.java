package ir.mazvia.logging.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Context هم‌بستگی لاگ (trace_id، request_id، ...) متصل به thread جاری.
 *
 * <p>مقدار ذخیره‌شده همیشه یک Map immutable است (copy-on-write)، پس {@link #snapshot()} ارزان و
 * امن است. برای انتقال به threadهای دیگر از {@link ContextPropagation} استفاده کنید؛
 * به‌کارگیری {@code InheritableThreadLocal} عمداً کنار گذاشته شده چون در poolها نشت می‌کند.
 *
 * <p>این کلاس هرگز exception نمی‌اندازد: ورودی نامعتبر یا سرریز سقف‌ها بی‌صدا نادیده گرفته می‌شود
 * (ثبت لاگ نباید برنامه را بشکند).
 */
public final class LogContext {

    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String REQUEST_ID = "request_id";
    public static final String CORRELATION_ID = "correlation_id";

    /** کلیدهایی که در سطح بالای JSON می‌آیند؛ بقیه زیر {@code ctx}. */
    public static final Set<String> STANDARD_KEYS = Set.of(TRACE_ID, SPAN_ID, REQUEST_ID, CORRELATION_ID);

    public static final int MAX_ENTRIES = 32;
    public static final int MAX_VALUE_LENGTH = 256;

    private static final ThreadLocal<Map<String, String>> CURRENT = new ThreadLocal<>();

    private LogContext() { }

    /** تصویر immutable از context فعلی؛ هرگز null نیست. */
    public static Map<String, String> snapshot() {
        Map<String, String> m = CURRENT.get();
        return m == null ? Map.of() : m;
    }

    public static String get(String key) {
        return snapshot().get(key);
    }

    /** مقدار null یعنی حذف کلید. */
    public static void put(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            remove(key);
            return;
        }
        Map<String, String> cur = snapshot();
        String v = limit(value);
        if (v.equals(cur.get(key))) {
            return;
        }
        if (!cur.containsKey(key) && cur.size() >= MAX_ENTRIES) {
            return;
        }
        Map<String, String> next = new LinkedHashMap<>(cur);
        next.put(key, v);
        CURRENT.set(Collections.unmodifiableMap(next));
    }

    public static void remove(String key) {
        Map<String, String> cur = snapshot();
        if (key == null || !cur.containsKey(key)) {
            return;
        }
        Map<String, String> next = new LinkedHashMap<>(cur);
        next.remove(key);
        set(next);
    }

    /** پاک‌سازی کامل context این thread (در پایان پردازش یک درخواست در threadهای pool). */
    public static void clear() {
        CURRENT.remove();
    }

    /** افزودن مقادیر به context فعلی تا زمان بسته شدن Scope؛ سپس context قبلی بازمی‌گردد. */
    public static Scope open(Map<String, String> additions) {
        Scope scope = new Scope(CURRENT.get());
        if (additions != null) {
            additions.forEach(LogContext::put);
        }
        return scope;
    }

    public static Scope open(String key, String value) {
        Scope scope = new Scope(CURRENT.get());
        put(key, value);
        return scope;
    }

    /** جایگزینی کامل context با {@code snapshot} تا زمان بسته شدن Scope. */
    public static Scope restore(Map<String, String> snapshot) {
        Scope scope = new Scope(CURRENT.get());
        set(sanitize(snapshot));
        return scope;
    }

    private static void set(Map<String, String> map) {
        if (map.isEmpty()) {
            CURRENT.remove();
        } else {
            CURRENT.set(Collections.unmodifiableMap(map));
        }
    }

    private static Map<String, String> sanitize(Map<String, String> source) {
        Map<String, String> out = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, String> e : source.entrySet()) {
                if (out.size() >= MAX_ENTRIES) {
                    break;
                }
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                    out.put(e.getKey(), limit(e.getValue()));
                }
            }
        }
        return out;
    }

    private static String limit(String v) {
        return v.length() <= MAX_VALUE_LENGTH ? v : v.substring(0, MAX_VALUE_LENGTH);
    }

    /** بازگرداندن context قبلی هنگام close. باید در همان threadی که باز شده بسته شود. */
    public static final class Scope implements AutoCloseable {
        private final Map<String, String> previous;
        private boolean closed;

        private Scope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
