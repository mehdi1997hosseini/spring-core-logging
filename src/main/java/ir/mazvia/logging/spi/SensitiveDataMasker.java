package ir.mazvia.logging.spi;

/** پوشاندن داده‌های حساس پیش از سریال‌سازی. */
public interface SensitiveDataMasker {

    /** مقدار فیلد با کلید مشخص؛ برای کلید حساس مقدار جایگزین برمی‌گردد. */
    Object maskValue(String key, Object value);

    /** پوشاندن الگوهای حساس داخل متن آزاد (مثلاً پیام یا بدنهٔ درخواست). */
    String maskText(String text);
}
