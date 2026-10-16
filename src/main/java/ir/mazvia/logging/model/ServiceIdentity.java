package ir.mazvia.logging.model;

import ir.mazvia.logging.api.LogStream;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * هویت ثابت فرایند. {@code indexPrefix} پیشوند index در Elasticsearch است
 * (Fluent Bit تاریخ روز را به آن اضافه می‌کند: {@code <indexPrefix>-<yyyy.MM.dd>}).
 *
 * <p>قواعد نام index در Elasticsearch: حروف کوچک، بدون فاصله و کاراکترهای خاص،
 * شروع نشدن با {@code - _ + .}، حداکثر ۲۵۵ بایت. اینجا سخت‌گیرانه‌تر و با سقف ۱۰۰ کاراکتر
 * اعتبارسنجی می‌شود تا جا برای پسوند تاریخ بماند.
 */
public record ServiceIdentity(
        String service,
        String module,
        String environment,
        String instanceId,
        String indexPrefix) {

    public static final int MAX_INDEX_PREFIX_LENGTH = 100;
    private static final Pattern VALID_INDEX_PREFIX = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");

    public ServiceIdentity {
        service = requireText(service, "service");
        environment = requireText(environment, "environment");
        module = (module == null || module.isBlank()) ? "default" : module.trim();
        instanceId = (instanceId == null || instanceId.isBlank()) ? "unknown" : instanceId.trim();
        Objects.requireNonNull(indexPrefix, "indexPrefix");
        if (!VALID_INDEX_PREFIX.matcher(indexPrefix).matches()) {
            throw new IllegalArgumentException("invalid Elasticsearch index prefix: '" + indexPrefix
                    + "' (allowed: [a-z0-9][a-z0-9._-]{0,99})");
        }
    }

    /**
     * پیشوند index برای یک stream: stream پیش‌فرض {@code app} همان {@code indexPrefix} است و
     * بقیه {@code <indexPrefix>-<stream>} (مثلاً {@code order-service-startup}).
     */
    public String indexPrefixFor(LogStream stream) {
        return (stream == null || LogStream.APP.equals(stream)) ? indexPrefix : indexPrefix + "-" + stream.name();
    }

    /** نام index از روی نام سرویس نرمال‌سازی می‌شود؛ اگر نتیجه تهی شود، fail-fast. */
    public static ServiceIdentity of(String service, String module, String environment, String instanceId) {
        return new ServiceIdentity(service, module, environment, instanceId,
                normalizeIndexPrefix(requireText(service, "service")));
    }

    /** حروف کوچک؛ هر کاراکتر نامجاز به '-'؛ حذف کاراکترهای ابتدایی غیرمجاز؛ برش به سقف طول. */
    public static String normalizeIndexPrefix(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        int start = 0;
        while (start < s.length() && !isAlnum(s.charAt(start))) {
            start++;
        }
        s = s.substring(start);
        if (s.length() > MAX_INDEX_PREFIX_LENGTH) {
            s = s.substring(0, MAX_INDEX_PREFIX_LENGTH);
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("service name '" + raw + "' cannot be converted to a valid index prefix");
        }
        return s;
    }

    private static boolean isAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static String requireText(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return v.trim();
    }
}
