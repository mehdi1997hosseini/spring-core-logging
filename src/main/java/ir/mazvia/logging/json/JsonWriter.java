package ir.mazvia.logging.json;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * نویسندهٔ JSON بدون وابستگی. ویژگی‌ها: escape کامل (کاراکترهای کنترلی، U+2028/2029،
 * surrogateهای ناقص)، NaN/Infinity به‌صورت null، سقف طول رشته، سقف عمق و تعداد عناصر، و
 * اعتبارسنجی ساختار (مثلاً مقدار بدون name داخل object خطا می‌دهد).
 *
 * <p>thread-safe نیست؛ هر نویسنده برای یک سند است.
 */
public final class JsonWriter {

    private static final int MAX_NESTING = 32;
    private static final int MAX_VALUE_DEPTH = 12;
    private static final int MAX_ELEMENTS = 1_000;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final StringBuilder out;
    private final int maxStringChars;
    private final boolean[] needComma = new boolean[MAX_NESTING + 1];
    private final boolean[] isObject = new boolean[MAX_NESTING + 1];
    private int depth;
    private boolean afterName;

    public JsonWriter(StringBuilder out) {
        this(out, Integer.MAX_VALUE);
    }

    public JsonWriter(StringBuilder out, int maxStringChars) {
        if (maxStringChars < 0) {
            throw new IllegalArgumentException("maxStringChars must be >= 0");
        }
        this.out = out;
        this.maxStringChars = maxStringChars;
    }

    public JsonWriter beginObject() {
        beforeValue();
        out.append('{');
        push(true);
        return this;
    }

    public JsonWriter endObject() {
        pop(true);
        out.append('}');
        return this;
    }

    public JsonWriter beginArray() {
        beforeValue();
        out.append('[');
        push(false);
        return this;
    }

    public JsonWriter endArray() {
        pop(false);
        out.append(']');
        return this;
    }

    public JsonWriter name(String name) {
        if (depth == 0 || !isObject[depth] || afterName) {
            throw new IllegalStateException("name() is only valid inside an object, before a value");
        }
        if (needComma[depth]) {
            out.append(',');
        }
        writeString(name == null ? "null" : name);
        out.append(':');
        afterName = true;
        return this;
    }

    public JsonWriter nullValue() {
        beforeValue();
        out.append("null");
        return this;
    }

    public JsonWriter value(String v) {
        if (v == null) {
            return nullValue();
        }
        beforeValue();
        writeString(v);
        return this;
    }

    public JsonWriter value(long v) {
        beforeValue();
        out.append(v);
        return this;
    }

    public JsonWriter value(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return nullValue(); // JSON نمی‌تواند NaN/Infinity را نمایش دهد
        }
        beforeValue();
        out.append(v);
        return this;
    }

    public JsonWriter value(boolean v) {
        beforeValue();
        out.append(v ? "true" : "false");
        return this;
    }

    /** مقدار عمومی: null، رشته، عدد، boolean، enum، Map، Iterable و آرایه؛ بقیه با toString (محافظت‌شده). */
    public JsonWriter value(Object v) {
        writeObject(v, 0);
        return this;
    }

    private void writeObject(Object v, int d) {
        if (v == null) {
            nullValue();
        } else if (v instanceof CharSequence cs) {
            value(cs.toString());
        } else if (v instanceof Boolean b) {
            value(b.booleanValue());
        } else if (v instanceof Number n) {
            writeNumber(n);
        } else if (v instanceof Character c) {
            value(String.valueOf(c));
        } else if (v instanceof Enum<?> e) {
            value(e.name());
        } else if (d >= MAX_VALUE_DEPTH) {
            value("[max-depth]");
        } else if (v instanceof Map<?, ?> m) {
            beginObject();
            int n = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (n++ >= MAX_ELEMENTS) {
                    name("...").value("truncated");
                    break;
                }
                name(String.valueOf(e.getKey()));
                writeObject(e.getValue(), d + 1);
            }
            endObject();
        } else if (v instanceof Iterable<?> it) {
            beginArray();
            int n = 0;
            for (Object o : it) {
                if (n++ >= MAX_ELEMENTS) {
                    value("...truncated");
                    break;
                }
                writeObject(o, d + 1);
            }
            endArray();
        } else if (v.getClass().isArray()) {
            beginArray();
            int len = Array.getLength(v);
            for (int i = 0; i < len; i++) {
                if (i >= MAX_ELEMENTS) {
                    value("...truncated");
                    break;
                }
                writeObject(Array.get(v, i), d + 1);
            }
            endArray();
        } else {
            value(safeToString(v));
        }
    }

    private void writeNumber(Number n) {
        if (n instanceof Double || n instanceof Float) {
            value(n.doubleValue());
        } else if (n instanceof BigDecimal || n instanceof BigInteger) {
            beforeValue();
            out.append(n.toString());
        } else if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte
                || n instanceof java.util.concurrent.atomic.AtomicInteger
                || n instanceof java.util.concurrent.atomic.AtomicLong) {
            value(n.longValue());
        } else {
            value(n.doubleValue());
        }
    }

    private void writeString(String s) {
        out.append('"');
        int limit = s.length();
        String suffix = null;
        if (limit > maxStringChars) {
            suffix = "...[truncated " + (limit - maxStringChars) + " chars]";
            limit = maxStringChars;
            if (limit > 0 && Character.isHighSurrogate(s.charAt(limit - 1))) {
                limit--;
            }
        }
        escape(s, limit);
        if (suffix != null) {
            escape(suffix, suffix.length());
        }
        out.append('"');
    }

    private void escape(String s, int limit) {
        for (int i = 0; i < limit; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                        appendUnicode(c);
                    } else if (Character.isHighSurrogate(c)) {
                        if (i + 1 < limit && Character.isLowSurrogate(s.charAt(i + 1))) {
                            out.append(c).append(s.charAt(i + 1));
                            i++;
                        } else {
                            appendUnicode('\uFFFD');
                        }
                    } else if (Character.isLowSurrogate(c)) {
                        appendUnicode('\uFFFD');
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }

    private void appendUnicode(char c) {
        out.append("\\u")
                .append(HEX[(c >> 12) & 0xF]).append(HEX[(c >> 8) & 0xF])
                .append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
    }

    private void beforeValue() {
        if (depth > 0 && isObject[depth]) {
            if (!afterName) {
                throw new IllegalStateException("value inside an object requires name() first");
            }
            afterName = false;
        } else if (depth > 0 && needComma[depth]) {
            out.append(',');
        }
        needComma[depth] = true;
    }

    private void push(boolean object) {
        if (depth >= MAX_NESTING) {
            throw new IllegalStateException("json nesting too deep");
        }
        depth++;
        isObject[depth] = object;
        needComma[depth] = false;
    }

    private void pop(boolean object) {
        if (depth == 0 || isObject[depth] != object || afterName) {
            throw new IllegalStateException("unbalanced json structure");
        }
        depth--;
    }

    private static String safeToString(Object o) {
        try {
            return String.valueOf(o);
        } catch (RuntimeException e) {
            return "<toString failed: " + o.getClass().getName() + ">";
        }
    }
}
