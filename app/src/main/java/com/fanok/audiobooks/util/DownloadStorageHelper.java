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
import com.fanok.audiobooks.pojo.BookPOJO;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class DownloadStorageHelper {

    public static final String PREF_DOWNLOAD_PATH = "pref_download_path";
    public static final String PREF_DOWNLOAD_URI = "pref_download_uri";

    private static volatile long lastScanStartMs = 0;

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
        if (outFinalPathOrUriHolder != null && outFinalPathOrUriHolder.length > 0) {
            outFinalPathOrUriHolder[0] = targetFile.getAbsolutePath();
        }

        // 1. Попытка создания через стандартный FileOutputStream (если есть права доступа к ФС)
        try {
            if (!targetDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                targetDir.mkdirs();
            }
            return new java.io.FileOutputStream(targetFile);
        } catch (Exception e) {
            android.util.Log.w("DownloadStorageHelper", "Standard FileOutputStream failed, trying SAF", e);
        }

        // 2. Попытка записи через SAF DocumentFile (если выбрана пользовательская папка через ACTION_OPEN_DOCUMENT_TREE)
        try {
            androidx.documentfile.provider.DocumentFile rootDoc = getRootDocumentFile(context);
            if (rootDoc != null) {
                String sourceFolder = getSourceName(bookUrl);
                String bookFolder = sanitizeFileName(bookName);
                String voiceFolder = (artistName != null && !artistName.trim().isEmpty())
                        ? sanitizeFileName(artistName)
                        : ((autorName != null && !autorName.trim().isEmpty()) ? sanitizeFileName(autorName) : "DefaultVoice");

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
            android.util.Log.w("DownloadStorageHelper", "SAF DocumentFile output stream failed, trying fallback path", e);
        }

        // 3. Резервный фолбэк на внутреннюю папку загрузок приложения (гарантированные права)
        File defaultBaseDir = new File(getDefaultPath(context));
        String sourceFolder = getSourceName(bookUrl);
        String bookFolder = sanitizeFileName(bookName);
        String voiceFolder = (artistName != null && !artistName.trim().isEmpty())
                ? sanitizeFileName(artistName)
                : ((autorName != null && !autorName.trim().isEmpty()) ? sanitizeFileName(autorName) : "DefaultVoice");
        File defaultTargetDir = new File(defaultBaseDir, sourceFolder + File.separator + bookFolder + File.separator + voiceFolder);
        if (!defaultTargetDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            defaultTargetDir.mkdirs();
        }
        File defaultTargetFile = new File(defaultTargetDir, fileName);
        if (outFinalPathOrUriHolder != null && outFinalPathOrUriHolder.length > 0) {
            outFinalPathOrUriHolder[0] = defaultTargetFile.getAbsolutePath();
        }
        return new java.io.FileOutputStream(defaultTargetFile);
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
        String basePath = getDownloadPath(context);
        String sourceFolder = getSourceName(bookUrl);
        String bookFolder = sanitizeFileName(bookName);
        String voiceFolder = (artistName != null && !artistName.trim().isEmpty())
                ? sanitizeFileName(artistName)
                : ((autorName != null && !autorName.trim().isEmpty()) ? sanitizeFileName(autorName) : "DefaultVoice");

        File targetDir = new File(basePath, sourceFolder + File.separator + bookFolder + File.separator + voiceFolder);
        return targetDir;
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

    @NonNull
    public static String deleteBookFiles(@NonNull Context context, @Nullable String bookUrl, @Nullable BookPOJO book) {
        StringBuilder log = new StringBuilder();
        log.append("--- НАЧАЛО УДАЛЕНИЯ ---\n");
        log.append("URL книги: ").append(bookUrl).append("\n");
        if (context == null || bookUrl == null || bookUrl.isEmpty()) {
            log.append("Ошибка: Context или bookUrl равен null/empty.\n");
            return log.toString();
        }

        try {
            com.fanok.audiobooks.model.AudioListDBModel audioListDBModel = new com.fanok.audiobooks.model.AudioListDBModel(context);
            java.util.ArrayList<com.fanok.audiobooks.pojo.AudioListPOJO> tracks = audioListDBModel.get(bookUrl);
            log.append("Найдено треков в БД: ").append(tracks != null ? tracks.size() : 0).append("\n");

            java.util.HashSet<File> parentDirs = new java.util.HashSet<>();

            if (tracks != null && !tracks.isEmpty()) {
                int delCount = 0;
                int errCount = 0;
                for (com.fanok.audiobooks.pojo.AudioListPOJO track : tracks) {
                    String url = track.getAudioUrl();
                    if (url != null && !url.isEmpty()) {
                        if (url.startsWith("content://")) {
                            try {
                                Uri uri = Uri.parse(url);
                                boolean res = false;
                                androidx.documentfile.provider.DocumentFile doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri);
                                if (doc != null && doc.exists()) {
                                    res = doc.delete();
                                }
                                if (!res) {
                                    res = android.provider.DocumentsContract.deleteDocument(context.getContentResolver(), uri);
                                }
                                if (res) delCount++; else errCount++;
                                log.append("SAF Трек ").append(uri.getLastPathSegment()).append(" удален: ").append(res).append("\n");
                            } catch (Exception e) {
                                errCount++;
                                log.append("SAF ошибка ").append(url).append(": ").append(e.getMessage()).append("\n");
                            }
                        } else {
                            try {
                                File f = new File(url);
                                if (f.exists()) {
                                    File parent = f.getParentFile();
                                    if (parent != null) parentDirs.add(parent);
                                    boolean res = f.delete();
                                    if (res) delCount++; else errCount++;
                                    log.append("Файл ").append(f.getName()).append(" удален: ").append(res).append("\n");
                                } else {
                                    log.append("Файл не найден на диске: ").append(f.getAbsolutePath()).append("\n");
                                }
                            } catch (Exception e) {
                                errCount++;
                                log.append("Ошибка файла ").append(url).append(": ").append(e.getMessage()).append("\n");
                            }
                        }
                    }
                }
                log.append("Итог по трекам: Успешно=").append(delCount).append(", Ошибок=").append(errCount).append("\n");
            }

            String sourceName = com.fanok.audiobooks.util.DownloadStorageHelper.getSourceName(bookUrl);
            String basePathRoot = getDownloadPath(context);
            File baseRootDir = new File(basePathRoot);

            for (File parent : parentDirs) {
                if (parent != null && parent.exists()) {
                    log.append("Удаляем родительскую папку: ").append(parent.getAbsolutePath()).append("\n");
                    deleteRecursive(parent);
                    log.append("Статус папки после удаления: существует=").append(parent.exists()).append("\n");

                    File current = parent.getParentFile();
                    while (current != null && current.exists()) {
                        String currentName = current.getName();
                        boolean isSourceFolder = currentName != null && sourceName != null
                                && currentName.equalsIgnoreCase(sourceName);
                        boolean isBaseRoot = baseRootDir != null && current.getAbsolutePath().equals(
                                baseRootDir.getAbsolutePath());
                        if (isSourceFolder || isBaseRoot) {
                            break;
                        }
                        File[] children = current.listFiles();
                        boolean isEmpty = children == null || children.length == 0;
                        if (!isEmpty) {
                            break;
                        }
                        log.append("Удаляем пустую папку: ").append(current.getAbsolutePath()).append("\n");
                        if (!current.delete()) {
                            break;
                        }
                        current = current.getParentFile();
                    }
                }
            }

            if (book == null) {
                com.fanok.audiobooks.model.BooksDBModel dbModel = new com.fanok.audiobooks.model.BooksDBModel(context);
                book = dbModel.getSomewhere(bookUrl);
                dbModel.closeDB();
            }

            if (book != null && book.getName() != null && !book.getName().trim().isEmpty()) {
                File expectedVoiceDir = getBookDownloadDir(context, bookUrl, book.getName(), book.getArtist(), book.getAutor());
                File expectedBookDir = (expectedVoiceDir != null) ? expectedVoiceDir.getParentFile() : null;
                if (expectedBookDir != null && expectedBookDir.exists()) {
                    log.append("Прямое удаление папки книги: ").append(expectedBookDir.getAbsolutePath()).append("\n");
                    deleteRecursive(expectedBookDir);
                    log.append("Папка книги удалена, существует=").append(expectedBookDir.exists()).append("\n");
                }
            }

            java.util.ArrayList<String> targetNames = new java.util.ArrayList<>();
            if (book != null && book.getName() != null && !book.getName().trim().isEmpty()) {
                targetNames.add(book.getName().trim());
                targetNames.add(sanitizeFileName(book.getName()));
            }
            int lastSlash = bookUrl.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < bookUrl.length() - 1) {
                String slug = bookUrl.substring(lastSlash + 1);
                targetNames.add(slug);
                targetNames.add(sanitizeFileName(slug));
            }

            log.append("Поиск директории книги по именам: ").append(targetNames.toString()).append("\n");

            androidx.documentfile.provider.DocumentFile rootDoc = getRootDocumentFile(context);
            if (rootDoc != null) {
                log.append("Сканируем SAF Root: ").append(rootDoc.getName()).append("\n");
                for (String targetName : targetNames) {
                    if (targetName != null && !targetName.isEmpty()) {
                        deleteDocMatching(rootDoc, targetName, log);
                    }
                }
            } else {
                log.append("SAF Root не настроен.\n");
            }

            String basePath = getDownloadPath(context);
            File baseDir = new File(basePath);
            if (baseDir.exists()) {
                log.append("Сканируем локальный путь: ").append(baseDir.getAbsolutePath()).append("\n");
                for (String targetName : targetNames) {
                    if (targetName != null && !targetName.isEmpty()) {
                        deleteFileMatching(baseDir, targetName, log);
                    }
                }
            } else {
                log.append("Локальный путь загрузки не существует: ").append(basePath).append("\n");
            }
        } catch (Exception e) {
            log.append("Критическое исключение: ").append(e.toString()).append("\n");
        }
        log.append("--- КОНЕЦ УДАЛЕНИЯ ---");
        return log.toString();
    }

    @NonNull
    public static String deleteBookFiles(@NonNull Context context, @Nullable String bookUrl) {
        return deleteBookFiles(context, bookUrl, null);
    }

    private static String normalizeForComparison(@Nullable String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9а-яё]", "");
    }

    private static void deleteDocMatching(@NonNull androidx.documentfile.provider.DocumentFile dir, @NonNull String targetName, @NonNull StringBuilder log) {
        if (dir.getName() != null && dir.getName().startsWith(".")) return;
        if (targetName == null || targetName.trim().isEmpty()) return;

        String tName = normalizeForComparison(targetName);
        String dName = dir.getName() != null ? normalizeForComparison(dir.getName()) : "";

        if (!tName.isEmpty() && dName.equals(tName)) {
            log.append("SAF Найдена совпавшая директория: ").append(dir.getName()).append(" -> Удаляем SAF дерево...\n");
            deleteDocRecursive(dir);
            return;
        }

        androidx.documentfile.provider.DocumentFile[] files = dir.listFiles();
        if (files != null) {
            for (androidx.documentfile.provider.DocumentFile file : files) {
                String fName = file.getName() != null ? normalizeForComparison(file.getName()) : "";
                if (!tName.isEmpty() && fName.equals(tName)) {
                    log.append("SAF Найдена совпавшая дочерняя директория: ").append(file.getName()).append(" -> Удаляем...\n");
                    deleteDocRecursive(file);
                } else if (file.isDirectory()) {
                    deleteDocMatching(file, targetName, log);
                }
            }
        }
    }

    private static void deleteDocRecursive(@NonNull androidx.documentfile.provider.DocumentFile doc) {
        if (doc.isDirectory()) {
            androidx.documentfile.provider.DocumentFile[] children = doc.listFiles();
            if (children != null) {
                for (androidx.documentfile.provider.DocumentFile child : children) {
                    deleteDocRecursive(child);
                }
            }
        }
        doc.delete();
    }

    private static void deleteFileMatching(@NonNull File dir, @NonNull String targetName, @NonNull StringBuilder log) {
        if (dir.getName().startsWith(".")) return;
        if (targetName == null || targetName.trim().isEmpty()) return;

        String tName = normalizeForComparison(targetName);
        String dName = normalizeForComparison(dir.getName());

        if (!tName.isEmpty() && dName.equals(tName)) {
            log.append("File Найдена директория: ").append(dir.getAbsolutePath()).append(" -> Удаляем папку...\n");
            deleteRecursive(dir);
            log.append("Папка удалена, существует=").append(dir.exists()).append("\n");
            return;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String fName = normalizeForComparison(file.getName());
                if (!tName.isEmpty() && fName.equals(tName)) {
                    log.append("File Найдена дочерняя папка: ").append(file.getAbsolutePath()).append(" -> Удаляем...\n");
                    deleteRecursive(file);
                    log.append("Удаление завершено, существует=").append(file.exists()).append("\n");
                } else if (file.isDirectory()) {
                    deleteFileMatching(file, targetName, log);
                }
            }
        }
    }

    public static void deleteRecursive(@Nullable File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
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
        new Thread(() -> {
            try {
                com.fanok.audiobooks.model.BooksDBModel booksDBModel = new com.fanok.audiobooks.model.BooksDBModel(context);
                com.fanok.audiobooks.model.AudioListDBModel audioListDBModel = new com.fanok.audiobooks.model.AudioListDBModel(context);

                String downloadPath = getDownloadPath(context);
                File baseDir = new File(downloadPath);
                if (baseDir.exists() && baseDir.isDirectory() && baseDir.canRead()) {
                    scanDirRecursive(context, booksDBModel, audioListDBModel, baseDir);
                } else {
                    androidx.documentfile.provider.DocumentFile rootDoc = getRootDocumentFile(context);
                    if (rootDoc != null) {
                        scanDocDirRecursive(context, booksDBModel, audioListDBModel, rootDoc);
                    }
                }

                booksDBModel.closeDB();
                audioListDBModel.closeDB();

                Intent intent = new Intent(com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER);
                context.sendBroadcast(intent);
            } catch (Throwable t) {
                android.util.Log.e("ScanHelper", "Error scanning download folder", t);
            } finally {
                if (onDone != null) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(onDone);
                }
            }
        }).start();
    }

    private static void scanDocDirRecursive(@NonNull Context context,
                                             @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                             @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                             @NonNull androidx.documentfile.provider.DocumentFile dir) {
        if (dir.getName() != null && dir.getName().startsWith(".")) return;

        androidx.documentfile.provider.DocumentFile[] files = dir.listFiles();
        if (files == null) return;

        java.util.List<androidx.documentfile.provider.DocumentFile> audioDocFiles = new java.util.ArrayList<>();
        for (androidx.documentfile.provider.DocumentFile f : files) {
            if (f.isFile() && isAudioFileName(f.getName())) {
                audioDocFiles.add(f);
            }
        }

        if (!audioDocFiles.isEmpty()) {
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
                } else if (hasRealSavedBookWithName(booksDBModel, parent.getName())) {
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
                scanDocDirRecursive(context, booksDBModel, audioListDBModel, f);
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
        String fakeUrl = "https://" + sourceName.toLowerCase() + ".org/imported/" + sanitizeFileName(bookName);

        // If a real (non-imported) saved book with the same name exists, keep it and skip importing
        if (hasRealSavedBookWithName(booksDBModel, bookName)) {
            android.util.Log.i("ScanHelper", "Skip import '" + bookName + "' - already saved with real URL");
            return;
        }

        String photoUrl = findDocCoverImage(dir);

        // Deduplication: remove any old ExoPlayer-based saved entries for the same book name
        findAndRemoveOldSavedEntries(booksDBModel, audioListDBModel, bookName, fakeUrl);

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

        java.util.Collections.sort(audioDocFiles, new java.util.Comparator<androidx.documentfile.provider.DocumentFile>() {
            @Override
            public int compare(androidx.documentfile.provider.DocumentFile f1, androidx.documentfile.provider.DocumentFile f2) {
                String n1 = f1.getName() != null ? f1.getName() : "";
                String n2 = f2.getName() != null ? f2.getName() : "";
                return n1.compareTo(n2);
            }
        });

        audioListDBModel.remove(fakeUrl);

        for (androidx.documentfile.provider.DocumentFile audioDoc : audioDocFiles) {
            com.fanok.audiobooks.pojo.AudioListPOJO audioPOJO = new com.fanok.audiobooks.pojo.AudioListPOJO();
            audioPOJO.setBookUrl(fakeUrl);
            audioPOJO.setBookName(bookName.replace("_", " "));
            String nameNoExt = audioDoc.getName() != null ? audioDoc.getName() : "track";
            int dotPos = nameNoExt.lastIndexOf('.');
            if (dotPos > 0) nameNoExt = nameNoExt.substring(0, dotPos);
            audioPOJO.setAudioName(nameNoExt);
            audioPOJO.setAudioUrl(audioDoc.getUri() != null ? audioDoc.getUri().toString() : "");
            audioListDBModel.add(audioPOJO);
        }
    }

    private static void scanDirRecursive(@NonNull Context context,
                                         @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                         @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                         @NonNull File dir) {
        if (dir.getName().startsWith(".")) return;

        File[] audioFiles = dir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return isAudioFileName(name);
            }
        });

        if (audioFiles != null && audioFiles.length > 0) {
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
                } else if (hasRealSavedBookWithName(booksDBModel, parent.getName())) {
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
                scanDirRecursive(context, booksDBModel, audioListDBModel, subDir);
            }
        }
    }

    private static void processBookDir(@NonNull Context context,
                                       @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
                                       @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
                                       @NonNull String sourceName, @NonNull String bookName, @NonNull String voiceName,
                                       @NonNull File dir, @NonNull File[] audioFiles) {
        String fakeUrl = "https://" + sourceName.toLowerCase() + ".org/imported/" + sanitizeFileName(bookName);

        // If a real (non-imported) saved book with the same name exists, keep it and skip importing
        if (hasRealSavedBookWithName(booksDBModel, bookName)) {
            android.util.Log.i("ScanHelper", "Skip import '" + bookName + "' - already saved with real URL");
            return;
        }

        String photoUrl = findCoverImage(dir);

        // Deduplication: remove any old ExoPlayer-based saved entries for the same book name
        findAndRemoveOldSavedEntries(booksDBModel, audioListDBModel, bookName, fakeUrl);

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

        java.util.Arrays.sort(audioFiles, new java.util.Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return f1.getName().compareTo(f2.getName());
            }
        });

        audioListDBModel.remove(fakeUrl);

        for (File audioFile : audioFiles) {
            com.fanok.audiobooks.pojo.AudioListPOJO audioPOJO = new com.fanok.audiobooks.pojo.AudioListPOJO();
            audioPOJO.setBookUrl(fakeUrl);
            audioPOJO.setBookName(bookName.replace("_", " "));
            String nameNoExt = audioFile.getName();
            int dotPos = nameNoExt.lastIndexOf('.');
            if (dotPos > 0) nameNoExt = nameNoExt.substring(0, dotPos);
            audioPOJO.setAudioName(nameNoExt);
            audioPOJO.setAudioUrl(audioFile.getAbsolutePath());
            audioListDBModel.add(audioPOJO);
        }
    }

    private static boolean hasRealSavedBookWithName(
            @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
            @NonNull String bookName) {
        try {
            java.util.ArrayList<BookPOJO> allSaved = booksDBModel.getAllSaved();
            if (allSaved == null) return false;

            String normalizedName = normalizeForComparison(bookName);
            String sanitizedName = normalizeForComparison(sanitizeFileName(bookName));

            for (BookPOJO existing : allSaved) {
                if (existing.getUrl() == null || existing.getUrl().contains("/imported/")) continue;
                String existingName = normalizeForComparison(existing.getName());
                if (!normalizedName.isEmpty() && (normalizedName.equals(existingName) || sanitizedName.equals(existingName))) {
                    return true;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ScanHelper", "Error checking for existing saved book", e);
        }
        return false;
    }

    /**
     * Finds and removes old ExoPlayer-based saved entries that match the same book name
     * but have a different URL (not imported). This prevents duplicates when the same book
     * exists both as an ExoPlayer download entry and as a local imported entry.
     */
    static void findAndRemoveOldSavedEntries(
            @NonNull com.fanok.audiobooks.model.BooksDBModel booksDBModel,
            @NonNull com.fanok.audiobooks.model.AudioListDBModel audioListDBModel,
            @NonNull String bookName,
            @NonNull String newFakeUrl) {
        try {
            java.util.ArrayList<BookPOJO> allSaved = booksDBModel.getAllSaved();
            if (allSaved == null) return;

            String normalizedNewName = normalizeForComparison(bookName);
            String sanitizedNewName = normalizeForComparison(sanitizeFileName(bookName));

            for (BookPOJO existing : allSaved) {
                if (existing.getUrl() == null) continue;
                // Skip if it's the same imported URL
                if (existing.getUrl().equals(newFakeUrl)) continue;
                // Skip other imported entries
                if (existing.getUrl().contains("/imported/")) continue;

                // Check if the book name matches
                String existingName = normalizeForComparison(existing.getName());
                String existingSlug = "";
                int lastSlash = existing.getUrl().lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < existing.getUrl().length() - 1) {
                    existingSlug = normalizeForComparison(existing.getUrl().substring(lastSlash + 1));
                }

                boolean nameMatch = (!normalizedNewName.isEmpty() && (normalizedNewName.equals(existingName) || sanitizedNewName.equals(existingName)))
                        || (!existingSlug.isEmpty() && (existingSlug.equals(normalizedNewName) || existingSlug.equals(sanitizedNewName)));

                if (nameMatch) {
                    android.util.Log.i("ScanHelper", "Dedup: removing old saved entry '" + existing.getUrl() + "' (name: " + existing.getName() + ") in favor of imported '" + newFakeUrl + "'");
                    // Remove old audio list entries
                    audioListDBModel.remove(existing.getUrl());
                    // Remove old saved book entry
                    booksDBModel.removeSaved(existing);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ScanHelper", "Error during deduplication", e);
        }
    }

    @Nullable
    private static File findFileInDirByName(@NonNull File dir, @Nullable String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) return null;
        if (!dir.exists() || !dir.isDirectory()) return null;
        if (dir.getName().startsWith(".")) return null;

        String tNorm = normalizeForComparison(targetName);
        if (tNorm.isEmpty()) return null;

        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File f : files) {
            if (f.isFile() && isAudioFileName(f.getName())) {
                String fname = f.getName();
                int dot = fname.lastIndexOf('.');
                String nameNoExt = dot > 0 ? fname.substring(0, dot) : fname;
                if (normalizeForComparison(nameNoExt).equals(tNorm)) {
                    return f;
                }
            }
        }

        for (File f : files) {
            if (f.isDirectory()) {
                File res = findFileInDirByName(f, targetName);
                if (res != null) return res;
            }
        }
        return null;
    }

    public static void repairSavedBooksDatabase(@NonNull Context context) {
        new Thread(() -> {
            try {
                com.fanok.audiobooks.model.BooksDBModel booksDBModel = new com.fanok.audiobooks.model.BooksDBModel(context);
                com.fanok.audiobooks.model.AudioListDBModel audioListDBModel = new com.fanok.audiobooks.model.AudioListDBModel(context);

                java.util.ArrayList<BookPOJO> savedBooks = booksDBModel.getAllSaved();
                if (savedBooks == null || savedBooks.isEmpty()) {
                    booksDBModel.closeDB();
                    audioListDBModel.closeDB();
                    return;
                }

                String downloadPath = getDownloadPath(context);
                File baseDir = new File(downloadPath);
                java.util.HashSet<String> claimedParents = new java.util.HashSet<>();

                for (BookPOJO book : savedBooks) {
                    if (book.getUrl() == null || book.getUrl().isEmpty()) continue;
                    if (book.getUrl().contains("/imported/")) continue;

                    java.util.ArrayList<com.fanok.audiobooks.pojo.AudioListPOJO> tracks = audioListDBModel.get(book.getUrl());
                    if (tracks == null || tracks.isEmpty()) continue;

                    File bookDir = getBookDownloadDir(context, book.getUrl(), book.getName(), book.getArtist(), book.getAutor());

                    for (com.fanok.audiobooks.pojo.AudioListPOJO track : tracks) {
                        String currentUrl = track.getAudioUrl();
                        if (currentUrl != null && (currentUrl.startsWith("/") || currentUrl.startsWith("file://") || currentUrl.startsWith("content://"))) {
                            File f = new File(currentUrl.replace("file://", ""));
                            if (f.exists()) {
                                if (f.getParent() != null) claimedParents.add(f.getParent());
                                continue;
                            }
                        }

                        String audioName = track.getAudioName();
                        File foundFile = null;
                        if (bookDir.exists()) {
                            foundFile = findFileInDirByName(bookDir, audioName);
                        }

                        if (foundFile != null && foundFile.exists()) {
                            try {
                                com.fanok.audiobooks.room.BooksAudioEntity entity = audioListDBModel.getDatabase().booksAudioDao()
                                        .getByUrlAndAudioUrl(book.getUrl(), track.getAudioUrl());
                                if (entity == null && track.getCleanAudioUrl() != null) {
                                    entity = audioListDBModel.getDatabase().booksAudioDao()
                                            .getByUrlAndAudioUrl(book.getUrl(), track.getCleanAudioUrl());
                                }
                                if (entity != null) {
                                    entity.urlAudio = foundFile.getAbsolutePath();
                                    audioListDBModel.getDatabase().booksAudioDao().insert(entity);
                                    if (foundFile.getParent() != null) claimedParents.add(foundFile.getParent());
                                }
                            } catch (Exception e) {
                                android.util.Log.e("RepairDB", "Error updating track entity", e);
                            }
                        }
                    }
                }

                // Remove duplicate imported books if original book already claims the files
                for (BookPOJO book : savedBooks) {
                    if (book.getUrl() != null && book.getUrl().contains("/imported/")) {
                        java.util.ArrayList<com.fanok.audiobooks.pojo.AudioListPOJO> tracks = audioListDBModel.get(book.getUrl());
                        if (tracks != null && !tracks.isEmpty()) {
                            boolean isDuplicate = false;
                            for (com.fanok.audiobooks.pojo.AudioListPOJO t : tracks) {
                                if (t.getAudioUrl() != null) {
                                    File f = new File(t.getAudioUrl().replace("file://", ""));
                                    if (f.getParent() != null && claimedParents.contains(f.getParent())) {
                                        isDuplicate = true;
                                        break;
                                    }
                                }
                            }
                            if (isDuplicate) {
                                android.util.Log.i("RepairDB", "Removing duplicate imported book: " + book.getUrl());
                                booksDBModel.removeSaved(book);
                                audioListDBModel.remove(book.getUrl());
                            }
                        }
                    }
                }

                booksDBModel.closeDB();
                audioListDBModel.closeDB();

                Intent intent = new Intent(com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER);
                context.sendBroadcast(intent);
            } catch (Exception e) {
                android.util.Log.e("RepairDB", "Error repairing saved books database", e);
            }
        }).start();
    }

    public static void deleteChapterFiles(@NonNull Context context, @Nullable String bookUrl, @Nullable BookPOJO book, @NonNull java.util.Set<String> audioUrls, @Nullable java.util.Set<String> chapterNames) {
        if (context == null || bookUrl == null || bookUrl.isEmpty() || audioUrls == null || audioUrls.isEmpty()) return;

        try {
            com.fanok.audiobooks.model.AudioListDBModel audioListDBModel = new com.fanok.audiobooks.model.AudioListDBModel(context);
            java.util.ArrayList<com.fanok.audiobooks.pojo.AudioListPOJO> tracks = audioListDBModel.get(bookUrl);

            File bookDir = null;
            if (book != null) {
                bookDir = getBookDownloadDir(context, bookUrl, book.getName(), book.getArtist(), book.getAutor());
            }

            java.util.HashSet<String> allNamesToDelete = new java.util.HashSet<>();
            if (chapterNames != null) {
                allNamesToDelete.addAll(chapterNames);
            }

            for (String audioUrl : audioUrls) {
                if (audioUrl == null || audioUrl.isEmpty()) continue;

                // 1. Tell ExoPlayer DownloadService to remove download
                try {
                    androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
                            context,
                            com.fanok.audiobooks.service.ExoDownloadService.class,
                            audioUrl,
                            false
                    );
                    Uri uri = Uri.parse(audioUrl);
                    if (uri.getScheme() != null && uri.getAuthority() != null) {
                        String id = new Uri.Builder()
                                .scheme(uri.getScheme())
                                .authority(uri.getAuthority())
                                .path(uri.getPath())
                                .build()
                                .toString();
                        if (!id.equals(audioUrl)) {
                            androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
                                    context,
                                    com.fanok.audiobooks.service.ExoDownloadService.class,
                                    id,
                                    false
                            );
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("DownloadStorageHelper", "Error removing ExoPlayer download for " + audioUrl, e);
                }

                // 2. Find matching track from DB tracks list
                if (tracks != null) {
                    for (com.fanok.audiobooks.pojo.AudioListPOJO t : tracks) {
                        if (t == null) continue;
                        boolean isMatch = false;

                        if (audioUrl.equals(t.getAudioUrl()) || audioUrl.equals(t.getCleanAudioUrl())
                                || (t.getAudioUrl() != null && t.getAudioUrl().contains(audioUrl))
                                || (t.getCleanAudioUrl() != null && audioUrl.contains(t.getCleanAudioUrl()))) {
                            isMatch = true;
                        }

                        if (!isMatch && chapterNames != null && t.getAudioName() != null) {
                            if (chapterNames.contains(t.getAudioName())) {
                                isMatch = true;
                            }
                        }

                        if (isMatch) {
                            if (t.getAudioName() != null && !t.getAudioName().isEmpty()) {
                                allNamesToDelete.add(t.getAudioName());
                            }
                            String tUrl = t.getAudioUrl();
                            if (tUrl != null && !tUrl.isEmpty()) {
                                if (tUrl.startsWith("content://")) {
                                    try {
                                        androidx.documentfile.provider.DocumentFile doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, Uri.parse(tUrl));
                                        if (doc != null && doc.exists()) doc.delete();
                                    } catch (Exception ignored) {}
                                } else if (tUrl.startsWith("file://") || tUrl.startsWith("/")) {
                                    File f = new File(tUrl.replace("file://", ""));
                                    if (f.exists()) f.delete();
                                }
                            }
                        }
                    }
                }

                // Direct file delete if audioUrl is a local file path
                if (audioUrl.startsWith("content://")) {
                    try {
                        androidx.documentfile.provider.DocumentFile doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, Uri.parse(audioUrl));
                        if (doc != null && doc.exists()) doc.delete();
                    } catch (Exception ignored) {}
                } else if (audioUrl.startsWith("file://") || audioUrl.startsWith("/")) {
                    File f = new File(audioUrl.replace("file://", ""));
                    if (f.exists()) f.delete();
                }

                // 3. Update or remove track entry in DB by URL
                if (bookUrl.contains("/imported/")) {
                    audioListDBModel.removeTrack(bookUrl, audioUrl);
                } else {
                    try {
                        com.fanok.audiobooks.room.BooksAudioEntity entity = audioListDBModel.getDatabase().booksAudioDao()
                                .getByUrlAndAudioUrl(bookUrl, audioUrl);
                        if (entity == null) {
                            String cleanUrl = audioUrl;
                            int q = cleanUrl.indexOf('?');
                            if (q >= 0) cleanUrl = cleanUrl.substring(0, q);
                            entity = audioListDBModel.getDatabase().booksAudioDao()
                                    .getByUrlAndAudioUrl(bookUrl, "%" + cleanUrl + "%");
                        }
                        if (entity != null) {
                            entity.urlAudio = audioUrl;
                            audioListDBModel.getDatabase().booksAudioDao().insert(entity);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 4. Delete physical files matching allNamesToDelete in bookDir and SAF root
            for (String name : allNamesToDelete) {
                if (name == null || name.isEmpty()) continue;
                if (bookUrl.contains("/imported/")) {
                    audioListDBModel.removeTrack(bookUrl, name);
                }

                if (bookDir != null && bookDir.exists()) {
                    deletePhysicalTrackFile(bookDir, name);
                }

                androidx.documentfile.provider.DocumentFile rootDoc = getRootDocumentFile(context);
                if (rootDoc != null) {
                    deleteDocMatching(rootDoc, name, new StringBuilder());
                }
            }

            // 5. Check if any downloaded audio files remain for this book
            boolean hasRemainingDownloaded = false;
            java.util.ArrayList<com.fanok.audiobooks.pojo.AudioListPOJO> currentTracks = audioListDBModel.get(bookUrl);
            androidx.media3.exoplayer.offline.DownloadIndex downloadIndex = null;
            try {
                downloadIndex = com.fanok.audiobooks.util.DownloadUtil.getDownloadManager().getDownloadIndex();
            } catch (Exception ignored) {}

            if (currentTracks != null) {
                for (com.fanok.audiobooks.pojo.AudioListPOJO t : currentTracks) {
                    if (t == null) continue;
                    String tUrl = t.getAudioUrl();
                    if (tUrl != null && !tUrl.isEmpty()) {
                        if (tUrl.startsWith("content://") || tUrl.startsWith("file://")) {
                            hasRemainingDownloaded = true;
                            break;
                        } else if (tUrl.startsWith("/")) {
                            if (new File(tUrl).exists()) {
                                hasRemainingDownloaded = true;
                                break;
                            }
                        }
                    }
                    if (downloadIndex != null && t.getCleanAudioUrl() != null && !t.getCleanAudioUrl().isEmpty()) {
                        try {
                            androidx.media3.exoplayer.offline.Download d = downloadIndex.getDownload(t.getCleanAudioUrl());
                            if (d != null && d.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                                hasRemainingDownloaded = true;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (bookDir != null && bookDir.exists() && t.getAudioName() != null && !t.getAudioName().isEmpty()) {
                        String sanitized = sanitizeFileName(t.getAudioName());
                        File trackFile = new File(bookDir, sanitized + ".mp3");
                        if (trackFile.exists()) {
                            hasRemainingDownloaded = true;
                            break;
                        }
                    }
                }
            }

            if (!hasRemainingDownloaded) {
                deleteBookFiles(context, bookUrl, book);
                com.fanok.audiobooks.model.BooksDBModel booksDBModel = new com.fanok.audiobooks.model.BooksDBModel(context);
                if (book != null) booksDBModel.removeSaved(book);
                booksDBModel.removeSavedByUrl(bookUrl);
                booksDBModel.closeDB();
                audioListDBModel.remove(bookUrl);
            }

            audioListDBModel.closeDB();

            Intent intent = new Intent(com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER);
            context.sendBroadcast(intent);

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    context.sendBroadcast(new Intent(com.fanok.audiobooks.activity.BookActivity.Broadcast_UPDATE_ADAPTER));
                } catch (Exception ignored) {}
            }, 600);

        } catch (Exception e) {
            android.util.Log.e("DownloadStorageHelper", "Error deleting chapter files", e);
        }
    }

    public static void deleteChapterFiles(@NonNull Context context, @Nullable String bookUrl, @Nullable BookPOJO book, @NonNull java.util.Set<String> audioUrls) {
        deleteChapterFiles(context, bookUrl, book, audioUrls, null);
    }

    private static void deletePhysicalTrackFile(@NonNull File dir, @NonNull String audioName) {
        if (!dir.exists() || !dir.isDirectory()) return;

        String sanitized = sanitizeFileName(audioName);
        String normName = normalizeForComparison(audioName);
        String normSanitized = normalizeForComparison(sanitized);

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    String fname = f.getName();
                    int dot = fname.lastIndexOf('.');
                    String nameNoExt = dot > 0 ? fname.substring(0, dot) : fname;
                    String normFile = normalizeForComparison(nameNoExt);
                    String normFullFile = normalizeForComparison(fname);
                    if (normFile.equals(normName) || normFile.equals(normSanitized)
                            || normFullFile.equals(normName) || normFullFile.equals(normSanitized)
                            || (!normFile.isEmpty() && normName.contains(normFile))
                            || (!normFile.isEmpty() && normSanitized.contains(normFile))) {
                        f.delete();
                    }
                } else if (f.isDirectory()) {
                    deletePhysicalTrackFile(f, audioName);
                }
            }
        }
    }
}