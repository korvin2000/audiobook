package com.fanok.audiobooks.service;

import static android.support.v4.media.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_IMAGE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_PROGRESS;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_SELECTION;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SET_TITLE;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_EQUALIZER;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_SHOW_RATING;
import static com.fanok.audiobooks.activity.SleepTimerActivity.Broadcast_UPDATE_TIMER_START;
import static com.fanok.audiobooks.activity.SleepTimerActivity.bradcast_FinishTimer;
import static com.fanok.audiobooks.activity.SleepTimerActivity.bradcast_UpdateTimer;
import static com.fanok.audiobooks.android_equalizer.EqualizerFragment.Broadcast_EqualizerEnabled;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_Equalizer;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_NEXT;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_PREVIOUS;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SEEK_NEXT_30;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SEEK_PREVIOUS_10;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SEEK_TO;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SET_SPEED;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_SHOW_TITLE;
import static com.fanok.audiobooks.presenter.SleepTimerPresenter.Broadcast_SleepTimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.os.Binder;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem.ClippingConfiguration;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.Hourglass;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.BookActivity;
import com.fanok.audiobooks.activity.LoadBook;
import com.fanok.audiobooks.android_equalizer.EqualizerModel;
import com.fanok.audiobooks.android_equalizer.Settings;
import com.fanok.audiobooks.broadcasts.OnCancelBroadcastReceiver;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.AudioModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.fanok.audiobooks.presenter.SleepTimerPresenter;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.util.DeletionPlanner;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import com.fanok.audiobooks.util.DownloadUtil;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plays one book. The user's intent ({@link #wantPlaying}) is changed only by the commands {@link #play()},
 * {@link #pause()}, {@link #toggle()}, {@link #seekTo(long)}, {@link #setSpeed(float)}, {@link #next()},
 * {@link #previous()}, {@link #select(int, boolean)} and {@link #stop()}; notification actions, media buttons,
 * session callbacks and the app's broadcasts are thin adapters over them. One {@link ExoPlayer} lives as long as
 * the service; a chapter change only replaces its media source. Every replacement and stop bumps {@link #gen},
 * and asynchronous work started for an older generation is dropped.
 */
@OptIn(markerClass = UnstableApi.class)
public class MediaPlayerService extends MediaBrowserServiceCompat {

    public static final String MY_MEDIA_ROOT_ID = "Root";

    public static final String ACTION_START = "start";

    public static final String ACTION_PLAY = "audioBook.ACTION_PLAY";

    public static final String ACTION_PAUSE = "audioBook.ACTION_PAUSE";

    public static final String ACTION_PREVIOUS = "audioBook.ACTION_PREVIOUS";

    public static final String ACTION_REWIND = "audioBook.ACTION_REWIND";

    public static final String ACTION_FORWARD = "audioBook.ACTION_FAST_FORWARD";

    public static final String ACTION_NEXT = "audioBook.ACTION_NEXT";

    public static final String ACTION_STOP = "audioBook.ACTION_STOP";

    private static final String TAG = "MediaPlayerService";
    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "124";
    private static final String CHANNEL_NAME = "Player";
    public static final int countAudioWereShowingRatingPopUp = 15;
    private static final int REWIND = 30000;
    private static final int FAST_FORWARD = 30000;
    private static final long PROGRESS_INTERVAL_MS = 500;
    private static final long CHECKPOINT_INTERVAL_MS = 10_000;

    private static final long SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_SEEK_TO
            | PlaybackStateCompat.ACTION_REWIND
            | PlaybackStateCompat.ACTION_FAST_FORWARD
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_STOP;

    // Published for in-process readers; written on the main thread only.
    private static volatile boolean running;
    private static volatile boolean isPlay;
    private static volatile String currentBookUrl;

    private static final AtomicReference<Request> pendingRequest = new AtomicReference<>();

    private static volatile DataSource.Factory upstreamForTest;

    /** What to play. Handed to the service in-process by {@link #start}, never through preferences. */
    public static final class Request {
        @Nullable
        final BookPOJO book;
        @NonNull
        final String bookUrl;
        @NonNull
        final ArrayList<AudioPOJO> chapters;
        @Nullable
        final int[] ordinals;
        final int index;
        final long positionMs;
        final boolean play;

        /**
         * @param ordinals each chapter's index in the book's full chapter list when {@code chapters} is a subset
         *                 (offline copies), else null; checkpoints always record the full-list index
         */
        public Request(@Nullable BookPOJO book, @NonNull String bookUrl, @NonNull List<AudioPOJO> chapters,
                @Nullable int[] ordinals, int index, long positionMs, boolean play) {
            this.book = book;
            this.bookUrl = bookUrl;
            this.chapters = new ArrayList<>(chapters);
            this.ordinals = ordinals != null && ordinals.length == chapters.size() ? ordinals.clone() : null;
            this.index = index;
            this.positionMs = positionMs;
            this.play = play;
        }
    }

    private final IBinder mBinder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final PlaybackErrorPolicy errorPolicy = new PlaybackErrorPolicy();
    private final DefaultExtractorsFactory extractorsFactory =
            new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);

    private ExoPlayer player;
    private MediaSessionCompat mediaSession;
    private StorageUtil storage;
    private SharedPreferences mPreferences;
    private AudioDBModel mAudioDBModel;
    private boolean receiverRegistered;

    // the book
    @Nullable
    private BookPOJO book;
    @Nullable
    private String bookUrl;
    @Nullable
    private ArrayList<AudioPOJO> chapters;
    @Nullable
    private int[] ordinals;
    private int chapterIndex;
    @Nullable
    private PendingIntent contentIntent;
    private String imageUrl = "";
    private Bitmap image;
    private Target artworkTarget;
    private String artworkFailedUrl;

    // the state machine
    private boolean wantPlaying;
    private int gen;
    /** The chapter's source is being resolved on the executor. */
    private boolean resolving;
    /** A cold start is reading the last book from the database. */
    private boolean restoring;
    /** Where the chapter being loaded starts. */
    private long startPositionMs;
    /** The error being shown, or retried while {@link #retryTask}/{@link #refreshTask} is pending. */
    @Nullable
    private PlaybackErrorPolicy.Kind failedKind;
    @Nullable
    private Runnable retryTask;
    @Nullable
    private Disposable refreshTask;
    /** elapsedRealtime of the pause, 0 for "long ago" (a freshly loaded book), -1 while not paused. */
    private long pausedAt = -1;
    private long lastCheckpointAt;
    private int shownIcon;
    private boolean isForeground;
    @Nullable
    private Hourglass sleepTimer;

    private boolean mEqualizerEnabled = true;
    private Equalizer mEqualizer;
    private BassBoost bassBoost;
    private PresetReverb presetReverb;

    /** Starts (or reaches the running) service and makes it play {@code request}. */
    public static void start(@NonNull Context context, @NonNull Request request) {
        pendingRequest.set(request);
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, MediaPlayerService.class).setAction(ACTION_START));
        } catch (RuntimeException e) {
            pendingRequest.compareAndSet(request, null);
            throw e;
        }
    }

    /** Whether the user wants the loaded book to play (it may still be buffering). */
    public static boolean isPlay() {
        return isPlay;
    }

    public static boolean isRunning() {
        return running;
    }

    /** The book the service holds, or null. */
    @Nullable
    public static String currentBookUrl() {
        return currentBookUrl;
    }

    public static int getNotificationId() {
        return NOTIFICATION_ID;
    }

    /** Instrumentation tests serve fixtures from a loopback HTTPS server through their own upstream. */
    @VisibleForTesting
    public static void setUpstreamFactoryForTest(@Nullable DataSource.Factory factory) {
        upstreamForTest = factory;
    }

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onCommandBroadcast(intent);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                onChapterEnded();
                return;
            }
            if (playbackState == Player.STATE_READY) {
                //READY never changes the intent and never seeks: the start position went with the source
                errorPolicy.reset();
                failedKind = null;
                publishMetadata();
            }
            publishState();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                startProgress();
                if (sleepTimer != null) {
                    sleepTimer.resumeTimer();
                }
            } else {
                stopProgress();
                if (sleepTimer != null) {
                    sleepTimer.pauseTimer();
                }
                sendProgress();
            }
            publishState();
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (!playWhenReady && wantPlaying && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                //focus loss or unplugged headphones paused the player: from now on that is the intent
                wantPlaying = false;
                pausedAt = SystemClock.elapsedRealtime();
                checkpointNow(false);
            }
            publishState();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            handleError(error);
        }

        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
            attachEffects();
        }
    };

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            sendProgress();
            if (SystemClock.elapsedRealtime() - lastCheckpointAt >= CHECKPOINT_INTERVAL_MS) {
                checkpointNow(false);
            }
            handler.postDelayed(this, PROGRESS_INTERVAL_MS);
        }
    };

    private final Runnable notifyTask = this::postNotification;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        storage = new StorageUtil(getApplicationContext());
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mAudioDBModel = new AudioDBModel(this);
        createNotificationChannel();
        initMediaSession();
        initPlayer();
        registerCommandReceiver();
        publishState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //1. nothing started before this point may act any more; the final position is written synchronously
        gen++;
        running = false;
        checkpointNow(true);

        //2. timers, handlers, artwork
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        SleepTimerPresenter.setTimerStarted(false);
        cancelRetry();
        handler.removeCallbacksAndMessages(null);
        cancelArtwork();

        //3. effects, player, session
        stopEqualizer();
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "player.release failed", e);
            }
            player = null;
        }
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "mediaSession.release failed", e);
            }
            mediaSession = null;
        }

        //4. receivers
        if (receiverRegistered) {
            try {
                unregisterReceiver(commandReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "receiver was not registered", e);
            }
            receiverRegistered = false;
        }
        leaveForeground();
        removeNotification();
        isPlay = false;
        currentBookUrl = null;

        //5. the executor goes last; every submit checks isShutdown()
        dbExecutor.shutdown();
    }

    /**
     * Service lifecycle methods
     */
    @Override
    public IBinder onBind(Intent intent) {
        if (SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return mBinder;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull final String clientPackageName, final int clientUid,
            @Nullable final Bundle rootHints) {
        return new MediaBrowserServiceCompat.BrowserRoot(MY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId, @NonNull final Result<List<MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        if (MY_MEDIA_ROOT_ID.equals(parentId) && hasBook()) {
            for (int i = 0; i < chapters.size(); i++) {
                MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                        .setMediaId(Integer.toString(i))
                        .setTitle(chapters.get(i).getName())
                        .setSubtitle(bookName())
                        .build();
                mediaItems.add(new MediaBrowserCompat.MediaItem(description, FLAG_PLAYABLE));
            }
        }
        result.sendResult(mediaItems);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //startForegroundService() requires this within seconds, whatever the start reason
        if (!enterForeground()) {
            stop();
            return START_NOT_STICKY;
        }
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            Request request = pendingRequest.getAndSet(null);
            if (request != null) {
                apply(request);
            }
        } else if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
            KeyEvent event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent.class);
            if (event != null) {
                handleMediaKey(event);
            }
        } else if (action != null) {
            handleIncomingAction(action);
        }
        if (!hasBook() && !restoring) {
            //nothing to play and nothing on its way: never stay foreground on a placeholder
            stop();
        }
        return START_NOT_STICKY;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Commands: the only code that changes the intent or playWhenReady

    /** Plays the loaded book; after an error re-prepares the chapter, after the end replays the book. */
    public void play() {
        if (player == null) {
            return;
        }
        if (!hasBook()) {
            restoreBook(null, true);
            return;
        }
        wantPlaying = true;
        int state = player.getPlaybackState();
        if (!resolving && state == Player.STATE_ENDED && chapterIndex >= chapters.size() - 1) {
            //the finished book starts over
            pausedAt = -1;
            loadChapter(0, 0, true);
            return;
        }
        if (!resolving && pausedAt >= 0 && state != Player.STATE_IDLE) {
            long position = player.getCurrentPosition();
            long rewound = rewoundAfterPause(position, pausedAt);
            if (rewound != position) {
                player.seekTo(rewound);
            }
        }
        pausedAt = -1;
        if (!resolving && state == Player.STATE_IDLE) {
            //after an error, or a retry still waiting: the user asked to try now
            cancelRetry();
            errorPolicy.reset();
            failedKind = null;
            if (player.getMediaItemCount() == 0) {
                loadChapter(chapterIndex, startPositionMs, true);
                return;
            }
            player.prepare();
        }
        player.setPlayWhenReady(true);
        publishState();
    }

    /** Pauses in every state; pending retries and source resolution can no longer start audio. */
    public void pause() {
        if (player == null) {
            return;
        }
        wantPlaying = false;
        cancelRetry();
        if (pausedAt < 0) {
            pausedAt = SystemClock.elapsedRealtime();
        }
        player.setPlayWhenReady(false);
        checkpointNow(false);
        publishState();
    }

    /** The only toggling command: decided by the intent, not by what the engine is doing. */
    public void toggle() {
        if (wantPlaying) {
            pause();
        } else {
            play();
        }
    }

    /** Seeks within the chapter, clamped to it; never changes the intent. */
    public void seekTo(long positionMs) {
        if (player == null || !hasBook() || resolving) {
            return;
        }
        if (!player.getCurrentTimeline().isEmpty() && !player.isCurrentMediaItemSeekable()) {
            return;
        }
        long target = Math.max(0, positionMs);
        long duration = player.getDuration();
        if (duration != C.TIME_UNSET) {
            target = Math.min(target, duration);
        }
        player.seekTo(target);
        //an explicit position is not rewound after the pause
        pausedAt = -1;
        sendProgress();
        publishState();
    }

    private void seekBy(long deltaMs) {
        if (player != null) {
            seekTo(player.getCurrentPosition() + deltaMs);
        }
    }

    /** Changes the speed only; a paused player stays paused. */
    public void setSpeed(float speed) {
        if (player == null) {
            return;
        }
        player.setPlaybackParameters(new PlaybackParameters(PlaybackSettings.clampSpeed(speed)));
        publishState();
    }

    /** The next chapter with the current intent; skips a chapter that failed. */
    public void next() {
        if (player != null && hasBook() && chapterIndex < chapters.size() - 1) {
            loadChapter(chapterIndex + 1, 0, true);
        }
    }

    /** The previous chapter with the current intent; on the first chapter restarts it. */
    public void previous() {
        if (player == null || !hasBook()) {
            return;
        }
        if (chapterIndex > 0) {
            loadChapter(chapterIndex - 1, 0, true);
        } else {
            seekTo(0);
        }
    }

    /** A chapter chosen from the list, with an explicit intent. */
    public void select(int index, boolean play) {
        if (player == null || !hasBook() || index < 0 || index >= chapters.size()) {
            return;
        }
        wantPlaying = play;
        pausedAt = play ? -1 : 0;
        loadChapter(index, 0, true);
    }

    /** Ends playback: checkpoint, release the media, remove the notification, stop the service. */
    public void stop() {
        wantPlaying = false;
        cancelRetry();
        checkpointNow(false);
        gen++;
        if (player != null) {
            player.setPlayWhenReady(false);
            player.stop();
            player.clearMediaItems();
        }
        stopProgress();
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
            SleepTimerPresenter.setTimerStarted(false);
            sendInternal(new Intent(bradcast_FinishTimer));
        }
        resolving = false;
        restoring = false;
        failedKind = null;
        book = null;
        bookUrl = null;
        chapters = null;
        ordinals = null;
        contentIntent = null;
        currentBookUrl = null;
        cancelArtwork();
        image = null;
        imageUrl = "";
        leaveForeground();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            publishMetadata();
        }
        publishState();
        removeNotification();
        notifyChildrenChanged(MY_MEDIA_ROOT_ID);
        stopSelf();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Test accessors (main thread)

    @VisibleForTesting
    @Nullable
    public ExoPlayer getPlayer() {
        return player;
    }

    public int getChapterIndex() {
        return chapterIndex;
    }

    public boolean isWantPlaying() {
        return wantPlaying;
    }

    @VisibleForTesting
    @Nullable
    public PlaybackStateCompat getSessionPlaybackState() {
        return mediaSession != null ? mediaSession.getController().getPlaybackState() : null;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Loading

    private boolean hasBook() {
        return bookUrl != null && chapters != null && !chapters.isEmpty();
    }

    private void apply(@NonNull Request request) {
        if (request.chapters.isEmpty() || player == null) {
            return;
        }
        //the outgoing book keeps where it was left
        checkpointNow(false);
        book = request.book;
        bookUrl = request.bookUrl;
        chapters = request.chapters;
        ordinals = request.ordinals;
        currentBookUrl = bookUrl;
        contentIntent = buildContentIntent();
        String photo = book != null && book.getPhoto() != null ? book.getPhoto() : "";
        if (!photo.equals(imageUrl)) {
            cancelArtwork();
            imageUrl = photo;
            image = null;
        }
        wantPlaying = request.play;
        long position = request.positionMs;
        if (request.play) {
            position = rewoundAfterPause(position, 0);
            pausedAt = -1;
        } else {
            pausedAt = 0;
        }
        int index = Math.max(0, Math.min(request.index, chapters.size() - 1));
        if (mediaSession != null) {
            mediaSession.setActive(true);
        }
        if (!isForeground) {
            //a browser client bound the service and asked to play: become a started foreground service
            try {
                ContextCompat.startForegroundService(this, new Intent(this, MediaPlayerService.class));
            } catch (RuntimeException e) {
                Log.w(TAG, "cannot start in the foreground", e);
            }
        }
        loadChapter(index, position, true);
        notifyChildrenChanged(MY_MEDIA_ROOT_ID);
    }

    /**
     * Replaces the chapter. The source is resolved (downloads, cache) on the executor and built on this thread;
     * the player and the foreground state stay as they are.
     */
    private void loadChapter(int index, long positionMs, boolean freshChapter) {
        final int generation = ++gen;
        cancelRetry();
        if (freshChapter) {
            errorPolicy.reset();
        }
        failedKind = null;
        chapterIndex = index;
        startPositionMs = Math.max(0, positionMs);
        final AudioPOJO audio = chapters.get(index);
        resolving = true;
        //the previous chapter must not go on while this one resolves
        player.stop();
        checkpointNow(false);
        if (freshChapter) {
            recordChapter(audio);
        }
        publishMetadata();
        publishState();
        sendChapterToUi();

        if (!PlaybackErrorPolicy.isValidClip(audio.getTimeStart(), audio.getTimeFinish())) {
            resolving = false;
            failedKind = PlaybackErrorPolicy.Kind.CONTENT;
            publishState();
            return;
        }
        final String book = bookUrl;
        final ArrayList<AudioPOJO> playlist = new ArrayList<>(chapters);
        final Context app = getApplicationContext();
        boolean queued = execDb(() -> {
            //download lookup and database reads stay off the main thread
            DownloadUtil.Playback playback = null;
            try {
                playback = DownloadUtil.playbackFor(app, book, playlist, index);
            } catch (RuntimeException e) {
                Log.e(TAG, "Cannot resolve the chapter source", e);
            }
            final DownloadUtil.Playback resolved = playback;
            handler.post(() -> onSourceResolved(generation, audio, resolved));
        });
        if (!queued) {
            resolving = false;
        }
    }

    private void onSourceResolved(int generation, @NonNull AudioPOJO audio, @Nullable DownloadUtil.Playback playback) {
        if (generation != gen || player == null) {
            return;
        }
        resolving = false;
        if (playback == null) {
            failedKind = PlaybackErrorPolicy.Kind.OTHER;
            publishState();
            return;
        }
        DataSource.Factory dataSourceFactory = playback.dataSourceFactory;
        if (upstreamForTest != null && DeletionPlanner.isRemote(audio.getUrl())) {
            dataSourceFactory = upstreamForTest;
        }
        androidx.media3.common.MediaItem.Builder item = playback.mediaItem.setMediaId(Integer.toString(chapterIndex));
        if (audio.getTimeStart() >= 0 && audio.getTimeFinish() >= 0) {
            item.setClippingConfiguration(new ClippingConfiguration.Builder()
                    .setStartPositionMs(audio.getTimeStart() * 1000L)
                    .setEndPositionMs(audio.getTimeFinish() * 1000L)
                    .build());
        }
        MediaSource mediaSource = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                .createMediaSource(item.build());
        player.setMediaSource(mediaSource, startPositionMs);
        player.setPlayWhenReady(wantPlaying);
        player.prepare();
        publishState();
    }

    /** A cold start (media button, browser client) plays the last book, or the given known one, from the database. */
    private void restoreBook(@Nullable String requestedUrl, final boolean play) {
        Checkpoint last = storage.loadLastCheckpoint();
        final String url = requestedUrl != null ? requestedUrl : last != null ? last.bookUrl : null;
        if (url == null) {
            publishState();
            return;
        }
        final int generation = gen;
        final Context app = getApplicationContext();
        restoring = true;
        boolean queued = execDb(() -> {
            final ArrayList<AudioPOJO> list = new ArrayList<>();
            BookPOJO found = null;
            try {
                for (BooksAudioEntity row : new AudioListDBModel(app).getRows(url)) {
                    String local = DownloadStorageHelper.localCopyOf(app, row);
                    AudioPOJO audio = new AudioPOJO();
                    audio.setName(row.nameAudio);
                    audio.setBookName(row.booksName);
                    audio.setUrl(local != null ? local : row.urlAudio);
                    audio.setTime(row.time);
                    audio.setTimeStart(row.timeStart);
                    audio.setTimeFinish(row.timeEnd);
                    list.add(audio);
                }
                found = new BooksDBModel(app).getSomewhere(url);
            } catch (RuntimeException e) {
                Log.w(TAG, "restoreBook: cannot read the book", e);
            }
            final BookPOJO restored = found;
            handler.post(() -> {
                restoring = false;
                if (generation != gen || player == null) {
                    return;
                }
                if (list.isEmpty()) {
                    if (hasBook()) {
                        publishState();
                    } else {
                        stop();
                    }
                    return;
                }
                Checkpoint checkpoint = storage.loadCheckpoint(url);
                Checkpoint at = checkpoint != null ? checkpoint.clampTo(list.size()) : new Checkpoint(url, 0, 0);
                apply(new Request(restored, url, list, null, at.chapterIndex, at.positionMs, play));
            });
        });
        if (!queued) {
            restoring = false;
        }
    }

    private void playFromMediaId(@Nullable String mediaId) {
        int index = parseMediaIndex(mediaId);
        if (index >= 0) {
            select(index, true);
        } else if (mediaId != null && (mediaId.startsWith("https://") || mediaId.startsWith("http://"))) {
            //a book this app knows: restoreBook ignores URLs without chapters in the database
            if (mediaId.equals(bookUrl)) {
                play();
            } else {
                restoreBook(mediaId, true);
            }
        } else {
            Log.w(TAG, "onPlayFromMediaId: ignoring invalid media id");
        }
    }

    /** @return the chapter index for a client-supplied media id, or -1 when it is not one this service issued. */
    private int parseMediaIndex(@Nullable String mediaId) {
        if (mediaId == null || mediaId.isEmpty() || !hasBook()) {
            return -1;
        }
        try {
            int index = Integer.parseInt(mediaId);
            return index >= 0 && index < chapters.size() ? index : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Engine events

    private void onChapterEnded() {
        if (resolving || !hasBook()) {
            publishState();
            return;
        }
        //an armed end-of-chapter sleep timer wins over auto-advance
        boolean sleepNow = sleepTimer != null && sleepTimer.chapterEnded();
        if (chapterIndex < chapters.size() - 1) {
            if (sleepNow) {
                wantPlaying = false;
                pausedAt = SystemClock.elapsedRealtime();
            }
            loadChapter(chapterIndex + 1, 0, true);
        } else {
            //end of the book: paused at the end, Play replays it
            wantPlaying = false;
            pausedAt = SystemClock.elapsedRealtime();
            player.setPlayWhenReady(false);
            checkpointNow(false);
            publishState();
        }
    }

    private void handleError(@NonNull PlaybackException error) {
        Log.w(TAG, "onPlayerError: " + error.getErrorCodeName(), error);
        cancelRetry();
        checkpointNow(false);
        PlaybackErrorPolicy.Decision decision = errorPolicy.onError(error.errorCode, httpStatusOf(error));
        failedKind = decision.kind;
        if (wantPlaying && decision.action == PlaybackErrorPolicy.Action.RETRY) {
            scheduleRetry(decision.delayMs);
        } else if (wantPlaying && decision.action == PlaybackErrorPolicy.Action.REFRESH && ordinals == null) {
            refreshChapters();
        }
        publishState();
    }

    private static int httpStatusOf(@NonNull Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) t).responseCode;
            }
        }
        return -1;
    }

    /** Re-prepares the chapter at the checkpoint position (the player keeps it) unless the intent changed. */
    private void scheduleRetry(long delayMs) {
        final int generation = gen;
        retryTask = new Runnable() {
            @Override
            public void run() {
                if (retryTask != this) {
                    return;
                }
                retryTask = null;
                if (generation == gen && player != null && wantPlaying) {
                    player.prepare();
                }
                publishState();
            }
        };
        handler.postDelayed(retryTask, delayMs);
    }

    /** The chapter address was refused: fetch the chapter list once more, quietly, and retry with it. */
    private void refreshChapters() {
        final int generation = gen;
        final String url = bookUrl;
        final int index = chapterIndex;
        final int count = chapters.size();
        final long position = player.getCurrentPosition();
        refreshTask = new AudioModel(getApplicationContext()).getAudio(url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(fresh -> {
                    refreshTask = null;
                    if (generation != gen || !wantPlaying || !hasBook() || fresh.size() != count) {
                        publishState();
                        return;
                    }
                    chapters = new ArrayList<>(fresh);
                    loadChapter(index, position, false);
                }, e -> {
                    refreshTask = null;
                    Log.w(TAG, "chapter list refresh failed", e);
                    publishState();
                });
    }

    private void cancelRetry() {
        if (retryTask != null) {
            handler.removeCallbacks(retryTask);
            retryTask = null;
        }
        if (refreshTask != null) {
            refreshTask.dispose();
            refreshTask = null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Checkpoints

    private int ordinalOf(int index) {
        return ordinals != null && index < ordinals.length ? ordinals[index] : index;
    }

    /** An immutable snapshot of where the book is, or null when nothing is loaded. */
    @Nullable
    private Checkpoint snapshot() {
        if (!hasBook() || player == null) {
            return null;
        }
        long position = resolving || player.getMediaItemCount() == 0
                ? startPositionMs : player.getCurrentPosition();
        return new Checkpoint(bookUrl, ordinalOf(chapterIndex), position);
    }

    private void checkpointNow(boolean synchronous) {
        Checkpoint checkpoint = snapshot();
        if (checkpoint == null) {
            return;
        }
        storage.storeCheckpoint(checkpoint, synchronous);
        lastCheckpointAt = SystemClock.elapsedRealtime();
        //legacy book-level seconds, written for the book lists only
        final String url = checkpoint.bookUrl;
        final int seconds = (int) (checkpoint.positionMs / 1000);
        execDb(() -> {
            try {
                mAudioDBModel.setTime(url, seconds);
            } catch (RuntimeException e) {
                Log.e(TAG, "DB setTime error", e);
            }
        });
    }

    /** Legacy last-chapter title of the book, and the rating prompt counter, once per chapter played. */
    private void recordChapter(@NonNull AudioPOJO audio) {
        final String url = bookUrl;
        final String name = audio.getName() != null ? audio.getName() : "";
        final int seconds = (int) (startPositionMs / 1000);
        execDb(() -> {
            try {
                mAudioDBModel.add(url, name);
                mAudioDBModel.setTime(url, seconds);
            } catch (RuntimeException e) {
                Log.e(TAG, "DB addLastBook error", e);
            }
        });
        if (storage.loadShowRating()) {
            int count = storage.loadCountAudioListneredForRating() + 1;
            if (count >= countAudioWereShowingRatingPopUp) {
                storage.storeCountAudioListneredForRating(0);
                sendInternal(new Intent(Broadcast_SHOW_RATING));
            } else {
                storage.storeCountAudioListneredForRating(count);
            }
        }
    }

    /** The "rewind after a long pause" preference: 10 s after 10 s of pause, 30 s after 30 s. */
    private long rewoundAfterPause(long position, long pausedSince) {
        if (!mPreferences.getBoolean("rewind_back", false) || pausedSince < 0) {
            return position;
        }
        long paused = pausedSince == 0 ? Long.MAX_VALUE : SystemClock.elapsedRealtime() - pausedSince;
        if (paused < 10_000) {
            return position;
        }
        return Math.max(0, position - (paused < 30_000 ? 10_000 : 30_000));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Published state: session, notification, UI broadcasts

    private void publishState() {
        isPlay = hasBook() && wantPlaying;
        if (mediaSession != null) {
            mediaSession.setPlaybackState(buildPlaybackState());
        }
        int icon = wantPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        if (icon != shownIcon) {
            shownIcon = icon;
            sendPlayIcon();
        }
        handler.removeCallbacks(notifyTask);
        handler.post(notifyTask);
    }

    private boolean isWaiting() {
        return resolving || restoring || retryTask != null || refreshTask != null;
    }

    private boolean isShowingError() {
        return failedKind != null && !isWaiting() && player != null
                && player.getPlaybackState() == Player.STATE_IDLE;
    }

    @NonNull
    private PlaybackStateCompat buildPlaybackState() {
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder().setActions(SESSION_ACTIONS);
        int state;
        long position = 0;
        float speed = 0f;
        if (!hasBook() || player == null) {
            state = restoring ? PlaybackStateCompat.STATE_CONNECTING : PlaybackStateCompat.STATE_NONE;
        } else {
            position = resolving ? startPositionMs : player.getCurrentPosition();
            builder.setActiveQueueItemId(chapterIndex);
            if (isShowingError()) {
                state = PlaybackStateCompat.STATE_ERROR;
                builder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, getString(errorMessage()));
            } else if (player.isPlaying()) {
                state = PlaybackStateCompat.STATE_PLAYING;
                speed = player.getPlaybackParameters().speed;
            } else if (!wantPlaying) {
                state = PlaybackStateCompat.STATE_PAUSED;
            } else if (isWaiting() || player.getPlaybackState() != Player.STATE_READY) {
                state = PlaybackStateCompat.STATE_BUFFERING;
            } else {
                //ready but silenced for a moment (transient focus loss)
                state = PlaybackStateCompat.STATE_PAUSED;
            }
        }
        builder.setState(state, position, speed, SystemClock.elapsedRealtime());
        return builder.build();
    }

    private int errorMessage() {
        return failedKind == PlaybackErrorPolicy.Kind.CONTENT
                ? R.string.playback_error_skip : R.string.playback_error_retry;
    }

    @NonNull
    private String bookName() {
        if (book != null && book.getName() != null && !book.getName().isEmpty()) {
            return book.getName();
        }
        if (hasBook() && chapters.get(chapterIndex).getBookName() != null) {
            return chapters.get(chapterIndex).getBookName();
        }
        return "";
    }

    private void publishMetadata() {
        if (mediaSession == null) {
            return;
        }
        if (!hasBook()) {
            mediaSession.setMetadata(null);
            return;
        }
        AudioPOJO audio = chapters.get(chapterIndex);
        MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, Integer.toString(chapterIndex))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, audio.getName())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, audio.getName())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, bookName())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, bookName())
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, chapterIndex + 1)
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, chapters.size());
        if (book != null) {
            if (book.getAutor() != null) {
                metadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, book.getAutor());
                metadata.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, book.getAutor());
            }
            if (book.getArtist() != null) {
                //the reader
                metadata.putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, book.getArtist());
                metadata.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, book.getArtist());
            }
        }
        if (!imageUrl.isEmpty()) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, imageUrl);
        }
        if (image != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, image);
        }
        if (!resolving && player != null && player.getPlaybackState() == Player.STATE_READY
                && player.getDuration() != C.TIME_UNSET) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getDuration());
        }
        mediaSession.setMetadata(metadata.build());
    }

    private void sendInternal(Intent intent) {
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void sendPlayIcon() {
        sendInternal(new Intent(Broadcast_SET_IMAGE).putExtra("id", wantPlaying ? R.drawable.ic_pause : R.drawable.ic_play));
    }

    private void sendProgress() {
        if (player == null || !hasBook()) {
            return;
        }
        long duration = player.getDuration();
        Intent intent = new Intent(Broadcast_SET_PROGRESS);
        intent.putExtra("timeCurrent", (int) player.getCurrentPosition());
        intent.putExtra("timeEnd", duration == C.TIME_UNSET ? 0 : (int) duration);
        intent.putExtra("buffered", (int) player.getBufferedPosition());
        sendInternal(intent);
    }

    private void sendChapterToUi() {
        if (!hasBook()) {
            return;
        }
        String name = chapters.get(chapterIndex).getName();
        sendInternal(new Intent(Broadcast_SET_SELECTION).putExtra("postion", chapterIndex).putExtra("name", name));
        sendInternal(new Intent(Broadcast_SET_TITLE).putExtra("title", name));
    }

    private void startProgress() {
        handler.removeCallbacks(progressTick);
        lastCheckpointAt = SystemClock.elapsedRealtime();
        handler.post(progressTick);
    }

    private void stopProgress() {
        handler.removeCallbacks(progressTick);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Notification

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /** @return false when the platform refused: the service must not go on as a background one */
    private boolean enterForeground() {
        try {
            Notification notification = buildNotification();
            if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            isForeground = true;
            return true;
        } catch (IllegalStateException e) {
            Log.e(TAG, "startForeground refused", e);
            return false;
        }
    }

    private void leaveForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        isForeground = false;
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    /** Updates the foreground notification from the current state; chapter changes never touch foreground. */
    private void postNotification() {
        if (!isForeground) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
        loadArtwork();
    }

    @NonNull
    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT);
        if (!hasBook()) {
            return builder.setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.loading))
                    .setOngoing(true)
                    .build();
        }
        boolean showPause = wantPlaying;
        builder.setContentTitle(chapters.get(chapterIndex).getName())
                .setContentText(isShowingError() ? getString(errorMessage()) : bookName())
                .setLargeIcon(image)
                .setOngoing(showPause)
                .setContentIntent(contentIntent)
                .setDeleteIntent(PendingIntent.getBroadcast(getApplicationContext(), 0,
                        new Intent(this, OnCancelBroadcastReceiver.class), PendingIntent.FLAG_IMMUTABLE))
                .addAction(R.drawable.ic_notification_rewind_30, getString(R.string.playback_rewind_30),
                        playbackAction(ACTION_REWIND, 4))
                .addAction(R.drawable.ic_notification_previous, getString(R.string.previous),
                        playbackAction(ACTION_PREVIOUS, 3))
                .addAction(showPause ? R.drawable.ic_notification_pause : R.drawable.ic_notification_play,
                        getString(showPause ? R.string.pause : R.string.play),
                        showPause ? playbackAction(ACTION_PAUSE, 1) : playbackAction(ACTION_PLAY, 0))
                .addAction(R.drawable.ic_notification_next, getString(R.string.next),
                        playbackAction(ACTION_NEXT, 2))
                .addAction(android.R.drawable.ic_delete, getString(R.string.stop),
                        playbackAction(ACTION_STOP, 6));
        if (book != null && book.getAutor() != null) {
            builder.setSubText(book.getAutor());
        }
        androidx.media.app.NotificationCompat.MediaStyle style =
                new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(1, 2, 3);
        if (mediaSession != null) {
            style.setMediaSession(mediaSession.getSessionToken());
        }
        return builder.setStyle(style).build();
    }

    /** Opens the book screen from the saved book itself; only an unknown book goes through the page loader. */
    @Nullable
    private PendingIntent buildContentIntent() {
        Intent intent;
        if (book != null && book.getUrl() != null && !book.getUrl().isEmpty()) {
            intent = BookActivity.notificationIntent(this, book);
        } else if (bookUrl != null) {
            intent = new Intent(this, LoadBook.class)
                    .putExtra("url", bookUrl)
                    .putExtra("notificationClick", true);
        } else {
            return null;
        }
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent playbackAction(String action, int requestCode) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, playbackAction, PendingIntent.FLAG_IMMUTABLE);
    }

    /** One strong target per cover; a result for a cover that is no longer shown is dropped. */
    private void loadArtwork() {
        if (image != null || imageUrl.isEmpty() || artworkTarget != null || imageUrl.equals(artworkFailedUrl)) {
            return;
        }
        final String url = imageUrl;
        artworkTarget = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                if (artworkTarget != this) {
                    return;
                }
                artworkTarget = null;
                if (url.equals(imageUrl)) {
                    image = bitmap;
                    publishMetadata();
                    handler.removeCallbacks(notifyTask);
                    handler.post(notifyTask);
                }
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                if (artworkTarget == this) {
                    artworkTarget = null;
                    artworkFailedUrl = url;
                }
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
            }
        };
        try {
            Picasso.get().load(url).into(artworkTarget);
        } catch (RuntimeException e) {
            Log.w(TAG, "artwork", e);
            artworkTarget = null;
            artworkFailedUrl = url;
        }
    }

    private void cancelArtwork() {
        if (artworkTarget != null) {
            Picasso.get().cancelRequest(artworkTarget);
            artworkTarget = null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Adapters: session, media buttons, notification actions, in-app broadcasts

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, getApplicationContext(),
                MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(getApplicationContext(), 0,
                mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE));
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                KeyEvent event = IntentCompat.getParcelableExtra(mediaButtonEvent, Intent.EXTRA_KEY_EVENT,
                        KeyEvent.class);
                if (event != null && handleMediaKey(event)) {
                    return true;
                }
                return super.onMediaButtonEvent(mediaButtonEvent);
            }

            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                previous();
            }

            @Override
            public void onStop() {
                stop();
            }

            @Override
            public void onSeekTo(long position) {
                seekTo(position);
            }

            @Override
            public void onRewind() {
                seekBy(-REWIND);
            }

            @Override
            public void onFastForward() {
                seekBy(FAST_FORWARD);
            }

            @Override
            public void onPlayFromMediaId(final String mediaId, final Bundle extras) {
                playFromMediaId(mediaId);
            }
        });
        setSessionToken(mediaSession.getSessionToken());
    }

    /** PLAY → play, PAUSE → pause, PLAY_PAUSE and the headset button → toggle. */
    private boolean handleMediaKey(@NonNull KeyEvent event) {
        int code = event.getKeyCode();
        boolean known = code == KeyEvent.KEYCODE_MEDIA_PLAY || code == KeyEvent.KEYCODE_MEDIA_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_HEADSETHOOK
                || code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || code == KeyEvent.KEYCODE_MEDIA_STOP || code == KeyEvent.KEYCODE_MEDIA_REWIND
                || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        if (!known) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                play();
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pause();
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                next();
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                previous();
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                stop();
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-REWIND);
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(FAST_FORWARD);
                break;
            default:
                toggle();
        }
        return true;
    }

    /** Notification actions. */
    private void handleIncomingAction(@NonNull String action) {
        switch (action) {
            case ACTION_PLAY:
                play();
                break;
            case ACTION_PAUSE:
                pause();
                break;
            case ACTION_NEXT:
                next();
                break;
            case ACTION_PREVIOUS:
                previous();
                break;
            case ACTION_STOP:
                stop();
                break;
            case ACTION_REWIND:
                seekBy(-REWIND);
                break;
            case ACTION_FORWARD:
                seekBy(FAST_FORWARD);
                break;
            default:
        }
    }

    /** App-internal actions are never exported: other apps must not be able to drive the player. */
    private void registerCommandReceiver() {
        IntentFilter filter = new IntentFilter();
        for (String action : new String[]{Broadcast_PLAY, Broadcast_PLAY_NEXT, Broadcast_PLAY_PREVIOUS,
                Broadcast_SEEK_TO, Broadcast_SEEK_NEXT_30, Broadcast_SEEK_PREVIOUS_10, Broadcast_SET_SPEED,
                Broadcast_SHOW_TITLE, Broadcast_SleepTimer, Broadcast_UPDATE_TIMER_START, Broadcast_Equalizer,
                Broadcast_EqualizerEnabled}) {
            filter.addAction(action);
        }
        ContextCompat.registerReceiver(this, commandReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void onCommandBroadcast(@NonNull Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case Broadcast_PLAY:
                toggle();
                break;
            case Broadcast_PLAY_NEXT:
                next();
                break;
            case Broadcast_PLAY_PREVIOUS:
                previous();
                break;
            case Broadcast_SEEK_TO:
                seekTo(intent.getIntExtra("postion", 0));
                playIfSeekUnpauses();
                break;
            case Broadcast_SEEK_NEXT_30:
                seekBy(FAST_FORWARD);
                playIfSeekUnpauses();
                break;
            case Broadcast_SEEK_PREVIOUS_10:
                seekBy(-REWIND);
                playIfSeekUnpauses();
                break;
            case Broadcast_SET_SPEED:
                setSpeed(intent.getFloatExtra("speed", BookPresenter.getSpeed()));
                break;
            case Broadcast_SHOW_TITLE:
                sendChapterToUi();
                sendPlayIcon();
                sendProgress();
                break;
            case Broadcast_SleepTimer:
                onSleepTimerCommand(intent);
                break;
            case Broadcast_UPDATE_TIMER_START:
                if (sleepTimer != null) {
                    sendInternal(new Intent(bradcast_UpdateTimer).putExtra("time", sleepTimer.getTimeRemaining()));
                }
                break;
            case Broadcast_Equalizer:
                Intent reply = new Intent(Broadcast_SHOW_EQUALIZER);
                if (player != null) {
                    reply.putExtra("id", player.getAudioSessionId());
                }
                sendInternal(reply);
                break;
            case Broadcast_EqualizerEnabled:
                mEqualizerEnabled = intent.getBooleanExtra("enabled", false);
                if (!mEqualizerEnabled) {
                    stopEqualizer();
                } else {
                    storage.loadEqualizerSettings();
                    startEqualizer();
                }
                break;
            default:
        }
    }

    /** The "unpause while seeking" preference: an explicit play() after the seek, not part of seeking. */
    private void playIfSeekUnpauses() {
        if (!wantPlaying && hasBook() && mPreferences.getBoolean("seekToPausePref", true)) {
            play();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Sleep timer

    private void onSleepTimerCommand(@NonNull Intent intent) {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        if (!intent.getBooleanExtra("start", false)) {
            return;
        }
        boolean endOfChapter = intent.getBooleanExtra("endOfChapter", false);
        Hourglass timer = new Hourglass(intent.getLongExtra("time", 0), endOfChapter) {
            @Override
            public void onTimerFinish() {
                if (sleepTimer == this) {
                    sleepTimer = null;
                }
                SleepTimerPresenter.setTimerStarted(false);
                sendInternal(new Intent(bradcast_FinishTimer));
                if (!isEndOfChapter()) {
                    stop();
                }
            }

            @Override
            public void onTimerTick(final long timeRemaining) {
                sendInternal(new Intent(bradcast_UpdateTimer).putExtra("time", timeRemaining));
            }

            @Override
            protected long boundaryRemainingMs() {
                return chapterRemainingMs();
            }
        };
        sleepTimer = timer;
        timer.startTimer();
        if (player == null || !player.isPlaying()) {
            timer.pauseTimer();
        }
        sendInternal(new Intent(bradcast_UpdateTimer).putExtra("time", timer.getTimeRemaining()));
    }

    /** Media time left in the chapter at the current speed. */
    private long chapterRemainingMs() {
        if (player == null || resolving) {
            return 0;
        }
        long duration = player.getDuration();
        if (duration == C.TIME_UNSET) {
            return 0;
        }
        float speed = player.getPlaybackParameters().speed;
        return (long) (Math.max(0, duration - player.getCurrentPosition()) / speed);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Player and effects

    private static float currentSpeed() {
        return PlaybackSettings.clampSpeed(BookPresenter.getSpeed());
    }

    private void initPlayer() {
        PlaybackSettings.BufferDurations buffer = PlaybackSettings.bufferDurations(
                mPreferences.getString("bufferSize", String.valueOf(PlaybackSettings.DEFAULT_BUFFER_SECONDS)));
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(buffer.minBufferMs, buffer.maxBufferMs,
                        buffer.bufferForPlaybackMs, buffer.bufferForPlaybackAfterRebufferMs)
                .setBackBuffer(PlaybackSettings.BACK_BUFFER_MS, true)
                .build();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build();
        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setTrackSelector(new DefaultTrackSelector(this))
                //ExoPlayer owns audio focus and becoming-noisy; both change audibility, onPlayWhenReadyChanged syncs intent
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.addListener(playerListener);
        player.setPlaybackParameters(new PlaybackParameters(currentSpeed()));
        attachEffects();
    }

    /** Effects belong to the audio session, which outlives chapter changes: attached once and on a new session. */
    private void attachEffects() {
        if (!mEqualizerEnabled) {
            return;
        }
        storage.loadEqualizerSettings();
        if (Settings.isEqualizerEnabled) {
            startEqualizer();
        } else {
            stopEqualizer();
        }
    }

    private void startEqualizer() {
        if (player == null) return;
        stopEqualizer();
        int audioSesionId = player.getAudioSessionId();
        if (audioSesionId > 0) {
            if (Settings.equalizerModel == null) {
                Settings.equalizerModel = new EqualizerModel();
                Settings.equalizerModel.setReverbPreset(PresetReverb.PRESET_NONE);
                Settings.equalizerModel.setBassStrength((short) (1000 / 19));
            }

            try {
                mEqualizer = new Equalizer(0, audioSesionId);

                bassBoost = new BassBoost(0, audioSesionId);
                bassBoost.setEnabled(Settings.isEqualizerEnabled);
                BassBoost.Settings bassBoostSettingTemp = bassBoost.getProperties();
                BassBoost.Settings bassBoostSetting = new BassBoost.Settings(
                        bassBoostSettingTemp.toString());
                bassBoostSetting.strength = Settings.equalizerModel.getBassStrength();
                bassBoost.setProperties(bassBoostSetting);

                presetReverb = new PresetReverb(0, audioSesionId);
                presetReverb.setPreset(Settings.equalizerModel.getReverbPreset());
                presetReverb.setEnabled(Settings.isEqualizerEnabled);

                mEqualizer.setEnabled(Settings.isEqualizerEnabled);

                if (Settings.presetPos == 0) {
                    for (short bandIdx = 0; bandIdx < mEqualizer.getNumberOfBands(); bandIdx++) {
                        mEqualizer.setBandLevel(bandIdx, (short) Settings.seekbarpos[bandIdx]);
                    }
                } else {
                    mEqualizer.usePreset((short) Settings.presetPos);
                }
            } catch (RuntimeException e) {
                stopEqualizer();
            }
        }
    }

    private void stopEqualizer() {
        if (mEqualizer != null) {
            mEqualizer.release();
            mEqualizer = null;
        }

        if (bassBoost != null) {
            bassBoost.release();
            bassBoost = null;
        }

        if (presetReverb != null) {
            presetReverb.release();
            presetReverb = null;
        }
    }

    private boolean execDb(Runnable task) {
        if (dbExecutor.isShutdown()) {
            return false;
        }
        try {
            dbExecutor.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return false;
        }
    }

    /**
     * Service Binder
     */
    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MediaPlayerService.this;
        }
    }

}
