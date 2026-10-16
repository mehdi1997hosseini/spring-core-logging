package ir.mazvia.logging.policy;

import java.util.Locale;

/**
 * نحوهٔ ثبت درخواست‌های multipart. محتوای باینری فایل‌ها در هیچ حالتی ثبت نمی‌شود.
 */
public enum MultipartMode {
    /** فقط ثبت می‌شود که درخواست multipart بوده. */
    OFF,
    /** نام part، نام فایل، content-type و اندازه. */
    METADATA,
    /** METADATA به‌علاوهٔ مقدار partهای متنی (با سقف حجم و masking). */
    TEXT_PARTS;

    public static MultipartMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("multipart mode must not be blank");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown multipart mode: " + value, e);
        }
    }
}
