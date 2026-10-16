package ir.mazvia.logging.api;

import java.util.function.Supplier;

/**
 * سازندهٔ یک رویداد لاگ. اگر سطح غیرفعال باشد، {@link #noop()} برگردانده می‌شود
 * و هیچ فرمت‌کردن رشته یا تخصیص حافظه‌ای انجام نمی‌شود.
 */
public interface LogBuilder {

    /** افزودن فیلد ساختاریافته. کلیدهای حساس قبل از خروج mask می‌شوند. */
    LogBuilder field(String key, Object value);

    /** پیوست کردن Throwable به رویداد. */
    LogBuilder cause(Throwable throwable);

    /** انتخاب stream مقصد (پیش‌فرض {@link LogStream#APP}). */
    LogBuilder stream(LogStream stream);

    void log(String message);

    /** قالب با placeholder از نوع {@code {}}؛ فقط در صورت فعال بودن سطح فرمت می‌شود. */
    void log(String template, Object... args);

    void log(Supplier<String> message);

    static LogBuilder noop() {
        return NoopLogBuilder.INSTANCE;
    }
}
