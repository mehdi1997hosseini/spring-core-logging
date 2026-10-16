package ir.mazvia.logging.api;

import java.util.function.Supplier;

/**
 * پیاده‌سازی بی‌اثر برای سطوح غیرفعال.
 */
enum NoopLogBuilder implements LogBuilder {
    INSTANCE;

    @Override
    public LogBuilder field(String key, Object value) {
        return this;
    }

    @Override
    public LogBuilder cause(Throwable throwable) {
        return this;
    }

    @Override
    public LogBuilder stream(LogStream stream) {
        return this;
    }

    @Override
    public void log(String message) {
    }

    @Override
    public void log(String template, Object... args) {
    }

    @Override
    public void log(Supplier<String> message) {
    }
}
