package com.fanok.audiobooks;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.net.MemoryCookieJar;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;


import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class App extends Application {

    // proxy removed; call sites deleted in Phase 1
    public static final boolean useProxy = false;
    /** Name of the Media3 download store below the app-specific files directory. */
    static final String DOWNLOAD_CACHE_DIR = "media3_downloads";
    private static final String PREF_DOWNLOAD_CACHE_DIR = "media3_download_cache_dir";

    // One download manager, cache and executor for the process lifetime, created once on first use and never
    // released or re-created: the download folder setting only names where explicit exports go.
    private volatile DownloadManager downloadManager;
    private volatile SimpleCache downloadCache;
    private File downloadCacheDir;
    private ExecutorService downloadExecutor;

    private volatile StandaloneDatabaseProvider databaseProvider;
    private static final MemoryCookieJar COOKIE_JAR = new MemoryCookieJar();

    private static App instance;

    @Override
    protected void attachBaseContext(Context base) {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(base);

        String lang = pref.getString("pref_lang", "ru");
        super.attachBaseContext(LocaleManager.onAttach(base, lang));
    }

    public static File getCrashFile() {
        return new java.io.File(getInstance().getFilesDir(), "crash.txt");
    }

    private static volatile String currentActivityName = "None";

    private static void appendCapped(File file, String content, int maxBytes) {
        try {
            StringBuilder sb = new StringBuilder();
            if (file.exists() && file.length() > maxBytes) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    java.util.List<String> lines = new java.util.ArrayList<>();
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                    int keepCount = Math.max(1, lines.size() / 2);
                    for (int i = lines.size() - keepCount; i < lines.size(); i++) {
                        sb.append(lines.get(i)).append("\n");
                    }
                }
            } else if (file.exists()) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
            }
            sb.append(content);
            try (java.io.FileOutputStream fw = new java.io.FileOutputStream(file, false)) {
                fw.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fw.flush();
            }
        } catch (Exception ignored) {}
    }

    private static void writeStackToFile(File file, Throwable t) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== CRASH ").append(new java.util.Date()).append(" ===\n");
            sb.append("Screen: ").append(currentActivityName).append("\n");
            Runtime r = Runtime.getRuntime();
            long maxMb = r.maxMemory() / (1024 * 1024);
            long totalMb = r.totalMemory() / (1024 * 1024);
            long freeMb = r.freeMemory() / (1024 * 1024);
            long usedMb = totalMb - freeMb;
            sb.append("Memory: used ").append(usedMb).append("MB / free ").append(freeMb).append("MB / max ").append(maxMb).append("MB\n");
            if (t != null) {
                sb.append("EXCEPTION: ").append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
                for (StackTraceElement e : t.getStackTrace()) {
                    sb.append("    at ").append(e).append("\n");
                }
                Throwable cause = t.getCause();
                while (cause != null) {
                    sb.append("Caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append("\n");
                    for (StackTraceElement e : cause.getStackTrace()) {
                        sb.append("    at ").append(e).append("\n");
                    }
                    cause = cause.getCause();
                }
            }
            sb.append("\n");
            appendCapped(file, sb.toString(), 30000);
        } catch (Exception ignored) {
        }
    }

    /**
     * RxJava 2 global error handler: errors that can no longer be delivered (the subscriber is
     * disposed) are logged when they are expected network/cancellation noise; bugs are forwarded
     * to the thread's uncaught-exception handler instead of being swallowed.
     */
    static void handleRxError(Throwable e) {
        if (e instanceof UndeliverableException && e.getCause() != null) {
            e = e.getCause();
        }
        if (e instanceof IOException || e instanceof SocketException || e instanceof InterruptedException) {
            android.util.Log.w("App", "Undeliverable Rx error", e);
            return;
        }
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
        if (handler != null) {
            handler.uncaughtException(thread, e);
        }
    }

    public static okhttp3.OkHttpClient getOkHttpClient() {
        return okHttpClientInstance;
    }

    public static okhttp3.OkHttpClient okHttpClientInstance;

    /** Cookies of the shared client: response cookies are resent to their host for the process lifetime. */
    public static MemoryCookieJar getCookieJar() {
        return COOKIE_JAR;
    }

    @UnstableApi
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@androidx.annotation.NonNull android.app.Activity activity, @androidx.annotation.Nullable android.os.Bundle savedInstanceState) {}
            @Override
            public void onActivityStarted(@androidx.annotation.NonNull android.app.Activity activity) {
                currentActivityName = activity.getClass().getSimpleName();
            }
            @Override
            public void onActivityResumed(@androidx.annotation.NonNull android.app.Activity activity) {
                currentActivityName = activity.getClass().getSimpleName();
            }
            @Override
            public void onActivityPaused(@androidx.annotation.NonNull android.app.Activity activity) {}
            @Override
            public void onActivityStopped(@androidx.annotation.NonNull android.app.Activity activity) {}
            @Override
            public void onActivitySaveInstanceState(@androidx.annotation.NonNull android.app.Activity activity, @androidx.annotation.NonNull android.os.Bundle outState) {}
            @Override
            public void onActivityDestroyed(@androidx.annotation.NonNull android.app.Activity activity) {}
        });

        final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeStackToFile(getCrashFile(), throwable);
            } catch (Exception ignored) {
            } finally {
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
        RxJavaPlugins.setErrorHandler(App::handleRxError);

        try {
            try {
                java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1);
            } catch (Throwable ignored) {}

            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder().cookieJar(COOKIE_JAR);

            // Application interceptor (runs before OkHttp adds its own User-Agent): fills in defaults
            // only, never overrides a header the caller or the DownloadUtil resolver already set.
            builder.addInterceptor(chain -> {
                okhttp3.Request original = chain.request();
                okhttp3.Request.Builder requestBuilder = original.newBuilder();
                if (original.header("User-Agent") == null) {
                    requestBuilder.header("User-Agent", Consts.USER_AGENT);
                }
                if (original.header("Referer") == null) {
                    String url = original.url().toString();
                    if (url.contains("poleknig.com")) {
                        requestBuilder.header("Referer", "https://poleknig.com/");
                    } else if (url.contains("izib.uk")) {
                        requestBuilder.header("Referer", Url.SERVER_IZIBUK + "/");
                    } else if (url.contains("akniga.org")) {
                        requestBuilder.header("Referer", "https://akniga.org/");
                    }
                }
                return chain.proceed(requestBuilder.build());
            });

            okHttpClientInstance = builder.build();

            com.squareup.picasso.Picasso picasso = new com.squareup.picasso.Picasso.Builder(this)
                    .downloader(new com.squareup.picasso.OkHttp3Downloader(okHttpClientInstance))
                    .loggingEnabled(BuildConfig.DEBUG)
                    .build();
            com.squareup.picasso.Picasso.setSingletonInstance(picasso);

                    } catch (Exception e) {
            e.printStackTrace();
                    }


        // User's original code
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        Consts.setBazaKnigCookies(pref.getString("cookes_baza_knig", ""));


        BookPresenter.setSpeedWithoutBroadcast(new StorageUtil(getBaseContext()).loadSpeed());

        String source = pref.getString("sorce_books", getString(R.string.abook_value));

        Consts.setSOURCE(this, source);
    }


    public StandaloneDatabaseProvider getDatabaseProvider() {
        StandaloneDatabaseProvider provider = databaseProvider;
        if (provider == null) {
            synchronized (this) {
                if (databaseProvider == null) databaseProvider = new StandaloneDatabaseProvider(this);
                provider = databaseProvider;
            }
        }
        return provider;
    }

    public static App getInstance() {
        return instance;
    }

    @UnstableApi
    public DownloadManager getDownloadManager() {
        DownloadManager dm = downloadManager;
        if (dm == null) {
            createDownloadStack();
            dm = downloadManager;
        }
        return dm;
    }

    public SimpleCache getDownloadCache() {
        SimpleCache cache = downloadCache;
        if (cache == null) {
            createDownloadStack();
            cache = downloadCache;
        }
        return cache;
    }

    /** Directory of the Media3 download store (app-specific, never the user folder or the clearable cache dir). */
    public File getDownloadCacheDir() {
        getDownloadCache();
        return downloadCacheDir;
    }

    /**
     * Creates the download store once: a non-evicting {@link SimpleCache} in app-specific storage, its executor (Media3
     * downloads HLS segments in parallel on it) and the manager. The volatile manager is published last, so a reader
     * that sees it also sees the cache.
     */
    @UnstableApi
    private synchronized void createDownloadStack() {
        if (downloadManager != null) return;
        File dir = downloadStoreDir();
        SimpleCache cache = new SimpleCache(dir, new NoOpCacheEvictor(), getDatabaseProvider());
        ExecutorService executor = Executors.newFixedThreadPool(3);
        DownloadManager dm = new DownloadManager(this, new DefaultDownloadIndex(getDatabaseProvider()),
                DownloadUtil.downloaderFactory(this, cache, executor));
        dm.setMaxParallelDownloads(1);
        dm.setRequirements(new Requirements(Requirements.NETWORK));
        downloadCacheDir = dir;
        downloadExecutor = executor;
        downloadCache = cache;
        downloadManager = dm;
    }

    /**
     * The store directory, chosen once and remembered so it never moves between runs. On first use a store left in
     * the old place (below the download folder) is moved here when the rename is possible; otherwise its downloads
     * keep their exported copies and stream again.
     */
    private File downloadStoreDir() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String saved = pref.getString(PREF_DOWNLOAD_CACHE_DIR, null);
        if (saved != null) return new File(saved);
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File dir = new File(base, DOWNLOAD_CACHE_DIR);
        if (!dir.exists()) {
            File legacy = new File(DownloadStorageHelper.getDownloadPath(this), ".audiobooks_cache");
            if (legacy.isDirectory() && legacy.renameTo(dir)) {
                android.util.Log.i("App", "Moved the download store to " + dir);
            }
        }
        pref.edit().putString(PREF_DOWNLOAD_CACHE_DIR, dir.getAbsolutePath()).apply();
        return dir;
    }

    @Override
    public void onTerminate() {
        ExecutorService executor = downloadExecutor;
        if (executor != null) executor.shutdown();
        DownloadUtil.shutdown();
        super.onTerminate();
    }

}
