package com.fanok.audiobooks.service;

import static com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.PlatformScheduler;
import androidx.media3.exoplayer.scheduler.Scheduler;
import com.fanok.audiobooks.NotificationDownload;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@UnstableApi
public class ExoDownloadService extends DownloadService {


    public static final String CHANNEL_ID = "download_channel";
    private static final int JOB_ID = 1;
    public static final int FOREGROUND_NOTIFICATION_ID = 98;

    private DownloadManager downloadManager;
    private DownloadManagerListener listener;


    public ExoDownloadService() {
        super(FOREGROUND_NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        downloadManager = getDownloadManager();
        listener = new DownloadManagerListener(this);
        downloadManager.addListener(listener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        DownloadManager currentManager = getDownloadManager();
        if (this.downloadManager != currentManager) {
            if (this.downloadManager != null && this.listener != null) {
                try {
                    this.downloadManager.removeListener(this.listener);
                } catch (Exception ignored) {}
            }
            this.downloadManager = currentManager;
            if (this.downloadManager != null && this.listener != null) {
                this.downloadManager.addListener(this.listener);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        downloadManager.removeListener(listener);
        super.onDestroy();
    }

    @NotNull
    @Override
    protected DownloadManager getDownloadManager() {
        return DownloadUtil.getDownloadManager();
    }

    @Override
    protected Scheduler getScheduler() {
        return new PlatformScheduler(this, JOB_ID);
    }

    @NotNull
    @Override
    protected Notification getForegroundNotification(@NotNull List<Download> downloads, int notMetRequirements) {
        Download current = downloads.stream()
                .filter(d -> d.state == Download.STATE_DOWNLOADING)
                .findFirst()
                .orElse(null);

        int progress = 0;
        if (current != null) {
            if (current.getPercentDownloaded() != C.PERCENTAGE_UNSET) {
                progress = Math.max(0, Math.min(100, Math.round(current.getPercentDownloaded())));
            } else if (current.contentLength > 0) {
                progress = (int) Math.round(100.0 * current.getBytesDownloaded() / (double) current.contentLength);
                progress = Math.max(0, Math.min(100, progress));
            }
        }

        boolean isDownloading = current != null && current.state== Download.STATE_DOWNLOADING;
        String id = null;
        if (current != null) {
            id = current.request.id;
        }
        return NotificationDownload.getNotification(this, id, progress, isDownloading);
    }

    private static class DownloadManagerListener implements DownloadManager.Listener {
        private final ExoDownloadService downloadService;

        public DownloadManagerListener(ExoDownloadService downloadService) {
            this.downloadService = downloadService;
        }



        @Override
        public void onDownloadChanged(@NonNull DownloadManager downloadManager, Download download, @Nullable Exception finalException) {
            android.util.Log.d("DL", "id=" + download.request.id
                    + " state=" + download.state
                    + " reason=" + download.failureReason
                    + " stopReason=" + download.stopReason
                    + " bytes=" + download.getBytesDownloaded());
            if (finalException != null) android.util.Log.w("DL", "err", finalException);

            if (download.state == Download.STATE_COMPLETED) {
                final byte[] data = download.request.data;
                if (data != null && data.length > 0) {
                    new Thread(() -> {
                        try {
                            Gson gson = new Gson();
                            Type type = new TypeToken<BookPOJO>() {}.getType();
                            BookPOJO book = gson.fromJson(new String(data), type);
                            if (book != null) {
                                BooksDBModel dbModel = new BooksDBModel(downloadService);
                                dbModel.addSaved(book);
                                dbModel.closeDB();
                                exportDownloadedFile(downloadService, download, book);
                            }
                        } catch (Exception e) {
                            android.util.Log.e("DL", "Export on background failed", e);
                        }
                    }).start();
                }
            }
            downloadService.sendBroadcast(new Intent(Broadcast_UPDATE_ADAPTER));
        }

        private void exportDownloadedFile(Context context, Download download, BookPOJO book) {
            try {
                AudioListDBModel audioListDBModel = new AudioListDBModel(context);
                java.util.ArrayList<com.fanok.audiobooks.pojo.AudioListPOJO> list = audioListDBModel.get(book.getUrl());
                audioListDBModel.closeDB();

                String audioName = null;
                if (list != null) {
                    for (com.fanok.audiobooks.pojo.AudioListPOJO pojo : list) {
                        if (download.request.id.equals(pojo.getCleanAudioUrl()) || download.request.uri.toString().contains(pojo.getAudioUrl())) {
                            audioName = pojo.getAudioName();
                            break;
                        }
                    }
                }

                if (audioName == null || audioName.isEmpty()) {
                    audioName = download.request.id.substring(download.request.id.lastIndexOf('/') + 1);
                    if (audioName.contains("?")) audioName = audioName.substring(0, audioName.indexOf('?'));
                }

                File targetDir = DownloadStorageHelper.getBookDownloadDir(context, book.getUrl(), book.getName(), book.getArtist(), book.getAutor());
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }

                if (book.getPhoto() != null && !book.getPhoto().isEmpty()) {
                    File coverFile = new File(targetDir, "cover.jpg");
                    if (!coverFile.exists()) {
                        new Thread(() -> {
                            try {
                                okhttp3.Request request = new okhttp3.Request.Builder()
                                        .url(book.getPhoto())
                                        .header("User-Agent", com.fanok.audiobooks.Consts.USER_AGENT)
                                        .build();
                                try (okhttp3.Response response = com.fanok.audiobooks.App.getOkHttpClient().newCall(request).execute()) {
                                    if (response.isSuccessful() && response.body() != null) {
                                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(coverFile)) {
                                            fos.write(response.body().bytes());
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }).start();
                    }
                }

                String fileName = DownloadStorageHelper.sanitizeFileName(audioName);
                if (!fileName.toLowerCase().endsWith(".mp3") && !fileName.toLowerCase().endsWith(".ts") && !fileName.toLowerCase().endsWith(".aac") && !fileName.toLowerCase().endsWith(".m4a")) {
                    fileName += ".mp3";
                }
                File targetFile = new File(targetDir, fileName);

                androidx.media3.datasource.cache.Cache cache = DownloadUtil.getDownloadCache();
                java.util.Set<String> keys = cache.getKeys();

                String reqUriStr = download.request.uri.toString();
                if (reqUriStr.contains("m3u8") || reqUriStr.contains("hls")) {
                    exportHlsDownload(context, download, book, targetDir, audioName, cache, keys);
                    return;
                }

                String uriPathPrefix = reqUriStr;
                if (uriPathPrefix.contains("?")) {
                    uriPathPrefix = uriPathPrefix.substring(0, uriPathPrefix.indexOf('?'));
                }
                if (uriPathPrefix.lastIndexOf('/') > 8) {
                    uriPathPrefix = uriPathPrefix.substring(0, uriPathPrefix.lastIndexOf('/'));
                }

                java.util.List<androidx.media3.datasource.cache.CacheSpan> spanList = new java.util.ArrayList<>();
                for (String key : keys) {
                    if (key.equals(download.request.id) || key.contains(download.request.id) || key.contains(uriPathPrefix) || reqUriStr.contains(key)) {
                        java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan> spans = cache.getCachedSpans(key);
                        if (spans != null) {
                            for (androidx.media3.datasource.cache.CacheSpan span : spans) {
                                if (span.file != null && span.file.exists() && span.length > 0) {
                                    spanList.add(span);
                                }
                            }
                        }
                    }
                }

                java.util.Collections.sort(spanList, (s1, s2) -> Long.compare(s1.position, s2.position));

                if (spanList.isEmpty()) {
                    java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan> spans = cache.getCachedSpans(download.request.id);
                    if (spans == null || spans.isEmpty()) {
                        spans = cache.getCachedSpans(download.request.uri.toString());
                    }
                    if (spans != null) {
                        for (androidx.media3.datasource.cache.CacheSpan span : spans) {
                            if (span.file != null && span.file.exists()) {
                                spanList.add(span);
                            }
                        }
                    }
                    java.util.Collections.sort(spanList, (s1, s2) -> Long.compare(s1.position, s2.position));
                }
                if (!spanList.isEmpty()) {
                    String[] finalPathOrUri = new String[1];
                    try (java.io.OutputStream fos = DownloadStorageHelper.openOutputStreamForExport(
                            context, book.getUrl(), book.getName(), book.getArtist(), book.getAutor(),
                            fileName, "audio/mpeg", finalPathOrUri)) {

                        for (androidx.media3.datasource.cache.CacheSpan span : spanList) {
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(span.file)) {
                                byte[] buffer = new byte[16384];
                                int read;
                                while ((read = fis.read(buffer)) != -1) {
                                    fos.write(buffer, 0, read);
                                }
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DL", "Failed writing exported file", e);
                    }

                    if (finalPathOrUri[0] != null && !finalPathOrUri[0].isEmpty()) {
                        try {
                            AudioListDBModel dbModel = new AudioListDBModel(context);
                            com.fanok.audiobooks.room.BooksAudioEntity entity = dbModel.getDatabase().booksAudioDao()
                                    .getByUrlAndAudioUrl(book.getUrl(), download.request.id);
                            if (entity == null && download.request.uri != null) {
                                entity = dbModel.getDatabase().booksAudioDao()
                                        .getByUrlAndAudioUrl(book.getUrl(), download.request.uri.toString());
                            }
                            if (entity != null) {
                                entity.urlAudio = finalPathOrUri[0];
                                dbModel.getDatabase().booksAudioDao().insert(entity);
                            }
                            dbModel.closeDB();
                        } catch (Exception e) {
                            android.util.Log.e("DL", "Failed to update track URL to local path in DB", e);
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("DL", "Error exporting downloaded file", e);
            }
        }

        private void exportHlsDownload(Context context, Download download, BookPOJO book, File targetDir,
                                       String audioName, androidx.media3.datasource.cache.Cache cache,
                                       java.util.Set<String> keys) {
            try {
                String playlistKey = findPlaylistKey(cache, keys, download.request.id);
                if (playlistKey == null) {
                    android.util.Log.w("DL", "HLS export: playlist key not found");
                    return;
                }

                String playlistText = readCachedText(cache, playlistKey);
                if (playlistText == null || playlistText.isEmpty()) {
                    android.util.Log.w("DL", "HLS export: playlist text empty");
                    return;
                }

                if (playlistText.contains("#EXT-X-STREAM-INF")) {
                    String mediaKey = findMediaPlaylistKey(cache, keys, playlistKey);
                    if (mediaKey != null) {
                        String media = readCachedText(cache, mediaKey);
                        if (media != null && !media.isEmpty() && !media.contains("#EXT-X-STREAM-INF")) {
                            playlistText = media;
                            playlistKey = mediaKey;
                        }
                    }
                }

                String base = urlBase(playlistKey);

                java.util.List<String> segmentUrls = new java.util.ArrayList<>();
                String initUrl = null;
                String keyUrl = null;
                String keyIv = null;
                boolean encrypted = false;
                long mediaSequence = 0;
                for (String line : playlistText.split("\n")) {
                    String t = line.trim();
                    if (t.isEmpty()) continue;
                    if (t.startsWith("#EXT-X-KEY:")) {
                        if (t.contains("METHOD=AES-128")) {
                            encrypted = true;
                            int ki = t.indexOf("URI=\"");
                            if (ki >= 0) {
                                String ku = t.substring(ki + 5);
                                int ci = ku.indexOf('"');
                                if (ci > 0) ku = ku.substring(0, ci);
                                keyUrl = resolveUrl(base, ku);
                            }
                            int vi = t.indexOf("IV=0x");
                            if (vi >= 0) {
                                int ve = vi + 5;
                                while (ve < t.length()
                                        && java.lang.Character.digit(t.charAt(ve), 16) >= 0) ve++;
                                keyIv = t.substring(vi + 5, ve);
                            }
                        } else if (t.contains("METHOD=NONE")) {
                            encrypted = false;
                        }
                        continue;
                    }
                    if (t.startsWith("#EXT-X-MAP:")) {
                        int qi = t.indexOf("URI=\"");
                        if (qi >= 0) {
                            String u = t.substring(qi + 5);
                            int ci = u.indexOf('"');
                            if (ci > 0) u = u.substring(0, ci);
                            initUrl = resolveUrl(base, u);
                        }
                        continue;
                    }
                    if (t.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                        mediaSequence = parseLongSafely(t.substring(t.indexOf(':') + 1));
                        continue;
                    }
                    if (t.startsWith("#")) continue;
                    segmentUrls.add(resolveUrl(base, t));
                }

                if (initUrl != null) segmentUrls.add(0, initUrl);

                java.util.List<File> segmentFiles = new java.util.ArrayList<>();
                int missing = 0;
                for (String url : segmentUrls) {
                    java.util.List<File> files = resolveCachedFiles(cache, keys, url);
                    if (files.isEmpty()) {
                        missing++;
                        android.util.Log.w("DL", "HLS export: segment NOT in cache: " + url);
                        continue;
                    }
                    segmentFiles.addAll(files);
                }
                if (segmentFiles.isEmpty()) {
                    android.util.Log.w("DL", "HLS export: no segments found in cache");
                    return;
                }
                android.util.Log.d("DL", "HLS export: expected=" + segmentUrls.size()
                        + " found=" + segmentFiles.size() + " missing=" + missing);

                String ext = detectContainerExt(segmentFiles.get(0));
                if (ext == null) {
                    android.util.Log.w("DL", "HLS export: unknown segment container ("
                            + segmentFiles.get(0).length() + "B), fallback .mp3");
                    ext = ".mp3";
                }

                if (ext.equals(".m4a") && !hasBox(segmentFiles.get(0), "ftyp")) {
                    File init = findInitFile(cache, keys, base);
                    if (init != null) {
                        segmentFiles.add(0, init);
                    } else {
                        android.util.Log.w("DL", "HLS export: fMP4 init segment not found in cache");
                    }
                }

                byte[] aesKey = null;
                if (encrypted) {
                    aesKey = readKeyFromCache(cache, keys, keyUrl);
                    if (aesKey == null) {
                        android.util.Log.e("DL", "HLS export: AES-128 key not found in cache, abort");
                        return;
                    }
                }

                String baseName = com.fanok.audiobooks.util.DownloadStorageHelper.sanitizeFileName(audioName);
                if (baseName.toLowerCase().endsWith(".ts") || baseName.toLowerCase().endsWith(".aac")
                        || baseName.toLowerCase().endsWith(".mp3") || baseName.toLowerCase().endsWith(".m4a")
                        || baseName.toLowerCase().endsWith(".ogg") || baseName.toLowerCase().endsWith(".flac")) {
                    int dot = baseName.lastIndexOf('.');
                    if (dot > 0) baseName = baseName.substring(0, dot);
                }
                String fullFileName = baseName + ext;
                String[] finalPathOrUri = new String[1];
                try (java.io.OutputStream os = DownloadStorageHelper.openOutputStreamForExport(
                        context, book.getUrl(), book.getName(), book.getArtist(), book.getAutor(),
                        fullFileName, "audio/mpeg", finalPathOrUri)) {
                    writeHlsFile(os, segmentFiles, aesKey, keyIv, mediaSequence);
                }

                if (finalPathOrUri[0] != null && !finalPathOrUri[0].isEmpty()) {
                    try {
                        AudioListDBModel dbModel = new AudioListDBModel(context);
                        com.fanok.audiobooks.room.BooksAudioEntity entity = dbModel.getDatabase().booksAudioDao()
                                .getByUrlAndAudioUrl(book.getUrl(), download.request.id);
                        if (entity == null && download.request.uri != null) {
                            entity = dbModel.getDatabase().booksAudioDao()
                                    .getByUrlAndAudioUrl(book.getUrl(), download.request.uri.toString());
                        }
                        if (entity != null) {
                            entity.urlAudio = finalPathOrUri[0];
                            dbModel.getDatabase().booksAudioDao().insert(entity);
                        }
                        dbModel.closeDB();
                    } catch (Exception e) {
                        android.util.Log.e("DL", "Failed to update HLS track URL to local path in DB", e);
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("DL", "Error exporting HLS download", e);
            }
        }

        private String readCachedText(androidx.media3.datasource.cache.Cache cache, String key) {
            StringBuilder sb = new StringBuilder();
            java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan> spans = cache.getCachedSpans(key);
            if (spans != null) {
                for (androidx.media3.datasource.cache.CacheSpan span : spans) {
                    if (span.file != null && span.file.exists() && span.length > 0) {
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(span.file)) {
                            byte[] buf = new byte[16384];
                            int r;
                            while ((r = fis.read(buf)) != -1) {
                                sb.append(new String(buf, 0, r, java.nio.charset.StandardCharsets.UTF_8));
                            }
                        } catch (Exception e) {
                            android.util.Log.w("DL", "Error reading cached playlist chunk", e);
                        }
                    }
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }

        private String detectContainerExt(File first) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(first)) {
                byte[] head = new byte[32];
                int r = fis.read(head);
                if (r >= 3) {
                    if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) return ".mp3";
                    if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return ".mp3";
                    if ((head[0] & 0xFF) == 0x47) return ".ts";
                    if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xF0) == 0xF0) return ".aac";
                }
                if (r >= 8) {
                    if (head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') return ".ogg";
                    if (head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') return ".flac";
                    if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F') return ".wav";
                    if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return ".m4a";
                    if (head[4] == 's' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return ".m4a";
                    if (head[4] == 'm' && head[5] == 'o' && head[6] == 'o' && head[7] == 'f') return ".m4a";
                    if (head[4] == 'm' && head[5] == 'o' && head[6] == 'o' && head[7] == 'v') return ".m4a";
                    if (head[4] == 'm' && head[5] == 'd' && head[6] == 'a' && head[7] == 't') return ".m4a";
                    if (head[4] == 's' && head[5] == 'i' && head[6] == 'd' && head[7] == 'x') return ".m4a";
                    if (head[4] == 'f' && head[5] == 'r' && head[6] == 'e' && head[7] == 'e') return ".m4a";
                    if (head[4] == 's' && head[5] == 'k' && head[6] == 'i' && head[7] == 'p') return ".m4a";
                    if (isPrintableBox(head[4]) && isPrintableBox(head[5])
                            && isPrintableBox(head[6]) && isPrintableBox(head[7])) return ".m4a";
                }
            } catch (Exception ignored) {}
            return null;
        }

        private boolean isPrintableBox(byte b) {
            return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || b == ' ';
        }

        private String findPlaylistKey(androidx.media3.datasource.cache.Cache cache,
                                       java.util.Set<String> keys, String requestId) {
            for (String key : keys) {
                if ((key.contains("m3u8") || key.contains("hls"))
                        && (key.equals(requestId) || key.contains(requestId) || requestId.contains(key))) {
                    return key;
                }
            }
            return null;
        }

        private String findMediaPlaylistKey(androidx.media3.datasource.cache.Cache cache,
                                            java.util.Set<String> keys, String playlistKey) {
            String base = urlBase(playlistKey);
            for (String key : keys) {
                if ((key.contains("m3u8") || key.contains("hls")) && !key.equals(playlistKey)
                        && key.startsWith(base)) {
                    String media = readCachedText(cache, key);
                    if (media != null && !media.isEmpty() && !media.contains("#EXT-X-STREAM-INF")) {
                        return key;
                    }
                }
            }
            for (String key : keys) {
                if ((key.contains("m3u8") || key.contains("hls")) && !key.equals(playlistKey)) {
                    String media = readCachedText(cache, key);
                    if (media != null && !media.isEmpty() && !media.contains("#EXT-X-STREAM-INF")) {
                        return key;
                    }
                }
            }
            return null;
        }

        private String urlBase(String url) {
            int q = url.indexOf('?');
            if (q >= 0) url = url.substring(0, q);
            int s = url.lastIndexOf('/');
            return s >= 0 ? url.substring(0, s + 1) : url;
        }

        private String stripQuery(String url) {
            int q = url.indexOf('?');
            return q >= 0 ? url.substring(0, q) : url;
        }

        private String resolveUrl(String base, String ref) {
            if (ref.startsWith("http://") || ref.startsWith("https://")) return ref;
            if (ref.startsWith("//")) return "https:" + ref;
            return base + ref;
        }

        private long parseLongSafely(String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception e) {
                return 0L;
            }
        }

        private java.util.List<File> resolveCachedFiles(androidx.media3.datasource.cache.Cache cache,
                                                        java.util.Set<String> keys, String url) {
            java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan> spans = cache.getCachedSpans(url);
            if ((spans == null || spans.isEmpty()) && keys != null) {
                String noQ = stripQuery(url);
                for (String k : keys) {
                    if (k.equals(url) || stripQuery(k).equals(noQ)
                            || k.contains(url) || url.contains(k)) {
                        java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan> s =
                                cache.getCachedSpans(k);
                        if (s != null && !s.isEmpty()) {
                            spans = s;
                            break;
                        }
                    }
                }
            }
            java.util.List<File> out = new java.util.ArrayList<>();
            if (spans != null) {
                java.util.List<androidx.media3.datasource.cache.CacheSpan> sorted =
                        new java.util.ArrayList<>(spans);
                sorted.sort((a, b) -> Long.compare(a.position, b.position));
                for (androidx.media3.datasource.cache.CacheSpan span : sorted) {
                    if (span.file != null && span.file.exists() && span.length > 0) {
                        out.add(span.file);
                    }
                }
            }
            return out;
        }

        private File findInitFile(androidx.media3.datasource.cache.Cache cache,
                                  java.util.Set<String> keys, String base) {
            for (String k : keys) {
                if (!k.startsWith(base)) continue;
                java.util.List<File> files = resolveCachedFiles(cache, keys, k);
                for (File f : files) {
                    if (hasBox(f, "ftyp")) return f;
                }
            }
            return null;
        }

        private byte[] readKeyFromCache(androidx.media3.datasource.cache.Cache cache,
                                        java.util.Set<String> keys, String keyUrl) {
            if (keyUrl == null) return null;
            java.util.List<File> files = resolveCachedFiles(cache, keys, keyUrl);
            if (files.isEmpty()) return null;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(files.get(0))) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = fis.read(buf)) != -1) bos.write(buf, 0, r);
                byte[] key = bos.toByteArray();
                return key.length == 16 ? key : null;
            } catch (Exception e) {
                return null;
            }
        }
        private void writeHlsFile(java.io.OutputStream os, java.util.List<File> segmentFiles,
                                  byte[] aesKey, String ivHex, long mediaSequence) {
            try {
                byte[] buf = new byte[65536];
                long seq = mediaSequence;
                for (File sf : segmentFiles) {
                    byte[] data;
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(sf)) {
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        int r;
                        while ((r = fis.read(buf)) != -1) bos.write(buf, 0, r);
                        data = bos.toByteArray();
                    }
                    if (aesKey != null) {
                        byte[] iv = ivHex != null ? hexToBytes(ivHex) : longToIv(seq);
                        data = aesCbcDecrypt(aesKey, iv, data);
                        seq++;
                    }
                    os.write(data);
                }
                os.flush();
            } catch (Exception e) {
                android.util.Log.e("DL", "HLS export: write failed", e);
            }
        }

        private byte[] hexToBytes(String hex) {
            byte[] out = new byte[hex.length() / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }

        private byte[] longToIv(long seq) {
            byte[] iv = new byte[16];
            for (int i = 15; i >= 0; i--) {
                iv[i] = (byte) (seq & 0xFF);
                seq >>>= 8;
            }
            return iv;
        }

        private byte[] aesCbcDecrypt(byte[] key, byte[] iv, byte[] data) {
            if (data == null || data.length == 0 || data.length % 16 != 0) return data;
            try {
                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                        new javax.crypto.spec.SecretKeySpec(key, "AES"),
                        new javax.crypto.spec.IvParameterSpec(iv));
                return cipher.doFinal(data);
            } catch (Exception e) {
                try {
                    javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
                    cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                            new javax.crypto.spec.SecretKeySpec(key, "AES"),
                            new javax.crypto.spec.IvParameterSpec(iv));
                    byte[] dec = cipher.doFinal(data);
                    int end = dec.length;
                    while (end > 0 && dec[end - 1] == 0) end--;
                    return end > 0 ? java.util.Arrays.copyOf(dec, end) : dec;
                } catch (Exception e2) {
                    android.util.Log.e("DL", "AES decrypt failed", e2);
                    return data;
                }
            }
        }

        private boolean hasBox(File f, String box) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                byte[] head = new byte[8];
                int r = fis.read(head);
                return r >= 8 && head[4] == box.charAt(0) && head[5] == box.charAt(1)
                        && head[6] == box.charAt(2) && head[7] == box.charAt(3);
            } catch (Exception ignored) {}
            return false;
        }

        @Override
        public void onDownloadsPausedChanged(@NonNull DownloadManager downloadManager, boolean downloadsPaused) {
            downloadService.sendBroadcast(new Intent(Broadcast_UPDATE_ADAPTER));
        }

        @Override
        public void onDownloadRemoved(@NonNull DownloadManager downloadManager, Download download) throws RuntimeException {
            String bookUrl = null;
            BookPOJO book = null;
            if (download.request.data != null && download.request.data.length > 0) {
                try {
                    String bookJson = new String(download.request.data);
                    Gson gson = new Gson();
                    Type type = new TypeToken<BookPOJO>() {}.getType();
                    book = gson.fromJson(bookJson, type);
                    if (book != null) {
                        bookUrl = book.getUrl();
                    }
                } catch (Exception e) {
                    android.util.Log.w("DL", "Error parsing book json on remove", e);
                }
            }

            if (bookUrl == null && download.request.uri != null) {
                String ref = download.request.uri.getQueryParameter("__ref");
                if (ref != null) {
                    if (ref.contains("%2F") || ref.contains("%3A")) {
                        ref = Uri.decode(ref);
                    }
                    if (!ref.startsWith("http://") && !ref.startsWith("https://")) {
                        ref = "https://" + ref;
                    }
                    bookUrl = ref;
                }
            }

            DownloadIndex downloadIndex = downloadManager.getDownloadIndex();
            boolean hasOtherDownloads = false;
            if (bookUrl != null && !bookUrl.isEmpty()) {
                try (DownloadCursor cursor = downloadIndex.getDownloads()) {
                    while (cursor.moveToNext()) {
                        Download d = cursor.getDownload();
                        if (d.state == Download.STATE_REMOVING) continue;
                        Uri u = d.request.uri;
                        if (u != null) {
                            String ref = u.getQueryParameter("__ref");
                            if (ref != null) {
                                if (ref.contains("%2F") || ref.contains("%3A")) {
                                    ref = Uri.decode(ref);
                                }
                                if (!ref.startsWith("http://") && !ref.startsWith("https://")) {
                                    ref = "https://" + ref;
                                }
                                if (ref.contains(bookUrl) || bookUrl.contains(ref)) {
                                    hasOtherDownloads = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("DL", "Error checking remaining downloads", e);
                }

                if (!hasOtherDownloads) {
                    BooksDBModel dbModel = new BooksDBModel(downloadService);
                    if (book != null) {
                        dbModel.removeSaved(book);
                    } else {
                        dbModel.removeSavedByUrl(bookUrl);
                    }
                    dbModel.closeDB();

                    AudioListDBModel audioListDBModel = new AudioListDBModel(downloadService);
                    audioListDBModel.remove(bookUrl);
                    audioListDBModel.closeDB();
                }
            }
            downloadService.sendBroadcast(new Intent(Broadcast_UPDATE_ADAPTER));
        }
    }
}
