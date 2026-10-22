package com.fanok.audiobooks;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.preference.PreferenceManager;

import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;


import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.Executors;

@UnstableApi
public class App extends Application {

    // proxy removed; call sites deleted in Phase 1
    public static final boolean useProxy = false;
    private DownloadManager downloadManager;
    private SimpleCache downloadCache;

    private StandaloneDatabaseProvider databaseProvider;

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

            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();

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

        // --- ВОЗВРАЩАЕМ ПРОСТОЙ И ПОНЯТНЫЙ КОД ---
        databaseProvider = new StandaloneDatabaseProvider(this);
        reinitDownloadManagerAsync();


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
        return databaseProvider;
    }

    public static App getInstance() {
        return instance;
    }

    @UnstableApi
    public DownloadManager getDownloadManager() {
        DownloadManager dm = downloadManager;
        if (dm == null) {
            synchronized (this) {
                dm = downloadManager;
                if (dm == null) {
                    reinitDownloadManager();
                    dm = downloadManager;
                }
            }
        }
        return dm;
    }

    public void reinitDownloadManagerAsync() {
        new Thread(() -> {
            try {
                reinitDownloadManager();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, "reinit-download-manager").start();
    }

    public synchronized void reinitDownloadManager() {
        try {
            stopService(new android.content.Intent(this, com.fanok.audiobooks.service.ExoDownloadService.class));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (downloadManager != null) {
            try {
                downloadManager.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            downloadManager = null;
        }
        if (downloadCache != null) {
            try {
                downloadCache.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            downloadCache = null;
        }

        File baseDownloadDir = new File(DownloadStorageHelper.getDownloadPath(this));
        if (!baseDownloadDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            baseDownloadDir.mkdirs();
        }
        File downloadDirectory = new File(baseDownloadDir, ".audiobooks_cache");
        if (!downloadDirectory.exists()) {
            if (!downloadDirectory.mkdirs()) {
                downloadDirectory = baseDownloadDir;
            }
        }
        try {
            downloadCache = new SimpleCache(downloadDirectory, new NoOpCacheEvictor(), databaseProvider);
        } catch (Exception e) {
            e.printStackTrace();
            downloadDirectory = new File(getCacheDir(), "downloads");
            //noinspection ResultOfMethodCallIgnored
            downloadDirectory.mkdirs();
            downloadCache = new SimpleCache(downloadDirectory, new NoOpCacheEvictor(), databaseProvider);
        }

        DataSource.Factory resolvingDataSourceFactory = DownloadUtil.getResolvingUpstreamFactory(this);

        downloadManager = new DownloadManager(
                this,
                databaseProvider,
                downloadCache,
                resolvingDataSourceFactory,
                Executors.newFixedThreadPool(3)
        );
        downloadManager.setMaxParallelDownloads(1);
    }

    public SimpleCache getDownloadCache() {
        SimpleCache cache = downloadCache;
        if (cache == null) {
            getDownloadManager();
            cache = downloadCache;
        }
        return cache;
    }

}
