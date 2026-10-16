package ir.mazvia.logging.spi;

import ir.mazvia.logging.policy.LoggingPolicy;

/**
 * منبع تنظیمات پویا (مثلاً جدول دیتابیس). پیاده‌سازی در ماژول Integration قرار می‌گیرد؛
 * هسته به دیتابیس وابسته نیست. خطا باید به‌صورت exception اعلام شود تا مصرف‌کننده
 * آخرین تنظیمات معتبر را نگه دارد.
 */
@FunctionalInterface
public interface LoggingPolicySource {
    LoggingPolicy load() throws Exception;
}
