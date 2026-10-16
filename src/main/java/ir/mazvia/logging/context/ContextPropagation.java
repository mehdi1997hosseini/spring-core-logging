package ir.mazvia.logging.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * انتقال {@link LogContext} به threadهای دیگر. context در لحظهٔ <b>wrap</b> (زمان ارسال کار) گرفته و
 * هنگام اجرا برقرار می‌شود و در پایان، context قبلی thread اجراکننده بازگردانده می‌شود
 * (threadهای pool آلوده نمی‌شوند).
 */
public final class ContextPropagation {

    private ContextPropagation() { }

    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task");
        Map<String, String> captured = LogContext.snapshot();
        return () -> {
            try (LogContext.Scope ignored = LogContext.restore(captured)) {
                task.run();
            }
        };
    }

    public static <V> Callable<V> wrap(Callable<V> task) {
        Objects.requireNonNull(task, "task");
        Map<String, String> captured = LogContext.snapshot();
        return () -> {
            try (LogContext.Scope ignored = LogContext.restore(captured)) {
                return task.call();
            }
        };
    }

    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        Map<String, String> captured = LogContext.snapshot();
        return () -> {
            try (LogContext.Scope ignored = LogContext.restore(captured)) {
                return supplier.get();
            }
        };
    }

    /** Executor که هر کار را همراه context لحظهٔ ارسال اجرا می‌کند. */
    public static Executor wrap(Executor delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return command -> delegate.execute(wrap(command));
    }
}
