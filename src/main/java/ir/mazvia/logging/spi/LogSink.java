package ir.mazvia.logging.spi;

import ir.mazvia.logging.api.LogLevel;

/** مقصد نوشتن (فایل چرخشی، stdout، ...). نباید thread فراخواننده را مسدود کند. */
public interface LogSink extends AutoCloseable {

    /** @return false اگر رویداد به‌دلیل پر بودن صف/خطای مقصد دور ریخته شد. */
    boolean offer(LogLevel level, byte[] line);

    default void flush() { }

    @Override
    void close();
}
