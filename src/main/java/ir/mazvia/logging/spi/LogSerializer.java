package ir.mazvia.logging.spi;

import ir.mazvia.logging.model.LogEvent;

/** تبدیل رویداد به بایت‌های UTF-8. خروجی دقیقاً یک خط JSON و با '\n' پایان می‌یابد. */
public interface LogSerializer {
    byte[] serialize(LogEvent event);
}
