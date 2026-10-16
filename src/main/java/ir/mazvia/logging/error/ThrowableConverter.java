package ir.mazvia.logging.error;

import ir.mazvia.logging.model.StackFrameInfo;
import ir.mazvia.logging.model.ThrowableInfo;
import ir.mazvia.logging.policy.StackPolicy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * تبدیل Throwable به {@link ThrowableInfo}: مشخص می‌کند خطا از کجای <b>برنامهٔ ما</b> آمده.
 *
 * <ul>
 *   <li>{@code origin}: اولین frame متعلق به پکیج‌های اپلیکیشن (کلاس، متد، فایل، خط).</li>
 *   <li>همهٔ frameهای اپلیکیشن + frame اول (نقطهٔ پرتاب واقعی) نگه داشته می‌شوند؛ frameهای
 *       فریمورک/JDK فقط شمرده می‌شوند ({@code omittedFrames}).</li>
 *   <li>زنجیرهٔ cause تا عمق مجاز؛ ریشه همیشه می‌ماند و تعداد گره‌های حذف‌شده ثبت می‌شود.</li>
 *   <li>اگر {@code appPackages} خالی باشد، فقط {@code maxFrames} frame بالایی بدون فشرده‌سازی می‌ماند.</li>
 * </ul>
 *
 * <p>stateless و thread-safe؛ policy در هر فراخوانی داده می‌شود تا hot-reload اعمال شود.
 * هرگز exception نمی‌اندازد (متن پیام یا getter خراب Throwable ثبت لاگ را نمی‌شکند).
 */
public final class ThrowableConverter {

    public static final int DEFAULT_MAX_MESSAGE_CHARS = 2_000;
    static final int MAX_CHAIN_SCAN = 100;
    static final int MAX_SUPPRESSED = 5;

    private final int maxMessageChars;

    public ThrowableConverter() {
        this(DEFAULT_MAX_MESSAGE_CHARS);
    }

    public ThrowableConverter(int maxMessageChars) {
        if (maxMessageChars < 1) {
            throw new IllegalArgumentException("maxMessageChars must be >= 1");
        }
        this.maxMessageChars = maxMessageChars;
    }

    public ThrowableInfo convert(Throwable throwable, StackPolicy policy) {
        Objects.requireNonNull(throwable, "throwable");
        Objects.requireNonNull(policy, "policy");

        List<Throwable> chain = causeChain(throwable);
        int max = policy.maxCauseDepth();
        List<Throwable> kept;
        int omitted = 0;
        if (chain.size() <= max) {
            kept = chain;
        } else {
            kept = new ArrayList<>(chain.subList(0, max - 1));
            kept.add(chain.get(chain.size() - 1));
            omitted = chain.size() - max;
        }

        int omittedAt = Math.max(kept.size() - 2, 0);
        ThrowableInfo next = null;
        for (int i = kept.size() - 1; i >= 0; i--) {
            int omittedHere = (omitted > 0 && i == omittedAt) ? omitted : 0;
            next = node(kept.get(i), policy, next, omittedHere, i == 0);
        }
        return next;
    }

    private ThrowableInfo node(Throwable t, StackPolicy policy, ThrowableInfo cause, int omittedCauses,
                               boolean withSuppressed) {
        FrameSelection sel = selectFrames(safeStack(t), policy);
        String type = t.getClass().getName();

        List<ThrowableInfo> suppressed = List.of();
        if (withSuppressed) {
            Throwable[] sup = safeSuppressed(t);
            if (sup.length > 0) {
                List<ThrowableInfo> list = new ArrayList<>();
                for (int i = 0; i < sup.length && i < MAX_SUPPRESSED; i++) {
                    if (sup[i] != null && sup[i] != t) {
                        list.add(node(sup[i], policy, null, 0, false));
                    }
                }
                suppressed = list;
            }
        }
        return new ThrowableInfo(type, safeMessage(t), sel.origin(), fingerprint(type, sel.origin()),
                sel.frames(), sel.omitted(), cause, omittedCauses, suppressed);
    }

    private static FrameSelection selectFrames(StackTraceElement[] stack, StackPolicy policy) {
        if (stack.length == 0) {
            return new FrameSelection(List.of(), 0, null);
        }
        boolean hasApp = !policy.appPackages().isEmpty();
        List<StackFrameInfo> kept = new ArrayList<>();
        StackFrameInfo origin = null;
        int omitted = 0;

        for (int i = 0; i < stack.length; i++) {
            StackTraceElement e = stack[i];
            boolean app = hasApp && policy.isAppClass(e.getClassName());
            if (app && origin == null) {
                origin = StackFrameInfo.from(e);
            }
            boolean keep = !hasApp || i == 0 || app;
            if (!keep || kept.size() >= policy.maxFrames()) {
                omitted++;
                continue;
            }
            kept.add(StackFrameInfo.from(e));
        }
        if (origin == null) {
            origin = StackFrameInfo.from(stack[0]);
        }
        return new FrameSelection(kept, omitted, origin);
    }

    private static List<Throwable> causeChain(Throwable top) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cur = top;
        while (cur != null && chain.size() < MAX_CHAIN_SCAN && seen.add(cur)) {
            chain.add(cur);
            cur = safeCause(cur);
        }
        return chain;
    }

    private String safeMessage(Throwable t) {
        String m;
        try {
            m = t.getMessage();
        } catch (RuntimeException e) {
            return "<message unavailable>";
        }
        if (m == null) {
            return null;
        }
        if (m.length() > maxMessageChars) {
            return m.substring(0, maxMessageChars) + "...[truncated " + (m.length() - maxMessageChars) + " chars]";
        }
        return m;
    }

    private static StackTraceElement[] safeStack(Throwable t) {
        try {
            StackTraceElement[] s = t.getStackTrace();
            return s == null ? new StackTraceElement[0] : s;
        } catch (RuntimeException e) {
            return new StackTraceElement[0];
        }
    }

    private static Throwable safeCause(Throwable t) {
        try {
            return t.getCause();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Throwable[] safeSuppressed(Throwable t) {
        try {
            Throwable[] s = t.getSuppressed();
            return s == null ? new Throwable[0] : s;
        } catch (RuntimeException e) {
            return new Throwable[0];
        }
    }

    /** هش نوع + کلاس/متد origin (بدون شماره خط تا بین نسخه‌های برنامه پایدار بماند). */
    static String fingerprint(String type, StackFrameInfo origin) {
        String basis = type + "|" + (origin == null ? "" : origin.className() + "." + origin.methodName());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[16];
            final char[] digits = "0123456789abcdef".toCharArray();
            for (int i = 0; i < 8; i++) {
                hex[i * 2] = digits[(digest[i] >> 4) & 0xF];
                hex[i * 2 + 1] = digits[digest[i] & 0xF];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(basis.hashCode());
        }
    }

    private record FrameSelection(List<StackFrameInfo> frames, int omitted, StackFrameInfo origin) { }
}
