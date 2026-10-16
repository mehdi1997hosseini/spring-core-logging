package ir.mazvia.logging.policy;

import java.util.Locale;

/** زمان ثبت بدنهٔ درخواست و پاسخ HTTP. */
public enum BodyCaptureMode {
    /** هیچ‌وقت. */
    NEVER,
    /** فقط وقتی status برابر ۴۰۰ یا بیشتر باشد (پیشنهاد production). */
    ON_ERROR,
    /** همیشه (مناسب تست). */
    ALWAYS;

    /** پذیرش {@code on-error} و {@code ON_ERROR}. */
    public static BodyCaptureMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("body capture mode must not be blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown body capture mode: " + value, e);
        }
    }
}
