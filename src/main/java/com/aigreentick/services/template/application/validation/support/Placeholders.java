package com.aigreentick.services.template.application.validation.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing helpers for Meta's {{n}} / {{name}} placeholder syntax. */
public final class Placeholders {

    private static final Pattern ANY = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");
    private static final Pattern POSITIONAL_TOKEN = Pattern.compile("^\\d+$");
    private static final Pattern ADJACENT = Pattern.compile("\\}\\}\\s*\\{\\{");

    private Placeholders() {}

    public static List<String> tokens(String text) {
        List<String> found = new ArrayList<>();
        if (text == null) return found;
        Matcher m = ANY.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    /** Distinct positional indexes, ascending. */
    public static List<Integer> positionalIndexes(String text) {
        return tokens(text).stream()
                .filter(t -> POSITIONAL_TOKEN.matcher(t).matches())
                .map(Integer::parseInt)
                .distinct()
                .sorted()
                .toList();
    }

    public static boolean hasNamed(String text) {
        return tokens(text).stream().anyMatch(t -> !POSITIONAL_TOKEN.matcher(t).matches());
    }

    public static boolean hasPositional(String text) {
        return tokens(text).stream().anyMatch(t -> POSITIONAL_TOKEN.matcher(t).matches());
    }

    public static boolean startsWithPlaceholder(String text) {
        return text != null && text.stripLeading().startsWith("{{");
    }

    public static boolean endsWithPlaceholder(String text) {
        return text != null && text.stripTrailing().endsWith("}}");
    }

    public static boolean hasAdjacentPlaceholders(String text) {
        return text != null && ADJACENT.matcher(text).find();
    }
}