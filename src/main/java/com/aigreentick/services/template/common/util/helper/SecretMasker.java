package com.aigreentick.services.template.common.util.helper;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks secrets before they reach a log line.
 *
 * <p>Keeps the first and last {@value #VISIBLE} characters so two tokens can
 * still be told apart when debugging, e.g. {@code EAAG*****x9Qz}. Values too
 * short to keep that much without revealing most of the secret are fully
 * masked.
 */
public final class SecretMasker {

    private static final String MASK = "*****";
    private static final int VISIBLE = 4;
    private static final int MIN_LENGTH_TO_REVEAL = VISIBLE * 3;

    /** Matches the value of an {@code access_token} query parameter. */
    private static final Pattern ACCESS_TOKEN_PARAM =
            Pattern.compile("([?&]access_token=)([^&#]*)");

    private SecretMasker() {
    }

    /** {@code EAAGabc...xyz9Qz} → {@code EAAG*****x9Qz}; short or blank values → {@code *****}. */
    public static String mask(String secret) {
        if (secret == null) {
            return null;
        }
        if (secret.length() < MIN_LENGTH_TO_REVEAL) {
            return MASK;
        }
        return secret.substring(0, VISIBLE) + MASK + secret.substring(secret.length() - VISIBLE);
    }

    /** Returns the URI as a string with any {@code access_token} query value masked. */
    public static String maskUri(URI uri) {
        return uri == null ? null : maskUri(uri.toString());
    }

    /** Masks any {@code access_token} query value inside a URL or free text. */
    public static String maskUri(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = ACCESS_TOKEN_PARAM.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + mask(m.group(2))));
        }
        m.appendTail(out);
        return out.toString();
    }
}
