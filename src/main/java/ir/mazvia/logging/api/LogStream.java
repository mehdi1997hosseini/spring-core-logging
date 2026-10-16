package ir.mazvia.logging.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * کانال منطقی لاگ. هر stream فایل‌ها و index جدا (پسوند نام index) دارد تا لاگ‌های
 * استارت، HTTP و بیزنسی با هم قاطی نشوند. مقدار پیش‌فرض {@link #APP} است و پسوندی ندارد.
 */
public record LogStream(String name) {

    // باید قبل از ثابت‌ها تعریف شود (ترتیب مقداردهی static)
    private static final Pattern VALID = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");

    public static final LogStream APP = new LogStream("app");
    public static final LogStream STARTUP = new LogStream("startup");
    public static final LogStream HTTP = new LogStream("http");

    public LogStream {
        Objects.requireNonNull(name, "name");
        if (!VALID.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid stream name: '" + name + "' (allowed: [a-z][a-z0-9-]{0,31})");
        }
    }

    public static LogStream of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("stream name must not be blank");
        }
        return new LogStream(name.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return name;
    }

}
