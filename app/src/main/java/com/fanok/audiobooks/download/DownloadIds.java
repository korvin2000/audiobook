package com.fanok.audiobooks.download;

import com.fanok.audiobooks.Url;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure identity of downloads. A progressive chapter is one download whose id and custom cache key are
 * {@code v1/<book>/chapter/<ordinal>}; an HLS stream (one URL for many chapters) is one download
 * {@code v1/<book>/stream/<n>} without a custom cache key (Media3 rejects one for HLS), shared by its chapters,
 * which keep their own clipping at playback. {@code <book>} is the canonical book URL without scheme, {@code www.},
 * trailing slash or fragment, so mirrors of one book and moved hosts give one id. Downloads made before these ids
 * keep the legacy id: the chapter URL without its query ({@link #legacyId}).
 */
public final class DownloadIds {

    public static final String VERSION = "v1";

    /** The download that holds one chapter. */
    public static final class Entry {
        public final String id;
        /** Custom cache key of the download; null for HLS. */
        public final String cacheKey;
        public final boolean hls;
        public final String legacyId;
        /** True when another chapter URL of the book has the same legacy id (query-addressed files). */
        public final boolean legacyShared;

        Entry(String id, String cacheKey, boolean hls, String legacyId, boolean legacyShared) {
            this.id = id;
            this.cacheKey = cacheKey;
            this.hls = hls;
            this.legacyId = legacyId;
            this.legacyShared = legacyShared;
        }
    }

    private DownloadIds() {
    }

    /** "host/path[?query]" of a book URL: moved hosts rewritten, scheme, www., trailing slashes and fragment dropped. */
    public static String bookKey(String bookUrl) {
        if (bookUrl == null) return "";
        String s = Url.canonical(bookUrl.trim());
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        String query = "";
        int q = s.indexOf('?');
        if (q >= 0) {
            query = q + 1 < s.length() ? s.substring(q) : "";
            s = s.substring(0, q);
        }
        int slash = s.indexOf('/');
        String host = (slash >= 0 ? s.substring(0, slash) : s).toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        String path = slash >= 0 ? s.substring(slash) : "";
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return host + path + query;
    }

    public static String chapterId(String bookUrl, int ordinal) {
        return VERSION + "/" + bookKey(bookUrl) + "/chapter/" + ordinal;
    }

    public static String streamId(String bookUrl, int n) {
        return VERSION + "/" + bookKey(bookUrl) + "/stream/" + n;
    }

    /** True for ids made by {@link #chapterId}/{@link #streamId} of this book. */
    public static boolean belongsTo(String downloadId, String bookUrl) {
        return downloadId != null && downloadId.startsWith(VERSION + "/" + bookKey(bookUrl) + "/");
    }

    /** HLS by media type as Media3 infers it for a URI without MIME type: the last path segment ends in ".m3u8". */
    public static boolean isHls(String url) {
        if (url == null) return false;
        String path = url;
        int cut = indexOfAny(path, "?#");
        if (cut >= 0) path = path.substring(0, cut);
        return path.toLowerCase(Locale.ROOT).endsWith(".m3u8");
    }

    /**
     * Downloads of the chapters of one book, aligned with {@code urls}. {@code ordinals[i]} is the chapter's recorded
     * ordinal, or -1 to use its position {@code i}; HLS chapters map to the index of their distinct stream URL.
     */
    public static List<Entry> forBook(String bookUrl, List<String> urls, int[] ordinals) {
        List<Entry> entries = new ArrayList<>(urls.size());
        Map<String, Integer> streams = new HashMap<>();
        Map<String, String> legacyOwner = new HashMap<>();
        Map<String, Boolean> legacyShared = new HashMap<>();
        for (String url : urls) {
            String legacy = legacyId(url);
            String owner = legacyOwner.get(legacy);
            if (owner == null) {
                legacyOwner.put(legacy, url);
            } else if (!owner.equals(url)) {
                legacyShared.put(legacy, true);
            }
        }
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String legacy = legacyId(url);
            boolean shared = legacyShared.containsKey(legacy);
            if (isHls(url)) {
                Integer n = streams.get(url);
                if (n == null) {
                    n = streams.size();
                    streams.put(url, n);
                }
                entries.add(new Entry(streamId(bookUrl, n), null, true, legacy, shared));
            } else {
                int ordinal = ordinals != null && i < ordinals.length && ordinals[i] >= 0 ? ordinals[i] : i;
                String id = chapterId(bookUrl, ordinal);
                entries.add(new Entry(id, id, false, legacy, shared));
            }
        }
        return entries;
    }

    /**
     * For each of {@code urls} the index of the row with the same URL (the k-th occurrence maps to the k-th row
     * with that URL), or -1 when no row has it.
     */
    public static int[] align(List<String> rowUrls, List<String> urls) {
        Map<String, List<Integer>> byUrl = new HashMap<>();
        for (int j = 0; j < rowUrls.size(); j++) {
            String url = rowUrls.get(j);
            List<Integer> same = byUrl.get(url);
            if (same == null) {
                same = new ArrayList<>();
                byUrl.put(url, same);
            }
            same.add(j);
        }
        Map<String, Integer> seen = new HashMap<>();
        int[] result = new int[urls.size()];
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            List<Integer> same = byUrl.get(url);
            int k = seen.containsKey(url) ? seen.get(url) : 0;
            seen.put(url, k + 1);
            result[i] = same == null ? -1 : same.get(Math.min(k, same.size() - 1));
        }
        return result;
    }

    /**
     * The id downloads had before {@code v1}: scheme, authority and path without query, encoded exactly as
     * {@code android.net.Uri.Builder} re-encodes the decoded parts ({@code AudioPOJO.getCleanUrl()}).
     */
    public static String legacyId(String url) {
        if (url == null || url.isEmpty()) return "";
        int colon = url.indexOf(':');
        int stop = indexOfAny(url, "/?#");
        if (colon <= 0 || (stop >= 0 && stop < colon)) return url;
        String scheme = url.substring(0, colon);
        String ssp = url.substring(colon + 1);
        int end = indexOfAny(ssp, "?#");
        if (end >= 0) ssp = ssp.substring(0, end);
        StringBuilder b = new StringBuilder(scheme).append(':');
        String path = ssp;
        if (ssp.startsWith("//")) {
            int slash = ssp.indexOf('/', 2);
            String authority = slash >= 0 ? ssp.substring(2, slash) : ssp.substring(2);
            path = slash >= 0 ? ssp.substring(slash) : "";
            b.append("//").append(encode(decode(authority), ""));
        }
        return b.append(encode(decode(path), "/")).toString();
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }

    /** android.net.Uri.decode: %XX sequences as UTF-8, malformed escapes kept literally. */
    private static String decode(String s) {
        if (s.indexOf('%') < 0) return s;
        StringBuilder out = new StringBuilder();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()
                    && Character.digit(s.charAt(i + 1), 16) >= 0 && Character.digit(s.charAt(i + 2), 16) >= 0) {
                bytes.write((Character.digit(s.charAt(i + 1), 16) << 4) | Character.digit(s.charAt(i + 2), 16));
                i += 3;
                continue;
            }
            if (bytes.size() > 0) {
                out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                bytes.reset();
            }
            out.append(c);
            i++;
        }
        if (bytes.size() > 0) out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        return out.toString();
    }

    /** android.net.Uri.encode: everything but [A-Za-z0-9_!.~'()*-] and {@code allow} as %XX of its UTF-8 bytes. */
    private static String encode(String s, String allow) {
        StringBuilder out = new StringBuilder();
        for (byte raw : s.getBytes(StandardCharsets.UTF_8)) {
            int c = raw & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || "_-!.~'()*".indexOf(c) >= 0 || (c < 0x80 && allow.indexOf(c) >= 0)) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return out.toString();
    }
}
