package ir.mazvia.logging.policy;

import java.util.List;
import java.util.Objects;

/**
 * کش درون‌حافظه‌ای تنظیمات. همهٔ اجزای لاگ فقط {@link #current()} را می‌خوانند (بدون قفل)؛
 * تغییر از هر منبعی (دیتابیس، properties) با {@link #update} و به‌صورت اتمیک اعمال می‌شود،
 * پس از رویداد بعدی در کل برنامه مؤثر است.
 *
 * <p>policy نامعتبر یا ناامن برای environment رد می‌شود و آخرین policy معتبر باقی می‌ماند.
 */
public final class PolicyHolder {

    private final String environment;
    private volatile LoggingPolicy current;
    private volatile long version;

    /** @throws IllegalArgumentException اگر policy اولیه برای environment ناامن باشد (fail-fast هنگام استارت) */
    public PolicyHolder(String environment, LoggingPolicy initial) {
        this.environment = Objects.requireNonNull(environment, "environment");
        requireValid(initial);
        this.current = initial;
    }

    public LoggingPolicy current() {
        return current;
    }

    /** شمارندهٔ به‌روزرسانی‌های پذیرفته‌شده (برای تشخیص تغییر و مانیتورینگ). */
    public long version() {
        return version;
    }

    /**
     * @return true اگر policy واقعاً تغییر کرد؛ false اگر مساوی نمونهٔ فعلی بود
     * @throws IllegalArgumentException اگر policy برای environment ناامن بود (policy فعلی دست‌نخورده می‌ماند)
     */
    public synchronized boolean update(LoggingPolicy candidate) {
        requireValid(candidate);
        if (candidate.equals(current)) {
            return false;
        }
        current = candidate;
        version++;
        return true;
    }

    private void requireValid(LoggingPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        List<String> violations = policy.violationsFor(environment);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("logging policy rejected: " + String.join("; ", violations));
        }
    }
}
