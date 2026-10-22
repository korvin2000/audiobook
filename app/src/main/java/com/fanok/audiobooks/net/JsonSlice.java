package com.fanok.audiobooks.net;

/**
 * Cuts one balanced JSON/JS literal ({@code {…}} or {@code […]}) out of a page script, skipping
 * brackets inside quoted strings. Pure; a missing marker or an unbalanced literal throws a
 * descriptive {@link IllegalStateException} instead of an index error.
 */
public final class JsonSlice {

    private JsonSlice() {
    }

    /** The literal that opens with the first {@code open} after the first {@code marker}. */
    public static String after(String text, String marker, char open) {
        int at = text.indexOf(marker);
        if (at < 0) {
            throw new IllegalStateException("marker not found: " + marker);
        }
        return from(text, at + marker.length(), open);
    }

    /** The literal that opens with the first {@code open} ({@code '{'} or {@code '['}) at or after {@code start}. */
    public static String from(String text, int start, char open) {
        if (open != '{' && open != '[') {
            throw new IllegalArgumentException("open must be '{' or '[': " + open);
        }
        int begin = text.indexOf(open, start);
        if (begin < 0) {
            throw new IllegalStateException("no '" + open + "' after index " + start);
        }
        int depth = 0;
        char quote = 0;
        for (int i = begin; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if ((c == '}' || c == ']') && --depth == 0) {
                return text.substring(begin, i + 1);
            }
        }
        throw new IllegalStateException("unbalanced '" + open + "' at index " + begin);
    }
}
