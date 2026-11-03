package com.apargo.services.template.application.audit;

/**
 * Where an unexpected exception came from, in a form safe for the audit
 * trail.
 *
 * <p>Class names and one stack frame only. The exception's message is never
 * read: an upstream or driver message can carry a URL, a response body or a
 * row, which is what the audit contract keeps out of {@code SYSTEM} failures.
 * The frame is what finds the bug.
 */
final class FailureOrigin {

    /** Our own code, as opposed to the JDK, Spring or a driver. */
    private static final String[] OWN_PACKAGES = {"com.apargo.", "com.aigreentick."};

    /** A cause chain longer than this is a cycle or a pathology; stop walking. */
    private static final int MAX_DEPTH = 32;

    private FailureOrigin() {
    }

    /** The deepest cause, or {@code failure} itself when it has none. */
    static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        for (int depth = 0; depth < MAX_DEPTH && root.getCause() != null && root.getCause() != root; depth++) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * The first frame in our own code, as {@code Class.method(File.java:line)}.
     * Searched on the root cause first, then on the exception itself (a
     * failure whose root is entirely inside a library); falls back to the root
     * cause's top frame, and is {@code null} when there is no stack at all.
     */
    static String origin(Throwable failure) {
        Throwable root = rootCause(failure);
        StackTraceElement frame = firstOwnFrame(root);
        if (frame == null && root != failure) {
            frame = firstOwnFrame(failure);
        }
        if (frame == null && root.getStackTrace().length > 0) {
            frame = root.getStackTrace()[0];
        }
        return frame == null ? null : format(frame);
    }

    private static StackTraceElement firstOwnFrame(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            String className = frame.getClassName();
            // A CGLIB proxy frame names our class but is Spring's code.
            if (isOwn(className) && !className.contains("$$")) {
                return frame;
            }
        }
        return null;
    }

    private static boolean isOwn(String className) {
        for (String prefix : OWN_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String format(StackTraceElement frame) {
        String className = frame.getClassName();
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String location = frame.getFileName() == null ? "Unknown Source"
                : frame.getLineNumber() > 0 ? frame.getFileName() + ":" + frame.getLineNumber()
                : frame.getFileName();
        return simpleName + "." + frame.getMethodName() + "(" + location + ")";
    }
}
