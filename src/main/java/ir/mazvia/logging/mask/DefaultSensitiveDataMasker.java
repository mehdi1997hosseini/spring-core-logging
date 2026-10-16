package ir.mazvia.logging.mask;

import ir.mazvia.logging.spi.SensitiveDataMasker;

import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * پوشاندن داده‌های حساس.
 *
 * <p><b>فیلدهای ساختاریافته</b> ({@link #maskValue}): کلید به کلمات شکسته می‌شود
 * ({@code accessToken}, {@code access_token}, {@code X-Api-Key} هر سه با کلید حساس
 * {@code access_token} / {@code api-key} تطبیق می‌خورند، ولی {@code className} با {@code ssn} نه).
 * مقدار جایگزین {@code ***} است. Map/Iterable/آرایه به‌صورت بازگشتی پردازش می‌شوند و اشیای ناشناخته
 * به متن تبدیل و متن‌شان mask می‌شود (نشت از طریق toString ممکن نیست).
 *
 * <p><b>متن آزاد</b> ({@link #maskText}): الگوها، به‌ترتیب: JWT، {@code Bearer ...}، هدر Cookie،
 * جفت‌های {@code key=value} / {@code "key":"value"} با کلیدهای حساس (حتی با پیشوند
 * مثل {@code user_password=} یا {@code x-api-key:})، و شمارهٔ کارت معتبر (Luhn).
 * محدودیت: در متن آزاد، کلید با پیشوند camelCase ({@code userPassword=}) پوشانده نمی‌شود؛
 * برای داده‌های حساس از فیلد ساختاریافته استفاده شود.
 * متن بلندتر از {@value #MAX_TEXT_CHARS} کاراکتر قبل از mask بریده می‌شود (ایمن‌تر از اسکن ناقص).
 *
 * <p>کلیدهای حساس از یک Supplier خوانده می‌شوند تا تغییر policy بدون ساخت دوبارهٔ masker اعمال شود.
 */
public final class DefaultSensitiveDataMasker implements SensitiveDataMasker {

    public static final String MASK = "***";
    static final int MAX_DEPTH = 8;
    static final int MAX_TEXT_CHARS = 65_536;
    private static final int WORD_CACHE_LIMIT = 2_048;
    private static final ConcurrentHashMap<String, List<String>> WORD_CACHE = new ConcurrentHashMap<>();

    private static final Pattern JWT =
            Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bBearer\\s+(?=[A-Za-z0-9._~+/=-]*[0-9._~+/=-])[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern COOKIE_HEADER =
            Pattern.compile("(?i)\\b(set-cookie|cookie)\\s*:\\s*[^\\r\\n]*");
    private static final Pattern CARD =
            Pattern.compile("(?<![0-9])(?:[0-9][ -]?){12,18}[0-9](?![0-9])");

    private final Supplier<? extends Set<String>> keySupplier;
    private volatile Compiled compiled;

    public DefaultSensitiveDataMasker(Supplier<? extends Set<String>> sensitiveKeys) {
        this.keySupplier = Objects.requireNonNull(sensitiveKeys, "sensitiveKeys");
    }

    public static DefaultSensitiveDataMasker of(Set<String> sensitiveKeys) {
        Set<String> copy = Set.copyOf(sensitiveKeys);
        return new DefaultSensitiveDataMasker(() -> copy);
    }

    // ------------------------------------------------------------------ structured values

    @Override
    public Object maskValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        Compiled c = compiled();
        if (key != null && isSensitiveKey(key, c)) {
            return MASK;
        }
        return maskNested(value, 0, c);
    }

    private Object maskNested(Object value, int depth, Compiled c) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence cs) {
            return maskTextInternal(cs.toString(), c);
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof TemporalAccessor
                || value instanceof UUID) {
            return value;
        }
        if (value instanceof Character ch) {
            return maskTextInternal(String.valueOf(ch), c);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (depth >= MAX_DEPTH) {
            return "[max-depth]";
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                out.put(k, isSensitiveKey(k, c) ? (e.getValue() == null ? null : MASK)
                        : maskNested(e.getValue(), depth + 1, c));
            }
            return out;
        }
        if (value instanceof Iterable<?> it) {
            List<Object> out = new ArrayList<>();
            for (Object o : it) {
                out.add(maskNested(o, depth + 1, c));
            }
            return out;
        }
        if (value.getClass().isArray()) {
            List<Object> out = new ArrayList<>();
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                out.add(maskNested(java.lang.reflect.Array.get(value, i), depth + 1, c));
            }
            return out;
        }
        return maskTextInternal(safeToString(value), c);
    }

    // ------------------------------------------------------------------ free text

    @Override
    public String maskText(String text) {
        return maskTextInternal(text, compiled());
    }

    private String maskTextInternal(String text, Compiled c) {
        if (text == null || text.length() < 4) {
            return text;
        }
        String t = text;
        boolean truncated = false;
        if (t.length() > MAX_TEXT_CHARS) {
            t = t.substring(0, MAX_TEXT_CHARS);
            truncated = true;
        }
        t = replaceAll(JWT, t, m -> MASK);
        t = replaceAll(BEARER, t, m -> "Bearer " + MASK);
        t = replaceAll(COOKIE_HEADER, t, m -> m.group(1) + ": " + MASK);
        if (c.keyValue != null) {
            t = replaceAll(c.keyValue, t, m -> m.group(1) + m.group(2) + maskedValue(m.group(3)));
        }
        t = replaceAll(CARD, t, m -> maskCard(m.group()));
        return truncated ? t + "...[truncated " + (text.length() - MAX_TEXT_CHARS) + " chars]" : t;
    }

    private static String replaceAll(Pattern p, String input, Function<Matcher, String> fn) {
        Matcher m = p.matcher(input);
        if (!m.find()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        do {
            m.appendReplacement(sb, Matcher.quoteReplacement(fn.apply(m)));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskedValue(String original) {
        if (original.startsWith("\"")) {
            return "\"" + MASK + "\"";
        }
        if (original.startsWith("'")) {
            return "'" + MASK + "'";
        }
        return MASK;
    }

    private static String maskCard(String candidate) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < candidate.length(); i++) {
            char ch = candidate.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digits.append(ch);
            }
        }
        if (digits.length() < 13 || digits.length() > 19 || !luhn(digits)) {
            return candidate;
        }
        return MASK + digits.substring(digits.length() - 4);
    }

    private static boolean luhn(CharSequence digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    // ------------------------------------------------------------------ key matching

    private boolean isSensitiveKey(String key, Compiled c) {
        List<String> words = WORD_CACHE.get(key);
        if (words == null) {
            words = splitWords(key);
            if (WORD_CACHE.size() < WORD_CACHE_LIMIT) {
                WORD_CACHE.putIfAbsent(key, words);
            }
        }
        if (words.isEmpty()) {
            return false;
        }
        String joined = String.join("", words);
        for (int i = 0; i < c.keyWords.size(); i++) {
            if (containsSequence(words, c.keyWords.get(i)) || joined.equals(c.keyJoined.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSequence(List<String> haystack, List<String> needle) {
        int n = needle.size();
        for (int start = 0; start + n <= haystack.size(); start++) {
            boolean all = true;
            for (int j = 0; j < n; j++) {
                if (!haystack.get(start + j).equals(needle.get(j))) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** شکستن کلید به کلمات: جداکننده‌ها، camelCase و مرز مخفف‌ها (APIKey → api, key). */
    static List<String> splitWords(String key) {
        List<String> words = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int len = key.length();
        for (int i = 0; i < len; i++) {
            char c = key.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                flush(words, cur);
                continue;
            }
            if (cur.length() > 0 && Character.isUpperCase(c)) {
                char prev = key.charAt(i - 1);
                boolean afterLowerOrDigit = Character.isLowerCase(prev) || Character.isDigit(prev);
                boolean acronymEnd = Character.isUpperCase(prev) && i + 1 < len
                        && Character.isLowerCase(key.charAt(i + 1));
                if (afterLowerOrDigit || acronymEnd) {
                    flush(words, cur);
                }
            }
            cur.append(Character.toLowerCase(c));
        }
        flush(words, cur);
        return List.copyOf(words);
    }

    private static void flush(List<String> words, StringBuilder cur) {
        if (cur.length() > 0) {
            words.add(cur.toString());
            cur.setLength(0);
        }
    }

    private static String safeToString(Object o) {
        try {
            return String.valueOf(o);
        } catch (RuntimeException e) {
            return "<toString failed: " + o.getClass().getName() + ">";
        }
    }

    private Compiled compiled() {
        Set<String> keys = keySupplier.get();
        Compiled c = this.compiled;
        if (c == null || c.source != keys) {
            c = new Compiled(keys);
            this.compiled = c;
        }
        return c;
    }

    /** نسخهٔ پیش‌محاسبه‌شده از مجموعهٔ کلیدهای حساس (با شناسایی بر اساس هویت شیء مجموعه). */
    private static final class Compiled {
        final Set<String> source;
        final List<List<String>> keyWords = new ArrayList<>();
        final List<String> keyJoined = new ArrayList<>();
        final Pattern keyValue;

        Compiled(Set<String> source) {
            this.source = source;
            StringBuilder alternatives = new StringBuilder();
            for (String k : source) {
                List<String> words = splitWords(k);
                if (words.isEmpty()) {
                    continue;
                }
                keyWords.add(words);
                keyJoined.add(String.join("", words));
                if (alternatives.length() > 0) {
                    alternatives.append('|');
                }
                for (int i = 0; i < words.size(); i++) {
                    if (i > 0) {
                        alternatives.append("[-_.]?");
                    }
                    alternatives.append(Pattern.quote(words.get(i)));
                }
            }
            this.keyValue = alternatives.length() == 0 ? null : Pattern.compile(
                    "(?i)(?<![A-Za-z0-9])((?:[A-Za-z0-9]+[-_.]){0,3}(?:" + alternatives + "))"
                            + "([\"']?\\s*[=:]\\s*)"
                            + "(\"[^\"]*\"|'[^']*'|(?:(?:Bearer|Basic|Digest)\\s+)?[^\\s,;&\"'}\\]]+)");
        }
    }
}
