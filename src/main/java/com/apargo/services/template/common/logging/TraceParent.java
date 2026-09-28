package com.apargo.services.template.common.logging;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W3C Trace Context {@code traceparent} values:
 * {@code 00-<32 hex trace id>-<16 hex parent id>-<2 hex flags>}.
 *
 * <p>This service keeps only the trace id (in the MDC). Each outbound call
 * gets a fresh parent id, which is what a span-less service should send: the
 * trace id is what ties the hops together.
 */
public final class TraceParent {

    /** The only W3C trace-context version this service reads and writes. */
    private static final String VERSION = "00";

    /** Trace flags sent on outbound calls: sampled. */
    private static final String FLAGS_SAMPLED = "01";

    private static final String SEPARATOR = "-";

    /** Trace id: 16 bytes; parent id: 8 bytes (W3C Trace Context §3.2). */
    private static final int TRACE_ID_BYTES = 16;
    private static final int PARENT_ID_BYTES = 8;

    private static final Pattern HEADER =
            Pattern.compile(VERSION + "-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}");

    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final HexFormat HEX = HexFormat.of();

    private TraceParent() {
    }

    /** A new random, non-zero 32-hex trace id. */
    public static String newTraceId() {
        return randomNonZeroHex(TRACE_ID_BYTES);
    }

    /**
     * The trace id from a well-formed {@code traceparent}; empty for anything
     * else, including the all-zero ids the spec declares invalid.
     */
    public static Optional<String> parseTraceId(String header) {
        if (header == null) {
            return Optional.empty();
        }
        Matcher m = HEADER.matcher(header.trim());
        if (!m.matches() || isAllZeros(m.group(1)) || isAllZeros(m.group(2))) {
            return Optional.empty();
        }
        return Optional.of(m.group(1));
    }

    /** {@code traceparent} for an outbound call in the given trace, sampled. */
    public static String format(String traceId) {
        return VERSION + SEPARATOR + traceId + SEPARATOR + randomNonZeroHex(PARENT_ID_BYTES)
                + SEPARATOR + FLAGS_SAMPLED;
    }

    public static boolean isValidTraceId(String traceId) {
        return traceId != null && TRACE_ID.matcher(traceId).matches() && !isAllZeros(traceId);
    }

    private static String randomNonZeroHex(int bytes) {
        byte[] buf = new byte[bytes];
        do {
            RANDOM.nextBytes(buf);
        } while (isAllZeroBytes(buf));
        return HEX.formatHex(buf);
    }

    private static boolean isAllZeros(String hex) {
        return hex.chars().allMatch(c -> c == '0');
    }

    private static boolean isAllZeroBytes(byte[] buf) {
        for (byte b : buf) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
