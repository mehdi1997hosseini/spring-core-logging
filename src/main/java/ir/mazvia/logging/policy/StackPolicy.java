package ir.mazvia.logging.policy;

import ir.mazvia.logging.api.LogLevel;

import java.util.Set;

/**
 * تنظیمات تریس خطا و ثبت محل فراخوانی.
 *
 * @param maxFrames       سقف frameهای نگه‌داشته‌شده در هر Throwable
 * @param maxCauseDepth   سقف عمق زنجیرهٔ cause (ریشه همیشه می‌ماند)
 * @param appPackages     پکیج‌های اپلیکیشن؛ frameهای این پکیج‌ها همیشه نگه داشته می‌شوند
 * @param callerMinLevel  از این سطح به بالا محل فراخوانی لاگ (کلاس/متد/خط) ثبت می‌شود
 */
public record StackPolicy(int maxFrames, int maxCauseDepth, Set<String> appPackages, LogLevel callerMinLevel) {

    public static final int DEFAULT_MAX_FRAMES = 15;
    public static final int DEFAULT_MAX_CAUSE_DEPTH = 5;
    public static final int MAX_FRAMES_CEILING = 200;
    public static final int MAX_CAUSE_DEPTH_CEILING = 20;

    public StackPolicy {
        if (maxFrames < 1) {
            throw new IllegalArgumentException("maxFrames must be >= 1");
        }
        if (maxCauseDepth < 1) {
            throw new IllegalArgumentException("maxCauseDepth must be >= 1");
        }
        maxFrames = Math.min(maxFrames, MAX_FRAMES_CEILING);
        maxCauseDepth = Math.min(maxCauseDepth, MAX_CAUSE_DEPTH_CEILING);
        appPackages = PackageMatch.normalize(appPackages);
        callerMinLevel = callerMinLevel == null ? LogLevel.WARN : callerMinLevel;
    }

    public static StackPolicy defaults() {
        return new StackPolicy(DEFAULT_MAX_FRAMES, DEFAULT_MAX_CAUSE_DEPTH, Set.of(), LogLevel.WARN);
    }

    /**
     * آیا کلاس متعلق به اپلیکیشن است؟ کلاس‌های تولیدشدهٔ پروکسی ({@code $$}) حتی در پکیج
     * اپلیکیشن، فریمورک محسوب می‌شوند.
     */
    public boolean isAppClass(String className) {
        return className != null
                && className.indexOf("$$") < 0
                && PackageMatch.matches(appPackages, className);
    }

    public boolean capturesCallerFor(LogLevel level) {
        return level.isEnabledFor(callerMinLevel);
    }
}
