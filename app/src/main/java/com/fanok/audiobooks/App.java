package com.fanok.audiobooks;

import static com.fanok.audiobooks.Consts.PROXY_PASSWORD;
import static com.fanok.audiobooks.Consts.PROXY_USERNAME;

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


import java.io.File;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.concurrent.Executors;

@UnstableApi
public class App extends Application {

    public static boolean useProxy;
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

    public static File getAnrFile() {
        return new java.io.File(getInstance().getFilesDir(), "anr.txt");
    }

    private static volatile String currentActivityName = "None";
    private static volatile boolean isAppInForeground = false;
    private static int startedActivitiesCount = 0;

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
            sb.append("Proxy: ").append(useProxy ? "ON" : "OFF").append("\n");
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

    private final android.os.Handler anrHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile long lastMainHeartbeat = android.os.SystemClock.uptimeMillis();
    private static boolean anrWatcherStarted = false;
    private volatile long lastAnrReportMs = 0;

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            lastMainHeartbeat = android.os.SystemClock.uptimeMillis();
            if (isAppInForeground) {
                anrHandler.postDelayed(this, 1000);
            }
        }
    };

    private void restartHeartbeat() {
        lastMainHeartbeat = android.os.SystemClock.uptimeMillis();
        anrHandler.removeCallbacks(heartbeatRunnable);
        anrHandler.post(heartbeatRunnable);
    }

    private void startAnrWatcher() {
        if (anrWatcherStarted) return;
        anrWatcherStarted = true;
        restartHeartbeat();

        final Thread watcher = new Thread(() -> {
            while (true) {
                try {
                    if (!isAppInForeground) {
                        Thread.sleep(5000);
                        lastMainHeartbeat = android.os.SystemClock.uptimeMillis();
                        continue;
                    }
                    Thread.sleep(3000);
                    long stuckFor = android.os.SystemClock.uptimeMillis() - lastMainHeartbeat;
                    if (stuckFor > 6000) {
                        appendAnr(stuckFor);
                    }
                } catch (Exception ignored) {
                    return;
                }
            }
        }, "anr-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private boolean isMainThreadIdle(Thread mainThread) {
        try {
            StackTraceElement[] st = mainThread.getStackTrace();
            if (st.length == 0) return false;
            return "android.os.MessageQueue".equals(st[0].getClassName())
                    && "nativePollOnce".equals(st[0].getMethodName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void appendAnr(long stuckMs) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastAnrReportMs < 120000) return;
            Thread mainThread = android.os.Looper.getMainLooper().getThread();
            if (isMainThreadIdle(mainThread)) return;
            StringBuilder report = new StringBuilder();
            String state = "";
            for (int i = 0; i < 5; i++) {
                if (isMainThreadIdle(mainThread)) break;
                StackTraceElement[] st = mainThread.getStackTrace();
                state = String.valueOf(mainThread.getState());
                if (st.length > 0) {
                    if (report.length() > 0) report.append("      -- sample ").append(i + 1).append("\n");
                    for (StackTraceElement e : st) {
                        report.append("    at ").append(e).append("\n");
                    }
                }
                Thread.sleep(600);
            }
            if (report.length() == 0) return;
            lastAnrReportMs = android.os.SystemClock.uptimeMillis();

            Runtime r = Runtime.getRuntime();
            long maxMb = r.maxMemory() / (1024 * 1024);
            long totalMb = r.totalMemory() / (1024 * 1024);
            long freeMb = r.freeMemory() / (1024 * 1024);
            long usedMb = totalMb - freeMb;

            StringBuilder sb = new StringBuilder();
            sb.append("=== ANR main-thread blocked ").append(stuckMs).append(" ms (").append(new java.util.Date())
                    .append(") state=").append(state).append(" ===\n");
            sb.append("Screen: ").append(currentActivityName).append("\n");
            sb.append("Memory: used ").append(usedMb).append("MB / free ").append(freeMb).append("MB / max ").append(maxMb).append("MB\n");
            sb.append("Proxy: ").append(useProxy ? "ON" : "OFF").append("\n");
            sb.append(report.toString()).append("\n");

            appendCapped(getAnrFile(), sb.toString(), 30000);
        } catch (Exception ignored) {
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
                startedActivitiesCount++;
                isAppInForeground = true;
                currentActivityName = activity.getClass().getSimpleName();
                restartHeartbeat();
            }
            @Override
            public void onActivityResumed(@androidx.annotation.NonNull android.app.Activity activity) {
                isAppInForeground = true;
                currentActivityName = activity.getClass().getSimpleName();
                restartHeartbeat();
            }
            @Override
            public void onActivityPaused(@androidx.annotation.NonNull android.app.Activity activity) {}
            @Override
            public void onActivityStopped(@androidx.annotation.NonNull android.app.Activity activity) {
                startedActivitiesCount = Math.max(0, startedActivitiesCount - 1);
                if (startedActivitiesCount == 0) {
                    isAppInForeground = false;
                }
            }
            @Override
            public void onActivitySaveInstanceState(@androidx.annotation.NonNull android.app.Activity activity, @androidx.annotation.NonNull android.os.Bundle outState) {}
            @Override
            public void onActivityDestroyed(@androidx.annotation.NonNull android.app.Activity activity) {}
        });

        startAnrWatcher();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeStackToFile(getCrashFile(), throwable);
            } catch (Exception ignored) {
            }
        });

        try {
            try {
                java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1);
            } catch (Throwable ignored) {}

            javax.net.ssl.SSLSocketFactory sf = Consts.socketFactory();
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sf);
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                @Override
                public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                    return true;
                }
            });

            // Настройка Picasso / OkHttp для обхода просроченного сертификата CDN
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
            };

            // Читаем настройки прокси перед созданием клиента
            android.content.SharedPreferences pref = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(this);
            useProxy = pref.getBoolean("pref_proxy", false);
            
            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder()
                    .sslSocketFactory(sf, (javax.net.ssl.X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true);

            if (useProxy) {
                java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS,
                        new java.net.InetSocketAddress(Consts.PROXY_HOST, Consts.PROXY_PORT));
                builder.proxy(proxy);
            }

            builder.addNetworkInterceptor(new okhttp3.Interceptor() {
                @Override
                public okhttp3.Response intercept(Chain chain) throws java.io.IOException {
                    okhttp3.Request original = chain.request();
                    okhttp3.Request.Builder requestBuilder = original.newBuilder();
                    
                    // Unconditionally force a real browser User-Agent to prevent 403s on audio streams
                    requestBuilder.header("User-Agent", Consts.USER_AGENT);
                    
                    if (original.url().toString().contains("poleknig.com")) {
                        requestBuilder.header("Referer", "https://poleknig.com/");
                    } else if (original.url().toString().contains("izib.uk") || (original.header("Referer") != null && original.header("Referer").contains("izib.uk"))) {
                        requestBuilder.header("Referer", Url.SERVER_IZIBUK + "/");
                    } else if (original.url().toString().contains("akniga.org")) {
                        requestBuilder.header("Referer", "https://akniga.org/");
                    }
                    
                    return chain.proceed(requestBuilder.build());
                }
            });

            okHttpClientInstance = builder.build();

            com.squareup.picasso.Picasso picasso = new com.squareup.picasso.Picasso.Builder(this)
                    .downloader(new com.squareup.picasso.OkHttp3Downloader(okHttpClientInstance))
                    .loggingEnabled(true)
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

        useProxy = pref.getBoolean("pref_proxy", false);
        if(useProxy){
            Authenticator authenticator = new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    return (new PasswordAuthentication(PROXY_USERNAME,
                            PROXY_PASSWORD.toCharArray()));
                }
            };
            Authenticator.setDefault(authenticator);
        }
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
