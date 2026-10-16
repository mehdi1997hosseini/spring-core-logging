package ir.mazvia.logging.api;

/** API عمومی و یکپارچهٔ ثبت لاگ برای برنامه‌های مصرف‌کننده. */
public interface PlatformLogger {

    String name();

    boolean isEnabled(LogLevel level);

    /** در صورت غیرفعال بودن سطح، {@link LogBuilder#noop()} برمی‌گرداند. */
    LogBuilder at(LogLevel level);

    default LogBuilder trace() { return at(LogLevel.TRACE); }
    default LogBuilder debug() { return at(LogLevel.DEBUG); }
    default LogBuilder info()  { return at(LogLevel.INFO); }
    default LogBuilder warn()  { return at(LogLevel.WARN); }
    default LogBuilder error() { return at(LogLevel.ERROR); }

    default void info(String message)  { info().log(message); }
    default void warn(String message)  { warn().log(message); }
    default void error(String message, Throwable cause) { error().cause(cause).log(message); }
}
