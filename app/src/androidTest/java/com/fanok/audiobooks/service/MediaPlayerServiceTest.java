package com.fanok.audiobooks.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media3.common.Player;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Drives the real service through its start command, its notification actions and its command methods, against
 * a loopback HTTPS server serving generated WAV chapters (the app forbids cleartext, so the test hands the service
 * an upstream that trusts the server's own certificate).
 */
@RunWith(AndroidJUnit4.class)
public class MediaPlayerServiceTest {

    @Rule
    public final ServiceTestRule serviceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @Rule
    public final TestRule notifications = Build.VERSION.SDK_INT >= 33
            ? GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) : RuleChain.emptyRuleChain();

    private Context context;
    private MockWebServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final List<String> books = new ArrayList<>();
    private final Object failuresLock = new Object();
    private int failuresLeft;
    private volatile long headersDelayMs;
    private volatile boolean neverAnswer;
    private String savedLast;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse("a player service from another test is still running", awaitStopped());
        savedLast = storagePrefs().getString(Checkpoint.KEY_LAST, null);

        String localhost = InetAddress.getByName("localhost").getCanonicalHostName();
        HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName(localhost).build();
        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
                .heldCertificate(certificate).build();
        HandshakeCertificates clientCertificates = new HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate()).build();
        server = new MockWebServer();
        server.useHttps(serverCertificates.sslSocketFactory(), false);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return respond(request);
            }
        });
        server.start();
        OkHttpClient client = new OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager())
                .build();
        MediaPlayerService.setUpstreamFactoryForTest(new OkHttpDataSource.Factory(client));
        BookPresenter.setSpeedWithoutBroadcast(1f);
    }

    @After
    public void tearDown() throws Exception {
        neverAnswer = false;
        serviceRule.unbindService();
        context.stopService(new Intent(context, MediaPlayerService.class));
        boolean stillRunning = awaitStopped();
        MediaPlayerService.setUpstreamFactoryForTest(null);
        server.shutdown();
        SharedPreferences.Editor editor = storagePrefs().edit();
        for (String book : books) {
            editor.remove(Checkpoint.key(book));
        }
        if (savedLast != null) {
            editor.putString(Checkpoint.KEY_LAST, savedLast);
        } else {
            editor.remove(Checkpoint.KEY_LAST);
        }
        editor.commit();
        AudioDBModel legacy = new AudioDBModel(context);
        for (String book : books) {
            legacy.remove(book);
        }
        assertFalse("the service did not stop", stillRunning);
    }

    @Test
    public void duplicatePauseStaysPaused() throws Exception {
        MediaPlayerService service = startAndBind(book("pause-twice", 20), true);
        await("playing", 8_000, () -> player(service).isPlaying());

        sendAction(MediaPlayerService.ACTION_PAUSE);
        sendAction(MediaPlayerService.ACTION_PAUSE);
        await("paused", 3_000, () -> !player(service).getPlayWhenReady() && !service.isWantPlaying());
        Thread.sleep(1_000);

        assertTrue(describe(service), onMain(() -> !player(service).getPlayWhenReady()
                && !player(service).isPlaying() && !service.isWantPlaying()));
        assertEquals(PlaybackStateCompat.STATE_PAUSED,
                (int) onMain(() -> service.getSessionPlaybackState().getState()));
    }

    @Test
    public void pauseWhileBufferingClearsIntentAndNothingPlaysAfterTheResponse() throws Exception {
        headersDelayMs = 3_000;
        MediaPlayerService service = startAndBind(book("pause-buffering", 20), true);
        await("buffering on a held response", 5_000, () -> server.getRequestCount() >= 1
                && player(service).getPlaybackState() == Player.STATE_BUFFERING
                && player(service).getPlayWhenReady());

        sendAction(MediaPlayerService.ACTION_PAUSE);
        await("intent cleared while buffering", 2_000,
                () -> !player(service).getPlayWhenReady() && !service.isWantPlaying());
        await("response delivered", 8_000, () -> player(service).getPlaybackState() == Player.STATE_READY);
        Thread.sleep(700);

        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));
    }

    @Test
    public void fourServerErrorsThenPlayRecovers() throws Exception {
        synchronized (failuresLock) {
            failuresLeft = 4;
        }
        MediaPlayerService service = startAndBind(book("errors", 20), true);
        await("four 503s surfaced as a player error", 10_000, () -> server.getRequestCount() >= 4
                && player(service).getPlayerError() != null);

        onMain(() -> {
            service.play();
            return null;
        });
        await("playing after Play", 8_000, () -> player(service).isPlaying());
        assertTrue(server.getRequestCount() >= 5);
    }

    @Test
    public void stopServiceWhilePlayingPersistsTheCheckpoint() throws Exception {
        MediaPlayerService.Request request = book("stop", 20);
        MediaPlayerService service = startAndBind(request, true);
        await("playing past a second", 8_000,
                () -> player(service).isPlaying() && player(service).getCurrentPosition() >= 1_000);

        serviceRule.unbindService();
        context.stopService(new Intent(context, MediaPlayerService.class));
        assertFalse("service destroyed", awaitStopped());

        Checkpoint checkpoint = new StorageUtil(context).loadCheckpoint(request.bookUrl);
        assertNotNull(checkpoint);
        assertEquals(0, checkpoint.chapterIndex);
        assertTrue("position " + checkpoint.positionMs, checkpoint.positionMs >= 1_000);
    }

    @Test
    public void coldStartPausedAgainstASilentServerIsForegroundAtOnce() throws Exception {
        neverAnswer = true;
        MediaPlayerService.start(context, book("cold", 20));
        await("foreground notification", 5_000, this::hasForegroundNotification);

        MediaPlayerService service = bind();
        Thread.sleep(1_500);
        assertTrue(MediaPlayerService.isRunning());
        assertTrue(describe(service), onMain(() -> !service.isWantPlaying()
                && !player(service).getPlayWhenReady()));
        assertTrue(hasForegroundNotification());
    }

    @Test
    public void previousOnFirstChapterThenNaturalEndContinuesToTheNextChapter() throws Exception {
        MediaPlayerService service = startAndBind(book("chapters", 3, 3), true);
        await("chapter 0 playing", 8_000, () -> service.getChapterIndex() == 0 && player(service).isPlaying());

        onMain(() -> {
            service.previous();
            return null;
        });
        await("chapter 1 playing", 10_000, () -> service.getChapterIndex() == 1 && player(service).isPlaying());
    }

    @Test
    public void speedChangeWhilePausedStaysPaused() throws Exception {
        MediaPlayerService service = startAndBind(book("speed", 20), false);
        await("ready and paused", 8_000, () -> player(service).getPlaybackState() == Player.STATE_READY);

        context.sendBroadcast(new Intent(BookPresenter.Broadcast_SET_SPEED)
                .putExtra("speed", 1.5f).setPackage(context.getPackageName()));
        await("speed applied", 3_000, () -> player(service).getPlaybackParameters().speed == 1.5f);
        Thread.sleep(500);

        assertFalse(describe(service), onMain(() -> player(service).isPlaying()
                || player(service).getPlayWhenReady() || service.isWantPlaying()));
    }

    // -----------------------------------------------------------------------------------------------------

    private MockResponse respond(RecordedRequest request) {
        if (neverAnswer) {
            return new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE);
        }
        synchronized (failuresLock) {
            if (failuresLeft > 0) {
                failuresLeft--;
                return new MockResponse().setResponseCode(503);
            }
        }
        byte[] wav = request.getRequestUrl() != null ? files.get(request.getRequestUrl().encodedPath()) : null;
        if (wav == null) {
            return new MockResponse().setResponseCode(404);
        }
        int start = 0;
        String range = request.getHeader("Range");
        if (range != null && range.startsWith("bytes=")) {
            String from = range.substring(6).split("-", 2)[0].trim();
            if (!from.isEmpty()) {
                start = (int) Math.min(Long.parseLong(from), wav.length);
            }
        }
        MockResponse response = new MockResponse()
                .setHeader("Content-Type", "audio/wav")
                .setHeader("Accept-Ranges", "bytes")
                .setBody(new Buffer().write(wav, start, wav.length - start));
        if (start > 0) {
            response.setResponseCode(206)
                    .setHeader("Content-Range", "bytes " + start + "-" + (wav.length - 1) + "/" + wav.length);
        }
        if (headersDelayMs > 0) {
            response.setHeadersDelay(headersDelayMs, TimeUnit.MILLISECONDS);
        }
        return response;
    }

    /** 8 kHz mono 16-bit PCM silence. */
    private static byte[] wav(int seconds) {
        int rate = 8_000;
        int dataLength = seconds * rate * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataLength)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataLength);
        return buffer.array();
    }

    /** A paused-or-playing request for a book whose chapters last {@code seconds} each. */
    private MediaPlayerService.Request book(String name, int... seconds) {
        String bookUrl = "https://test.invalid/" + name + "/" + SystemClock.elapsedRealtimeNanos();
        books.add(bookUrl);
        ArrayList<AudioPOJO> chapters = new ArrayList<>();
        for (int i = 0; i < seconds.length; i++) {
            String path = "/" + name + "/" + i + ".wav";
            files.put(path, wav(seconds[i]));
            AudioPOJO audio = new AudioPOJO();
            audio.setName(name + " " + i);
            audio.setBookName(name);
            audio.setUrl(server.url(path).toString());
            chapters.add(audio);
        }
        BookPOJO book = new BookPOJO();
        book.setUrl(bookUrl);
        book.setName(name);
        return new MediaPlayerService.Request(book, bookUrl, chapters, null, 0, 0, false);
    }

    private MediaPlayerService startAndBind(MediaPlayerService.Request paused, boolean play) throws Exception {
        MediaPlayerService.start(context, new MediaPlayerService.Request(paused.book, paused.bookUrl,
                paused.chapters, null, 0, 0, play));
        return bind();
    }

    private MediaPlayerService bind() throws Exception {
        IBinder binder = serviceRule.bindService(new Intent(context, MediaPlayerService.class));
        return ((MediaPlayerService.LocalBinder) binder).getService();
    }

    private void sendAction(String action) {
        context.startService(new Intent(context, MediaPlayerService.class).setAction(action));
    }

    private boolean hasForegroundNotification() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getId() == MediaPlayerService.getNotificationId()
                    && (notification.getNotification().flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
                return true;
            }
        }
        return false;
    }

    private SharedPreferences storagePrefs() {
        return context.getSharedPreferences("STORAGE", Context.MODE_PRIVATE);
    }

    /** @return true when the service is still running after 5 s */
    private static boolean awaitStopped() throws InterruptedException {
        long end = SystemClock.elapsedRealtime() + 5_000;
        while (MediaPlayerService.isRunning() && SystemClock.elapsedRealtime() < end) {
            Thread.sleep(50);
        }
        return MediaPlayerService.isRunning();
    }

    private static ExoPlayer player(MediaPlayerService service) {
        ExoPlayer player = service.getPlayer();
        assertNotNull("player released", player);
        return player;
    }

    private static <T> T onMain(Callable<T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                result.set(callable.call());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return result.get();
    }

    private void await(String what, long timeoutMs, Callable<Boolean> condition) throws InterruptedException {
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < end) {
            if (Boolean.TRUE.equals(onMain(condition))) {
                return;
            }
            Thread.sleep(50);
        }
        fail("timed out waiting for " + what + "; requests=" + server.getRequestCount());
    }

    private static String describe(MediaPlayerService service) {
        return onMain(() -> {
            ExoPlayer p = service.getPlayer();
            PlaybackStateCompat session = service.getSessionPlaybackState();
            return p == null ? "no player" : "state=" + p.getPlaybackState() + " playWhenReady="
                    + p.getPlayWhenReady() + " playing=" + p.isPlaying() + " error=" + p.getPlayerError()
                    + " chapter=" + service.getChapterIndex() + " intent=" + service.isWantPlaying()
                    + " session=" + (session == null ? null : session.getState());
        });
    }
}
