package ir.mazvia.logging.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * تنظیمات ثبت درخواست/پاسخ HTTP.
 *
 * <p>قفل ایمنی production: در environmentهای {@code productionEnvironments}، حالت‌های پرخطر
 * ({@code ALWAYS} برای بدنه و {@code TEXT_PARTS} برای multipart) فقط وقتی مجازند که
 * {@code allowBodyCaptureInProduction} صریحاً true باشد.
 *
 * @param bodyContentTypes  content-typeهایی که بدنه‌شان ثبت می‌شود (هر کدام باید strategy داشته باشد؛
 *                          بررسی آن در مرحلهٔ HTTP انجام می‌شود)
 * @param successSampleRate نسبت نمونه‌برداری از درخواست‌های موفق (۰ تا ۱)؛ خطاها همیشه ثبت می‌شوند
 */
public record HttpPolicy(
        boolean exchangeEnabled,
        BodyCaptureMode bodyCapture,
        int bodyMaxBytes,
        Set<String> bodyContentTypes,
        MultipartMode multipartMode,
        Set<String> excludePaths,
        double successSampleRate,
        boolean logRequestStart,
        boolean allowBodyCaptureInProduction,
        Set<String> productionEnvironments) {

    public static final int DEFAULT_BODY_MAX_BYTES = 8 * 1024;
    public static final int BODY_MAX_BYTES_CEILING = 64 * 1024;

    public HttpPolicy {
        bodyCapture = bodyCapture == null ? BodyCaptureMode.NEVER : bodyCapture;
        if (bodyMaxBytes < 0) {
            throw new IllegalArgumentException("bodyMaxBytes must be >= 0");
        }
        bodyMaxBytes = Math.min(bodyMaxBytes, BODY_MAX_BYTES_CEILING);
        bodyContentTypes = normalizeContentTypes(bodyContentTypes);
        multipartMode = multipartMode == null ? MultipartMode.OFF : multipartMode;
        excludePaths = trimmed(excludePaths, false);
        if (Double.isNaN(successSampleRate) || successSampleRate < 0.0 || successSampleRate > 1.0) {
            throw new IllegalArgumentException("successSampleRate must be within [0,1]");
        }
        productionEnvironments = trimmed(productionEnvironments, true);
    }

    /** پیش‌فرض امن: متادیتای exchange فعال، بدنه هرگز، multipart خاموش. */
    public static HttpPolicy defaults() {
        return new HttpPolicy(true, BodyCaptureMode.NEVER, DEFAULT_BODY_MAX_BYTES,
                Set.of("application/json", "application/x-www-form-urlencoded"),
                MultipartMode.OFF, Set.of("/actuator/**", "/health/**"), 1.0, false, false,
                Set.of("prod", "production"));
    }

    public HttpPolicy withBodyCapture(BodyCaptureMode mode) {
        return new HttpPolicy(exchangeEnabled, mode, bodyMaxBytes, bodyContentTypes, multipartMode, excludePaths,
                successSampleRate, logRequestStart, allowBodyCaptureInProduction, productionEnvironments);
    }

    public HttpPolicy withMultipartMode(MultipartMode mode) {
        return new HttpPolicy(exchangeEnabled, bodyCapture, bodyMaxBytes, bodyContentTypes, mode, excludePaths,
                successSampleRate, logRequestStart, allowBodyCaptureInProduction, productionEnvironments);
    }

    public HttpPolicy withAllowBodyCaptureInProduction(boolean allow) {
        return new HttpPolicy(exchangeEnabled, bodyCapture, bodyMaxBytes, bodyContentTypes, multipartMode, excludePaths,
                successSampleRate, logRequestStart, allow, productionEnvironments);
    }

    /** آیا بدنه‌ای با این content-type (غیر multipart) مجاز به ثبت است؟ */
    public boolean allowsBodyFor(String contentType) {
        String ct = normalizeContentType(contentType);
        return !ct.isEmpty() && !isMultipart(ct) && bodyContentTypes.contains(ct);
    }

    /** نقض‌های قفل production برای environment داده‌شده؛ فهرست خالی یعنی مجاز. */
    public List<String> productionViolations(String environment) {
        if (environment == null || allowBodyCaptureInProduction) {
            return List.of();
        }
        if (!productionEnvironments.contains(environment.trim().toLowerCase(Locale.ROOT))) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        if (bodyCapture == BodyCaptureMode.ALWAYS) {
            violations.add("http.body.capture-when=always is not allowed in production environment '" + environment
                    + "' (set allow-in-production=true to override explicitly)");
        }
        if (multipartMode == MultipartMode.TEXT_PARTS) {
            violations.add("http.body.multipart.mode=text-parts is not allowed in production environment '"
                    + environment + "' (set allow-in-production=true to override explicitly)");
        }
        return List.copyOf(violations);
    }

    /** حروف کوچک، حذف پارامترها (charset=...) و فاصله. */
    public static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String ct = contentType;
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi);
        }
        return ct.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isMultipart(String contentType) {
        return normalizeContentType(contentType).startsWith("multipart/");
    }

    private static Set<String> normalizeContentTypes(Set<String> types) {
        if (types == null || types.isEmpty()) {
            return Set.of();
        }
        java.util.HashSet<String> out = new java.util.HashSet<>();
        for (String t : types) {
            String n = normalizeContentType(t);
            if (!n.isEmpty()) {
                out.add(n);
            }
        }
        return Set.copyOf(out);
    }

    private static Set<String> trimmed(Set<String> values, boolean lowerCase) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        java.util.HashSet<String> out = new java.util.HashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(lowerCase ? v.trim().toLowerCase(Locale.ROOT) : v.trim());
            }
        }
        return Set.copyOf(out);
    }
}
