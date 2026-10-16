package ir.mazvia.logging.policy;

import ir.mazvia.logging.api.LogLevel;

import java.util.*;

/**
 * تصویر immutable از تنظیمات زمان اجرا (hot-reload). منبع آن (properties، دیتابیس، ...) از طریق
 * {@code LoggingPolicySource} تأمین می‌شود؛ مسیر ثبت لاگ فقط از نمونهٔ در حافظه
 * ({@link PolicyHolder}) می‌خواند.
 *
 * <p>کف امنیتی غیرقابل‌دورزدن: کلیدهای حساس پایه همیشه mask می‌شوند حتی اگر منبع تنظیمات
 * آن‌ها را ندهد.
 */
public record LoggingPolicy(
        LogLevel rootLevel,
        Map<String, LogLevel> loggerLevels,
        boolean methodTraceEnabled,
        Set<String> methodTracePackages,
        Set<String> sensitiveKeys,
        StackPolicy stack,
        HttpPolicy http) {

    public static final Set<String> BASELINE_SENSITIVE_KEYS = Set.of(
            "password", "passwd", "secret", "token", "access_token", "refresh_token",
            "authorization", "cookie", "set-cookie", "api-key", "apikey", "client_secret",
            "card_number", "cvv", "ssn");

    public LoggingPolicy {
        if (rootLevel == null) {
            throw new IllegalArgumentException("rootLevel must not be null");
        }
        loggerLevels = loggerLevels == null ? Map.of() : Map.copyOf(loggerLevels);
        methodTracePackages = PackageMatch.normalize(methodTracePackages);
        sensitiveKeys = withBaseline(sensitiveKeys);
        stack = stack == null ? StackPolicy.defaults() : stack;
        http = http == null ? HttpPolicy.defaults() : http;
    }

    /** پیش‌فرض امن: INFO، ردیابی متد خاموش، بدنهٔ HTTP هرگز. */
    public static LoggingPolicy defaults() {
        return new LoggingPolicy(LogLevel.INFO, Map.of(), false, Set.of(), Set.of(),
                StackPolicy.defaults(), HttpPolicy.defaults());
    }

    /** نسخهٔ کپی‌شده با سیاست HTTP جدید. */
    public LoggingPolicy withHttp(HttpPolicy newHttp) {
        return new LoggingPolicy(rootLevel, loggerLevels, methodTraceEnabled, methodTracePackages,
                sensitiveKeys, stack, newHttp);
    }

    /** سطح مؤثر logger با نزدیک‌ترین پیشوند (com.a.b → com.a → com → root). */
    public LogLevel effectiveLevel(String loggerName) {
        if (loggerName != null && !loggerLevels.isEmpty()) {
            String name = loggerName;
            while (true) {
                LogLevel level = loggerLevels.get(name);
                if (level != null) {
                    return level;
                }
                int dot = name.lastIndexOf('.');
                if (dot < 0) {
                    break;
                }
                name = name.substring(0, dot);
            }
        }
        return rootLevel;
    }

    public boolean isEnabled(String loggerName, LogLevel level) {
        return level.isEnabledFor(effectiveLevel(loggerName));
    }

    /** آیا کلاس در یکی از پکیج‌های ردیابی متد است؟ (بدون تخصیص حافظه) */
    public boolean tracesMethodsIn(String className) {
        return methodTraceEnabled && className != null && PackageMatch.matches(methodTracePackages, className);
    }

    /** نقض‌های امنیتی این policy برای environment؛ فهرست خالی یعنی قابل‌پذیرش. */
    public List<String> violationsFor(String environment) {
        return http.productionViolations(environment);
    }

    private static Set<String> withBaseline(Set<String> configured) {
        Set<String> out = new HashSet<>(BASELINE_SENSITIVE_KEYS);
        if (configured != null) {
            for (String k : configured) {
                if (k != null && !k.isBlank()) {
                    out.add(k.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return Set.copyOf(out);
    }
}
