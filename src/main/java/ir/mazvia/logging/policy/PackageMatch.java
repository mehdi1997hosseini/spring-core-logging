package ir.mazvia.logging.policy;

import java.util.HashSet;
import java.util.Set;

/** تطبیق نام کلاس با پیشوند پکیج (با رعایت مرز نقطه). */
final class PackageMatch {

    private PackageMatch() { }

    /** حذف خالی‌ها، trim، و حذف '.' و '*' انتهایی. */
    static Set<String> normalize(Set<String> packages) {
        if (packages == null || packages.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String p : packages) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String t = p.trim();
            while (t.endsWith(".") || t.endsWith("*")) {
                t = t.substring(0, t.length() - 1);
            }
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return Set.copyOf(out);
    }

    /** بدون تخصیص حافظه. */
    static boolean matches(Set<String> packages, String className) {
        for (String p : packages) {
            if (className.startsWith(p)
                    && (className.length() == p.length() || className.charAt(p.length()) == '.')) {
                return true;
            }
        }
        return false;
    }
}
