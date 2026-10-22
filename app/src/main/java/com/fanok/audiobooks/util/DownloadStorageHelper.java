package com.fanok.audiobooks.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadService;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.broadcasts.DownloadActionReceiver;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.service.ExoDownloadService;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadStorageHelper {

    public static final String PREF_DOWNLOAD_PATH = "pref_download_path";
    public static final String PREF_DOWNLOAD_URI = "pref_download_uri";

    /** One worker serialises library scans and deletions so their read-modify-write passes never interleave. */
    private static final ExecutorService STORAGE = Executors.newSingleThreadExecutor();

    public static boolean openFolderPicker(@NonNull ActivityResultLauncher<Intent> launcher) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            launcher.launch(intent);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Intent simpleIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                launcher.launch(simpleIntent);
                return true;
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        }
    }

    public static void saveDownloadPath(@NonNull Context context, @NonNull String path) {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            pref.edit()
                .remove(PREF_DOWNLOAD_URI)
                .putString(PREF_DOWNLOAD_PATH, path)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class StorageOption {
        public final String title;
        public final String path;
        public StorageOption(String title, String path) {
            this.title = title;
            this.path = path;
        }
    }

    public static java.util.List<StorageOption> getAvailableStorageOptions(@NonNull Context context) {
        java.util.List<StorageOption> list = new java.util.ArrayList<>();
        try {
            File pubDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (pubDownload != null) {
                File target = new File(pubDownload, "AudioBooks");
                list.add(new StorageOption("📂 Папка «Скачанное» в памяти телефона", target.getAbsolutePath()));
            }
        } catch (Exception ignored) {}

        try {
            File[] extDirs = context.getExternalFilesDirs(Environment.DIRECTORY_DOWNLOADS);
            if (extDirs != null) {
                for (int i = 0; i < extDirs.length; i++) {
                    File f = extDirs[i];
                    if (f != null) {
                        String p = f.getAbsolutePath();
                        if (i == 0 && list.isEmpty()) {
                            list.add(new StorageOption("📁 Папка приложения", p));
                        } else if (i > 0) {
                            list.add(new StorageOption("💳 Карта памяти SD Card", p));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    public static void saveDownloadUri(@NonNull Context context, @NonNull Intent data) {
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (takeFlags != 0) {
                context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String path = getPathFromUri(context, uri);
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            pref.edit()
                .putString(PREF_DOWNLOAD_URI, uri.toString())
                .putString(PREF_DOWNLOAD_PATH, path)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void resetToDefault(@NonNull Context context) {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            pref.edit()
                .remove(PREF_DOWNLOAD_URI)
                .remove(PREF_DOWNLOAD_PATH)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    public static String getDefaultPath(@NonNull Context context) {
        try {
            File extDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (extDownloads != null) {
                if (!extDownloads.exists()) {
                    extDownloads.mkdirs();
                }
                return extDownloads.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            File publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (publicDownloads != null) {
                return publicDownloads.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return context.getFilesDir().getAbsolutePath();
    }

    @NonNull
    public static String getDownloadPath(@NonNull Context context) {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            String savedPath = pref.getString(PREF_DOWNLOAD_PATH, "");
            if (savedPath != null && !savedPath.isEmpty()) {
                File dir = new File(savedPath);
                if (!dir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }
                return savedPath;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return getDefaultPath(context);
    }

    public static boolean canCreateFileInDir(@NonNull File dir) {
        try {
            File tempFile = new File(dir, ".test_write_" + System.currentTimeMillis());
            if (tempFile.createNewFile()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    /**
     * Opens the export destination of one chapter and puts the exact path or content:// URI being written into
     * {@code outFinalPathOrUriHolder[0]}. Throws when neither the folder path nor the selected SAF folder accepts
     * the file, instead of silently writing somewhere the settings do not name.
     */
    @NonNull
    public static OutputStream openOutputStreamForExport(@NonNull Context context,
                                                        @Nullable String bookUrl,
                                                        @Nullable String bookName,
                                                        @Nullable String artistName,
                                                        @Nullable String autorName,
                                                        @NonNull String fileName,
                                                        @NonNull String mimeType,
                                                        @NonNull String[] outFinalPathOrUriHolder) throws IOException {
        File targetDir = getBookDownloadDir(context, bookUrl, bookName, artistName, autorName);
        File targetFile = new File(targetDir, fileName);

        // 1. Попытка создания через стандартный FileOutputStream (если есть права доступа к ФС)
        try {
            if (!targetDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                targetDir.mkdirs();
            }
            OutputStream os = new java.io.FileOutputStream(targetFile);
            if (outFinalPathOrUriHolder != null && outFinalPathOrUriHolder.length > 0) {
                outFinalPathOrUriHolder[0] = targetFile.getAbsolutePath();
            }
            return os;
        } catch (Exception e) {
            android.util.Log.w("DownloadStorageHelper", "Standard FileOutputStream failed, trying SAF", e);
        }

        // 2. Попытка записи через SAF DocumentFile (если выбрана пользовательская папка через ACTION_OPEN_DOCUMENT_TREE)
        try {
            androidx.documentfile.provider.DocumentFile rootDoc = getRootDocumentFile(context);
            if (rootDoc != null) {
                String sourceFolder = getSourceName(bookUrl);
                String bookFolder = sanitizeFileName(bookName);
                String voiceFolder = voiceFolder(artistName, autorName);

                androidx.documentfile.provider.DocumentFile sourceDoc = findOrCreateSubdir(rootDoc, sourceFolder);
                if (sourceDoc != null) {
                    androidx.documentfile.provider.DocumentFile bookDoc = findOrCreateSubdir(sourceDoc, bookFolder);
                    if (bookDoc != null) {
                        androidx.documentfile.provider.DocumentFile voiceDoc = findOrCreateSubdir(bookDoc, voiceFolder);
                        if (voiceDoc != null) {
                            androidx.documentfile.provider.DocumentFile existingFile = voiceDoc.findFile(fileName);
                            if (existingFile != null && existingFile.exists()) {
                                existingFile.delete();
                            }
                            androidx.documentfile.provider.DocumentFile newFile = voiceDoc.createFile(mimeType, fileName);
                            if (newFile != null) {
                                OutputStream os = context.getContentResolver().openOutputStream(newFile.getUri());
                                if (os != null) {
                                    if (outFinalPathOrUriHolder != null && outFinalPathOrUriHolder.length > 0) {
                                        outFinalPathOrUriHolder[0] = newFile.getUri().toString();
                                    }
                                    return os;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("DownloadStorageHelper", "SAF DocumentFile output stream failed", e);
        }
        throw new IOException("Download folder does not accept " + fileName);
    }

    private static androidx.documentfile.provider.DocumentFile findOrCreateSubdir(androidx.documentfile.provider.DocumentFile parent, String name) {
        if (parent == null || name == null || name.isEmpty()) return null;
        androidx.documentfile.provider.DocumentFile child = parent.findFile(name);
        if (child != null && child.isDirectory()) {
            return child;
        }
        return parent.createDirectory(name);
    }

    @Nullable
    public static String getSavedUriString(@NonNull Context context) {
        try {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            return pref.getString(PREF_DOWNLOAD_URI, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @NonNull
    public static String getPathFromUri(@NonNull Context context, @Nullable Uri uri) {
        if (uri == null) return getDefaultPath(context);

        try {
            String docId = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && DocumentsContract.isTreeUri(uri)) {
                docId = DocumentsContract.getTreeDocumentId(uri);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
                docId = DocumentsContract.getDocumentId(uri);
            }

            if (docId != null) {
                String[] split = docId.split(":");
                String type = split[0];
                String path = split.length > 1 ? split[1] : "";

                if ("primary".equalsIgnoreCase(type)) {
                    File extDir = Environment.getExternalStorageDirectory();
                    return path.isEmpty() ? extDir.getAbsolutePath() : extDir.getAbsolutePath() + "/" + path;
                } else {
                    return path.isEmpty() ? "/storage/" + type : "/storage/" + type + "/" + path;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            String path = uri.getPath();
            if (path != null) {
                if (path.contains(":")) {
                    String[] split = path.split(":");
                    String volume = split[0].substring(path.lastIndexOf('/') + 1);
                    String subPath = split.length > 1 ? split[1] : "";
                    if ("primary".equalsIgnoreCase(volume)) {
                        return Environment.getExternalStorageDirectory() + "/" + subPath;
                    }
                    return "/storage/" + volume + "/" + subPath;
                }
                return path;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uri.toString();
    }

    @NonNull
    public static String getSourceName(@Nullable String bookUrl) {
        if (bookUrl == null || bookUrl.isEmpty()) return "Audiobooks";
        if (bookUrl.contains("akniga.org")) return "Akniga";
        if (bookUrl.contains("knigavuhe.org")) return "KnigaVUhe";
        if (bookUrl.contains("abook.org")) return "Abook";
        if (bookUrl.contains("izib.uk") || bookUrl.contains("izibuc")) return "Izibuk";
        if (bookUrl.contains("abmp3.org") || bookUrl.contains("audiobook-mp3")) return "AudioBookMP3";
        if (bookUrl.contains("baza-knig.ru")) return "BazaKnig";
        if (bookUrl.contains("knigoblud.com")) return "Knigoblud";
        if (bookUrl.contains("audioboo.ru")) return "Audioboo";
        if (bookUrl.contains("otrub.ru")) return "Otrub";
        if (bookUrl.contains("poleknig.com")) return "Poleknig";
        return "Audiobooks";
    }

    @NonNull
    public static String sanitizeFileName(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return "Unknown";
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "Unknown" : sanitized;
    }

    @NonNull
    public static File getBookDownloadDir(@NonNull Context context, @Nullable String bookUrl, @Nullable String bookName, @Nullable String artistName, @Nullable String autorName) {
        return new File(getDownloadPath(context), exportRelativeDir(bookUrl, bookName, artistName, autorName));
    }

    /** "source/book/reader": where below the download root the exporter writes a book's chapters. */
    @NonNull
    static String exportRelativeDir(@Nullable String bookUrl, @Nullable String bookName,
                                    @Nullable String artistName, @Nullable String autorName) {
        return getSourceName(bookUrl) + "/" + sanitizeFileName(bookName) + "/" + voiceFolder(artistName, autorName);
    }

    @NonNull
    private static String voiceFolder(@Nullable String artistName, @Nullable String autorName) {
        if (artistName != null && !artistName.trim().isEmpty()) return sanitizeFileName(artistName);
        if (autorName != null && !autorName.trim().isEmpty()) return sanitizeFileName(autorName);
        return "DefaultVoice";
    }

    @Nullable
    public static androidx.documentfile.provider.DocumentFile getRootDocumentFile(@NonNull Context context) {
        try {
            String uriStr = getSavedUriString(context);
            if (uriStr != null && !uriStr.isEmpty()) {
                Uri uri = Uri.parse(uriStr);
                androidx.documentfile.provider.DocumentFile docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri);
                if (docFile != null && docFile.exists()) {
                    return docFile;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    public static String findLocalCoverForBook(@NonNull Context context, @Nullable BookPOJO book) {
        if (book == null || book.getUrl() == null || book.getUrl().isEmpty()) return null;
        try {
            String photo = book.getPhoto();
            if (photo != null && !photo.isEmpty()) {
                if (photo.startsWith("content://")) {
                    androidx.documentfile.provider.DocumentFile df =
                            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, android.net.Uri.parse(photo));
                    if (df != null && df.exists()) return photo;
                } else if (photo.startsWith("/") || photo.startsWith("file://")) {
                    File f = new File(photo.replace("file://", ""));
                    if (f.exists()) return photo;
                }
            }

            File dir = getBookDownloadDir(context, book.getUrl(), book.getName(), book.getArtist(), book.getAutor());
            if (dir != null && dir.exists()) {
                String cover = findCoverImage(dir);
                if (!cover.isEmpty()) return cover;
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && isImageFileName(f.getName())) {
                            return f.getAbsolutePath();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isImageFileName(@Nullable String name) {
        if (name == null) return false;
        String lname = name.toLowerCase();
        return lname.endsWith(".jpg") || lname.endsWith(".jpeg") || lname.endsWith(".png")
                || lname.endsWith(".webp") || lname.endsWith(".bmp") || lname.endsWith(".gif");
    }

    private static boolean isAudioFileName(@Nullable String name) {
        if (name == null) return false;
        String lname = name.toLowerCase();
        return lname.endsWith(".mp3") || lname.endsWith(".m4a") || lname.endsWith(".m4b")
                || lname.endsWith(".aac") || lname.endsWith(".ts") || lname.endsWith(".flac")
                || lname.endsWith(".ogg") || lname.endsWith(".wav") || lname.endsWith(".wma")
                || lname.endsWith(".mp4");
    }

    public static void scanAndImportDownloadedBooks(@NonNull Context context) {
        scanAndImportDownloadedBooks(context, null);
    }

    public static void scanAndImportDownloadedBooks(@NonNull Context context, @Nullable Runnable onDone) {
        final Context app = context.getApplicationContext();
        STORAGE.execute(() -> {
            try {
                BooksDBModel booksDBModel = new BooksDBModel(app);
                AudioListDBModel audioListDBModel = new AudioListDBModel(app);

                String downloadPath = getDownloadPath(app);
                File baseDir = new File(downloadPath);
                if (baseDir.exists() && baseDir.isDirectory() && baseDir.canRead()) {
                    scanDirRecursive(app, booksDBModel, audioListDBModel, baseDir,
                            exportedLocations(app, booksDBModel, audioListDBModel, false));
                } else {
                    androidx.documentfile.provider.DocumentFile rootDoc = getRootDocumentFile(app);
                    if (rootDoc != null) {
                        scanDocDirRecursive(app, booksDBModel, audioListDBModel, rootDoc, "",
                                exportedLocations(app, booksDBModel, audioListDBModel, true));
                    }
                }

                Intent intent = new Intent(BookActivity.Broadcast_UPDATE_ADAPTER);
                app.sendBroadcast(intent);
            } catch (Throwable t) {
                android.util.Log.e("ScanHelper", "Error scanning download folder", t);
            } finally {
                if (onDone != null) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(onDone);
                }
            }
        });
    }

    /**
     * What the scan must not import as separate books: the copies recorded for online books (local_uri, or a local
     * url_audio written before local_uri existed) and the export folder of every saved online book, as absolute
     * paths or, for a SAF scan, as "source/book/reader" below the tree.
     */
    @NonNull
    private static Set<String> exportedLocations(@NonNull Context context, @NonNull BooksDBModel booksDBModel,
                                                 @NonNull AudioListDBModel audioListDBModel, boolean saf) {
        Set<String> exported = new HashSet<>();
        for (BooksAudioEntity row : audioListDBModel.getDatabase().booksAudioDao().getAll()) {
            if (DeletionPlanner.isImportedBook(row.urlBook)) continue;
            if (row.localUri != null) exported.add(row.localUri);
            if (row.urlAudio != null && !DeletionPlanner.isRemote(row.urlAudio)) exported.add(row.urlAudio);
        }
        java.util.ArrayList<BookPOJO> saved = booksDBModel.getAllSaved();
        if (saved != null) {
            for (BookPOJO book : saved) {
                if (book.getUrl() == null || DeletionPlanner.isImportedBook(book.getUrl())) continue;
                exported.add(saf
                        ? exportRelativeDir(book.getUrl(), book.getName(), book.getArtist(), book.getAutor())
                        : getBookDownloadDir(context, book.getUrl(), book.getName(), book.getArtist(), book.getAutor()).getAbsolutePath());
            }
        }
        return exported;
    }

    private static void scanDocDirRecursive(@NonNull Context context,
                                             @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                             @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                             @NonNull androidx.documentfile.provider.DocumentFile dir,
                                             @NonNull String relativePath, @NonNull Set<String> exported) {
        if (dir.getName() != null && dir.getName().startsWith(".")) return;

        androidx.documentfile.provider.DocumentFile[] files = dir.listFiles();
        if (files == null) return;

        java.util.List<androidx.documentfile.provider.DocumentFile> audioDocFiles = new java.util.ArrayList<>();
        for (androidx.documentfile.provider.DocumentFile f : files) {
            if (f.isFile() && isAudioFileName(f.getName()) && !exported.contains(f.getUri().toString())) {
                audioDocFiles.add(f);
            }
        }

        if (!audioDocFiles.isEmpty() && !exported.contains(relativePath)) {
            String dirName = dir.getName() != null ? dir.getName() : "Voice";
            String voiceName = "Основной";
            String bookName = dirName;
            String sourceName = "Локально";

            androidx.documentfile.provider.DocumentFile parent = dir.getParentFile();
            if (parent != null && parent.getName() != null && !parent.getName().equalsIgnoreCase("Download") && !parent.getName().equalsIgnoreCase("0")) {
                if (dirName.equalsIgnoreCase("DefaultVoice") || dirName.equalsIgnoreCase("Voice") || dirName.toLowerCase().startsWith("озвуч")) {
                    voiceName = dirName;
                    bookName = parent.getName();
                    androidx.documentfile.provider.DocumentFile grandParent = parent.getParentFile();
                    if (grandParent != null && grandParent.getName() != null && !grandParent.getName().equalsIgnoreCase("Download")) {
                        sourceName = grandParent.getName();
                    }
                } else {
                    sourceName = parent.getName();
                }
            }

            processDocBookDir(context, booksDBModel, audioListDBModel, sourceName, bookName, voiceName, dir, audioDocFiles);
        }

        for (androidx.documentfile.provider.DocumentFile f : files) {
            if (f.isDirectory()) {
                scanDocDirRecursive(context, booksDBModel, audioListDBModel, f,
                        relativePath.isEmpty() ? f.getName() : relativePath + "/" + f.getName(), exported);
            }
        }
    }

    private static String findCoverImage(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files == null) return "";
        File candidate = null;
        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.equals("cover.jpg") || name.equals("cover.png") || name.equals("cover.jpeg") || name.equals("folder.jpg") || name.equals("folder.png")) {
                    return f.getAbsolutePath();
                }
                if (candidate == null && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
                    candidate = f;
                }
            }
        }
        return candidate != null ? candidate.getAbsolutePath() : "";
    }

    private static String findDocCoverImage(@NonNull androidx.documentfile.provider.DocumentFile dir) {
        androidx.documentfile.provider.DocumentFile[] files = dir.listFiles();
        if (files == null) return "";
        androidx.documentfile.provider.DocumentFile candidate = null;
        for (androidx.documentfile.provider.DocumentFile f : files) {
            if (f.isFile() && f.getName() != null) {
                String name = f.getName().toLowerCase();
                if (name.equals("cover.jpg") || name.equals("cover.png") || name.equals("cover.jpeg") || name.equals("folder.jpg") || name.equals("folder.png")) {
                    return f.getUri() != null ? f.getUri().toString() : "";
                }
                if (candidate == null && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
                    candidate = f;
                }
            }
        }
        return (candidate != null && candidate.getUri() != null) ? candidate.getUri().toString() : "";
    }

    private static void processDocBookDir(@NonNull Context context,
                                          @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                          @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                          @NonNull String sourceName, @NonNull String bookName, @NonNull String voiceName,
                                          @NonNull androidx.documentfile.provider.DocumentFile dir,
                                          @NonNull java.util.List<androidx.documentfile.provider.DocumentFile> audioDocFiles) {
        List<String[]> tracks = new ArrayList<>();
        for (androidx.documentfile.provider.DocumentFile audioDoc : audioDocFiles) {
            tracks.add(new String[]{audioDoc.getName() != null ? audioDoc.getName() : "track",
                    audioDoc.getUri() != null ? audioDoc.getUri().toString() : ""});
        }
        importPlaylist(booksDBModel, audioListDBModel, sourceName, bookName, voiceName, findDocCoverImage(dir), tracks);
    }

    private static void scanDirRecursive(@NonNull Context context,
                                         @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                         @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                         @NonNull File dir, @NonNull Set<String> exported) {
        if (dir.getName().startsWith(".")) return;

        File[] audioFiles = dir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return isAudioFileName(name) && !exported.contains(new File(d, name).getAbsolutePath());
            }
        });

        if (audioFiles != null && audioFiles.length > 0 && !exported.contains(dir.getAbsolutePath())) {
            String dirName = dir.getName();
            String voiceName = "Основной";
            String bookName = dirName;
            String sourceName = "Локально";

            File parent = dir.getParentFile();
            if (parent != null && !parent.getName().equalsIgnoreCase("Download") && !parent.getName().equalsIgnoreCase("0") && !parent.getName().equalsIgnoreCase("sdcard")) {
                if (dirName.equalsIgnoreCase("DefaultVoice") || dirName.equalsIgnoreCase("Voice") || dirName.toLowerCase().startsWith("озвуч")) {
                    voiceName = dirName;
                    bookName = parent.getName();
                    File grandParent = parent.getParentFile();
                    if (grandParent != null && !grandParent.getName().equalsIgnoreCase("Download") && !grandParent.getName().equalsIgnoreCase("0")) {
                        sourceName = grandParent.getName();
                    }
                } else {
                    sourceName = parent.getName();
                }
            }

            processBookDir(context, booksDBModel, audioListDBModel, sourceName, bookName, voiceName, dir, audioFiles);
        }

        File[] subDirs = dir.listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname != null && pathname.isDirectory();
            }
        });
        if (subDirs != null) {
            for (File subDir : subDirs) {
                scanDirRecursive(context, booksDBModel, audioListDBModel, subDir, exported);
            }
        }
    }

    private static void processBookDir(@NonNull Context context,
                                       @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                       @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                       @NonNull String sourceName, @NonNull String bookName, @NonNull String voiceName,
                                       @NonNull File dir, @NonNull File[] audioFiles) {
        List<String[]> tracks = new ArrayList<>();
        for (File audioFile : audioFiles) {
            tracks.add(new String[]{audioFile.getName(), audioFile.getAbsolutePath()});
        }
        importPlaylist(booksDBModel, audioListDBModel, sourceName, bookName, voiceName, findCoverImage(dir), tracks);
    }

    /**
     * Writes one scanned folder as an imported book in one transaction, tracks ({name, uri}) in natural order.
     * The identity is source, title and reader folder, so two readers of one title stay two books.
     */
    private static void importPlaylist(@NonNull BooksDBModel booksDBModel, @NonNull AudioListDBModel audioListDBModel,
                                       @NonNull String sourceName, @NonNull String bookName, @NonNull String voiceName,
                                       @NonNull String photoUrl, @NonNull List<String[]> tracks) {
        String legacyUrl = "https://" + sourceName.toLowerCase() + ".org/imported/" + sanitizeFileName(bookName);
        String fakeUrl = legacyUrl + "/" + sanitizeFileName(voiceName);
        Collections.sort(tracks, (t1, t2) -> StorageNames.naturalCompare(t1[0], t2[0]));

        audioListDBModel.getDatabase().runInTransaction(() -> {
            // Imports made before the reader was part of the identity shared one URL per title.
            booksDBModel.removeSavedByUrl(legacyUrl);
            audioListDBModel.remove(legacyUrl);

            BookPOJO book = booksDBModel.getSaved(fakeUrl);
            if (book == null) {
                book = new BookPOJO();
                book.setUrl(fakeUrl);
                book.setName(bookName.replace("_", " "));
                book.setArtist(voiceName.replace("_", " "));
                book.setAutor("Импортировано");
                book.setPhoto(photoUrl);

                booksDBModel.addSaved(book);
            } else if ((book.getPhoto() == null || book.getPhoto().isEmpty()) && !photoUrl.isEmpty()) {
                book.setPhoto(photoUrl);
                booksDBModel.addSaved(book);
            }

            audioListDBModel.remove(fakeUrl);
            for (String[] track : tracks) {
                com.fanok.audiobooks.pojo.AudioListPOJO audioPOJO = new com.fanok.audiobooks.pojo.AudioListPOJO();
                audioPOJO.setBookUrl(fakeUrl);
                audioPOJO.setBookName(bookName.replace("_", " "));
                String nameNoExt = track[0];
                int dotPos = nameNoExt.lastIndexOf('.');
                if (dotPos > 0) nameNoExt = nameNoExt.substring(0, dotPos);
                audioPOJO.setAudioName(nameNoExt);
                audioPOJO.setAudioUrl(track[1]);
                audioListDBModel.add(audioPOJO);
            }
        });
    }

    /** Media3 download id of a remote chapter URL: scheme, authority and path without the query. */
    @NonNull
    public static String downloadIdOf(@NonNull String remoteUrl) {
        Uri uri = Uri.parse(remoteUrl);
        return new Uri.Builder()
                .scheme(uri.getScheme())
                .authority(uri.getAuthority())
                .path(uri.getPath())
                .build()
                .toString();
    }

    /** "NNN - title.ext" for the chapter whose remote URL has {@code downloadId}; NNN is its position in the book. */
    @NonNull
    public static String exportFileNameFor(@NonNull Context context, @NonNull String bookUrl,
                                           @NonNull String downloadId, @Nullable String title, @NonNull String ext) {
        int index = 0;
        List<BooksAudioEntity> rows = new AudioListDBModel(context).getRows(bookUrl);
        for (int i = 0; i < rows.size(); i++) {
            String url = rows.get(i).urlAudio;
            if (DeletionPlanner.isRemote(url) && downloadId.equals(downloadIdOf(url))) {
                index = i + 1;
                break;
            }
        }
        return StorageNames.exportFileName(index, title, ext);
    }

    /** Whether the exact file named by a recorded URI or path opens; directories are never searched. */
    public static boolean exists(@NonNull Context context, @NonNull String uriOrPath) {
        try {
            if (uriOrPath.startsWith("content://")) {
                DocumentFile doc = DocumentFile.fromSingleUri(context, Uri.parse(uriOrPath));
                return doc != null && doc.exists();
            }
            String path = uriOrPath.startsWith("file://") ? Uri.parse(uriOrPath).getPath() : uriOrPath;
            return path != null && new File(path).isFile();
        } catch (Exception e) {
            return false;
        }
    }

    /** The row's own playable local copy: its recorded export, else a local url_audio (imported books); or null. */
    @Nullable
    public static String localCopyOf(@NonNull Context context, @NonNull BooksAudioEntity row) {
        if (row.localUri != null && !row.localUri.isEmpty() && exists(context, row.localUri)) {
            return row.localUri;
        }
        if (row.urlAudio != null && !row.urlAudio.isEmpty() && !DeletionPlanner.isRemote(row.urlAudio)
                && exists(context, row.urlAudio)) {
            return row.urlAudio;
        }
        return null;
    }

    /** Receives, on the main thread, how many of the chosen chapters were fully deleted. */
    public interface DeleteCallback {
        void onDeleted(int deleted, int total);
    }

    /**
     * Deletes the downloaded copies of the chosen chapters of one book on the storage worker. A chapter is chosen
     * when its url_audio or its recorded local_uri is in {@code chapterUrls}. Online chapters stay in the list;
     * chapters of imported books leave the library while their files stay. When no chapter stays downloaded the
     * book leaves the Saved list.
     */
    public static void deleteChapters(@NonNull Context context, @NonNull String bookUrl,
                                      @NonNull Collection<String> chapterUrls, @Nullable DeleteCallback callback) {
        final Context app = context.getApplicationContext();
        final Set<String> chosenUrls = new HashSet<>(chapterUrls);
        STORAGE.execute(() -> {
            int deleted = 0;
            int total = 0;
            try {
                AudioListDBModel model = new AudioListDBModel(app);
                List<BooksAudioEntity> chosen = new ArrayList<>();
                for (BooksAudioEntity row : model.getRows(bookUrl)) {
                    if (chosenUrls.contains(row.urlAudio) || (row.localUri != null && chosenUrls.contains(row.localUri))) {
                        chosen.add(row);
                    }
                }
                total = chosen.size();
                Set<String> removedIds = new HashSet<>();
                deleted = applyPlan(app, model, chosen, true, removedIds);
                if (total > 0 && !anyDownloaded(app, model.getRows(bookUrl), removedIds)) {
                    model.remove(bookUrl);
                    new BooksDBModel(app).removeSavedByUrl(bookUrl);
                }
            } catch (Exception e) {
                android.util.Log.e("DownloadStorageHelper", "Error deleting chapters", e);
            }
            app.sendBroadcast(new Intent(BookActivity.Broadcast_UPDATE_ADAPTER));
            if (callback != null) {
                final int finalDeleted = deleted;
                final int finalTotal = total;
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onDeleted(finalDeleted, finalTotal));
            }
        });
    }

    /**
     * Removes books from the library on the storage worker: their Media3 downloads, the copies recorded in
     * local_uri and their rows. Files the app did not write (imported folders) are never touched.
     */
    public static void removeBooks(@NonNull Context context, @NonNull Collection<String> bookUrls) {
        final Context app = context.getApplicationContext();
        final List<String> urls = new ArrayList<>(bookUrls);
        STORAGE.execute(() -> {
            AudioListDBModel model = new AudioListDBModel(app);
            BooksDBModel books = new BooksDBModel(app);
            for (String url : urls) {
                try {
                    applyPlan(app, model, model.getRows(url), false, new HashSet<>());
                    books.removeSavedByUrl(url);
                    Intent cancel = new Intent(app, DownloadActionReceiver.class);
                    cancel.setAction("CANCEL");
                    cancel.putExtra("id", url);
                    app.sendBroadcast(cancel);
                } catch (Exception e) {
                    android.util.Log.e("DownloadStorageHelper", "Error removing book " + url, e);
                }
            }
            app.sendBroadcast(new Intent(BookActivity.Broadcast_UPDATE_ADAPTER));
        });
    }

    /**
     * Applies the deletion plan of {@code rows}: removes their Media3 downloads, deletes exactly their recorded
     * copies, then clears or removes only the rows whose copies are gone. Returns how many rows were fully handled.
     */
    private static int applyPlan(@NonNull Context context, @NonNull AudioListDBModel model,
                                 @NonNull List<BooksAudioEntity> rows, boolean keepOnlineRows,
                                 @NonNull Set<String> removedIds) {
        List<DeletionPlanner.Row> planRows = new ArrayList<>();
        for (BooksAudioEntity row : rows) {
            planRows.add(new DeletionPlanner.Row(row.urlBook, row.urlAudio, row.localUri));
        }
        List<String> roots = downloadRoots(context);
        DeletionPlanner.Plan plan = DeletionPlanner.plan(planRows, roots, keepOnlineRows, DownloadStorageHelper::downloadIdOf);

        DownloadIndex index = null;
        try {
            index = DownloadUtil.getDownloadManager().getDownloadIndex();
        } catch (Exception ignored) {
        }
        Set<String> failed = new HashSet<>();
        for (String id : plan.downloadIdsToRemove) {
            try {
                if (index == null || index.getDownload(id) != null) {
                    DownloadService.sendRemoveDownload(context, ExoDownloadService.class, id, false);
                }
                removedIds.add(id);
            } catch (Exception e) {
                failed.add(id);
                android.util.Log.w("DownloadStorageHelper", "Error removing download " + id, e);
            }
        }
        failed.addAll(deleteRecordedUris(context, plan.urisToDelete, roots));

        try {
            model.getDatabase().runInTransaction(() -> {
                for (DeletionPlanner.Row row : plan.rowsToRemove) {
                    if (isHandled(row, failed)) model.removeTrack(row.urlBook, row.urlAudio);
                }
                for (DeletionPlanner.Row row : plan.rowsToClearLocalUri) {
                    if (isHandled(row, failed)) {
                        model.getDatabase().booksAudioDao().updateLocalUri(row.urlBook, row.urlAudio, null);
                    }
                }
            });
        } catch (Exception e) {
            android.util.Log.e("DownloadStorageHelper", "Error updating deleted chapters", e);
            return 0;
        }
        int handled = 0;
        for (DeletionPlanner.Row row : planRows) {
            if (isHandled(row, failed)) handled++;
        }
        return handled;
    }

    private static boolean isHandled(@NonNull DeletionPlanner.Row row, @NonNull Set<String> failed) {
        return !failed.contains(row.localUri)
                && !(DeletionPlanner.isRemote(row.urlAudio) && failed.contains(downloadIdOf(row.urlAudio)));
    }

    /** True while a row still has a local copy or a completed Media3 download that is not being removed. */
    private static boolean anyDownloaded(@NonNull Context context, @NonNull List<BooksAudioEntity> rows,
                                         @NonNull Set<String> removedIds) {
        DownloadIndex index = null;
        try {
            index = DownloadUtil.getDownloadManager().getDownloadIndex();
        } catch (Exception ignored) {
        }
        for (BooksAudioEntity row : rows) {
            if (localCopyOf(context, row) != null) return true;
            if (index != null && DeletionPlanner.isRemote(row.urlAudio)) {
                String id = downloadIdOf(row.urlAudio);
                try {
                    Download download = index.getDownload(id);
                    if (!removedIds.contains(id) && download != null && download.state == Download.STATE_COMPLETED) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /** Roots the exporter writes below (and the selected SAF tree); folders are pruned only strictly inside them. */
    @NonNull
    private static List<String> downloadRoots(@NonNull Context context) {
        List<String> roots = new ArrayList<>();
        roots.add(getDownloadPath(context));
        String defaultPath = getDefaultPath(context);
        if (!roots.contains(defaultPath)) roots.add(defaultPath);
        String tree = getSavedUriString(context);
        if (tree != null && !tree.isEmpty()) roots.add(tree);
        return roots;
    }

    /**
     * Deletes exactly the given recorded copies (content:// through SAF, file:// and plain paths through File), then
     * prunes the folders above each one that are now empty, stopping below its root. Returns the URIs still present.
     */
    @NonNull
    public static Set<String> deleteRecordedUris(@NonNull Context context, @NonNull Collection<String> uris,
                                                 @NonNull Collection<String> roots) {
        Set<String> failed = new HashSet<>();
        for (String uri : uris) {
            try {
                if (!deleteRecordedUri(context, uri, roots)) failed.add(uri);
            } catch (Exception e) {
                failed.add(uri);
                android.util.Log.w("DownloadStorageHelper", "Error deleting " + uri, e);
            }
        }
        return failed;
    }

    private static boolean deleteRecordedUri(@NonNull Context context, @NonNull String uri,
                                             @NonNull Collection<String> roots) {
        if (uri.startsWith("content://")) {
            Uri docUri = Uri.parse(uri);
            List<String> folders = safFoldersAbove(context, docUri);
            DocumentFile doc = DocumentFile.fromSingleUri(context, docUri);
            boolean gone = doc == null || doc.delete() || !doc.exists();
            if (gone) pruneEmptySafFolders(context, docUri, folders);
            return gone;
        }
        File file = new File(uri.startsWith("file://") ? Uri.parse(uri).getPath() : uri);
        boolean gone = file.delete() || !file.exists();
        if (gone) pruneEmptyFolders(file.getParentFile(), DeletionPlanner.enclosingRoot(file.getAbsolutePath(), roots));
        return gone;
    }

    /** Deletes {@code dir} and then its parents while each is empty and strictly below {@code root}. */
    private static void pruneEmptyFolders(@Nullable File dir, @Nullable String root) {
        if (root == null) return;
        while (dir != null && dir.getAbsolutePath().startsWith(root + "/")) {
            String[] children = dir.list();
            if (children == null || children.length > 0 || !dir.delete()) return;
            dir = dir.getParentFile();
        }
    }

    /** Document ids of the folders between the tree root (excluded) and the document (excluded), nearest last. */
    @NonNull
    private static List<String> safFoldersAbove(@NonNull Context context, @NonNull Uri docUri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !DocumentsContract.isTreeUri(docUri)) {
            return Collections.emptyList();
        }
        try {
            DocumentsContract.Path path = DocumentsContract.findDocumentPath(context.getContentResolver(), docUri);
            List<String> ids = path != null ? path.getPath() : null;
            if (ids == null || ids.size() < 3) return Collections.emptyList();
            return new ArrayList<>(ids.subList(1, ids.size() - 1));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** SAF deletes folders recursively, so a folder is deleted only after a query shows it has no children. */
    private static void pruneEmptySafFolders(@NonNull Context context, @NonNull Uri docUri, @NonNull List<String> folders) {
        android.content.ContentResolver resolver = context.getContentResolver();
        for (int k = folders.size() - 1; k >= 0; k--) {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(docUri, folders.get(k));
            int count;
            try (android.database.Cursor cursor = resolver.query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
                count = cursor != null ? cursor.getCount() : -1;
            } catch (Exception e) {
                return;
            }
            try {
                if (count != 0 || !DocumentsContract.deleteDocument(resolver,
                        DocumentsContract.buildDocumentUriUsingTree(docUri, folders.get(k)))) {
                    return;
                }
            } catch (Exception e) {
                return;
            }
        }
    }
}
