package ir.mazvia.logging.model;

import java.util.List;
import java.util.Objects;

/**
 * نمایش ساختاریافته و immutable از یک Throwable.
 *
 * @param origin         اولین frame متعلق به اپلیکیشن (جایی از برنامهٔ ما که خطا از آن آمده)
 * @param fingerprint    هش نوع + کلاس/متد origin برای گروه‌بندی خطاهای تکراری (بدون شماره خط)
 * @param frames         frameهای نگه‌داشته‌شده (frameهای فریمورک فشرده شده‌اند)
 * @param omittedFrames  تعداد frameهای حذف‌شده
 * @param omittedCauses  تعداد causeهای میانی حذف‌شده بین این گره و گرهٔ بعدی (ریشه همیشه می‌ماند)
 */
public record ThrowableInfo(
        String type,
        String message,
        StackFrameInfo origin,
        String fingerprint,
        List<StackFrameInfo> frames,
        int omittedFrames,
        ThrowableInfo cause,
        int omittedCauses,
        List<ThrowableInfo> suppressed) {

    public ThrowableInfo {
        Objects.requireNonNull(type, "type");
        if (omittedFrames < 0 || omittedCauses < 0) {
            throw new IllegalArgumentException("omitted counters must be >= 0");
        }
        fingerprint = fingerprint == null ? "" : fingerprint;
        frames = frames == null ? List.of() : List.copyOf(frames);
        suppressed = suppressed == null ? List.of() : List.copyOf(suppressed);
    }

    /** ریشهٔ زنجیرهٔ cause. */
    public ThrowableInfo root() {
        ThrowableInfo cur = this;
        while (cur.cause != null) {
            cur = cur.cause;
        }
        return cur;
    }
}
