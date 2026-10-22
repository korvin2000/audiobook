package com.fanok.audiobooks.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure plan for deleting chapter rows. A row owns at most two things: the Media3 download whose id derives from
 * its remote {@code url_audio}, and the exported copy named exactly by its {@code local_uri}. Nothing else is ever
 * planned for deletion: no name matches, no folders, and no files of imported books (their {@code url_audio} is the
 * user's own file, so removing them from the library removes rows only).
 */
public final class DeletionPlanner {

    /** Derives the Media3 download id from a remote chapter URL, exactly as the download request did. */
    public interface DownloadIds {
        String of(String remoteUrl);
    }

    public static final class Row {
        public final String urlBook;
        public final String urlAudio;
        public final String localUri;

        public Row(String urlBook, String urlAudio, String localUri) {
            this.urlBook = urlBook;
            this.urlAudio = urlAudio;
            this.localUri = localUri;
        }
    }

    public static final class Plan {
        public final Set<String> downloadIdsToRemove = new LinkedHashSet<>();
        public final Set<String> urisToDelete = new LinkedHashSet<>();
        /** Online chapters that stay in the library once their exported copy is gone. */
        public final List<Row> rowsToClearLocalUri = new ArrayList<>();
        /** Rows removed from the library. */
        public final List<Row> rowsToRemove = new ArrayList<>();
    }

    private DeletionPlanner() {
    }

    /**
     * @param keepOnlineRows true to delete only the downloaded copies of the chapters (rows of online books stay,
     *                       rows of imported books leave the library); false to remove the rows themselves.
     * @param roots          configured download roots; a recorded URI equal to a root is never deleted.
     */
    public static Plan plan(Collection<Row> rows, Collection<String> roots, boolean keepOnlineRows, DownloadIds ids) {
        Plan plan = new Plan();
        for (Row row : rows) {
            if (isRemote(row.urlAudio)) {
                String id = ids.of(row.urlAudio);
                if (!isEmpty(id)) plan.downloadIdsToRemove.add(id);
            }
            boolean hasCopy = !isEmpty(row.localUri) && !isRoot(row.localUri, roots);
            if (hasCopy) plan.urisToDelete.add(row.localUri);
            if (!keepOnlineRows || isImportedBook(row.urlBook)) {
                plan.rowsToRemove.add(row);
            } else if (hasCopy) {
                plan.rowsToClearLocalUri.add(row);
            }
        }
        return plan;
    }

    public static boolean isRemote(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /** Books created by the folder scan carry a synthetic ".../imported/..." URL. */
    public static boolean isImportedBook(String urlBook) {
        return urlBook != null && urlBook.contains("/imported/");
    }

    /**
     * The configured root that strictly contains {@code path} (plain or file:// path), or null. Empty folders are
     * pruned only below it, never the root itself.
     */
    public static String enclosingRoot(String path, Collection<String> roots) {
        String p = normalize(path);
        String best = null;
        for (String root : roots) {
            String r = normalize(root);
            if (!r.isEmpty() && p.startsWith(r + "/") && (best == null || r.length() > best.length())) best = r;
        }
        return best;
    }

    private static boolean isRoot(String uri, Collection<String> roots) {
        String u = normalize(uri);
        for (String root : roots) {
            if (u.equals(normalize(root))) return true;
        }
        return false;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = s.startsWith("file://") ? s.substring("file://".length()) : s;
        while (n.length() > 1 && n.endsWith("/")) n = n.substring(0, n.length() - 1);
        return n;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
