package ir.mazvia.logging.model;

import java.util.Objects;

/** یک frame از استک (immutable و مستقل از StackTraceElement). */
public record StackFrameInfo(String className, String methodName, String fileName, int lineNumber) {

    public StackFrameInfo {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
    }

    public static StackFrameInfo from(StackTraceElement e) {
        return new StackFrameInfo(e.getClassName(), e.getMethodName(), e.getFileName(), e.getLineNumber());
    }

    /** قالب استاندارد جاوا: {@code com.x.Y.m(Y.java:42)} */
    public String toJavaStyle() {
        String location;
        if (lineNumber == -2) {
            location = "Native Method";
        } else if (fileName != null && lineNumber >= 0) {
            location = fileName + ":" + lineNumber;
        } else if (fileName != null) {
            location = fileName;
        } else {
            location = "Unknown Source";
        }
        return className + "." + methodName + "(" + location + ")";
    }

    /** قالب کوتاه: {@code com.x.Y.m:42} */
    public String toShort() {
        return lineNumber > 0 ? className + "." + methodName + ":" + lineNumber : className + "." + methodName;
    }

    @Override
    public String toString() {
        return toJavaStyle();
    }
}
