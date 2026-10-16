package ir.mazvia.logging.api;

import java.util.Locale;

/** سطح لاگ. ترتیب severity: TRACE &lt; DEBUG &lt; INFO &lt; WARN &lt; ERROR. */
public enum LogLevel {
    TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /** آیا رویدادی با این سطح، در آستانهٔ {@code threshold} ثبت می‌شود؟ */
    public boolean isEnabledFor(LogLevel threshold) {
        return severity >= threshold.severity;
    }

    /** تبدیل رشته (بدون حساسیت به حروف). {@code WARNING} هم‌ارز WARN است. */
    public static LogLevel parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("log level must not be blank");
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        if ("WARNING".equals(v)) {
            return WARN;
        }
        try {
            return valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown log level: " + value, e);
        }
    }
}
