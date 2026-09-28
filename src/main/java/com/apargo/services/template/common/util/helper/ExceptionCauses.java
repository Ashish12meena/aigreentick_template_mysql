package com.apargo.services.template.common.util.helper;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Questions about an exception's cause chain, answered in one place so the
 * HTTP error mapping and the audit error mapping always agree.
 */
public final class ExceptionCauses {

    /** Suffix of Netty's {@code ReadTimeoutException} / {@code ConnectTimeoutException}. */
    private static final String TIMEOUT_EXCEPTION_SUFFIX = "TimeoutException";

    private ExceptionCauses() {
    }

    /**
     * True when any cause is a timeout. Netty's timeout types are matched by
     * name so callers need no dependency on Netty.
     */
    public static boolean hasTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TimeoutException
                    || t instanceof SocketTimeoutException
                    || t.getClass().getSimpleName().endsWith(TIMEOUT_EXCEPTION_SUFFIX)) {
                return true;
            }
        }
        return false;
    }
}
