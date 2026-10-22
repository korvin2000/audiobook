package com.fanok.audiobooks.presenter;

import static com.fanok.audiobooks.Consts.getAttributeColor;
import static com.fanok.audiobooks.Consts.isServiceRunning;
import static com.fanok.audiobooks.activity.BookActivity.Broadcast_PLAY_NEW_AUDIO;
import static com.fanok.audiobooks.util.DownloadUtil.getDownloadManager;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.preference.PreferenceManager;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.EncodingExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.activity.SleepTimerActivity;
import com.fanok.audiobooks.interface_pacatge.book_content.Activity;
import com.fanok.audiobooks.interface_pacatge.book_content.ActivityPresenter;
import com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece;
import com.fanok.audiobooks.model.AudioDBModel;
import com.fanok.audiobooks.model.AudioListDBModel;
import com.fanok.audiobooks.model.AudioModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.OtherSourceModel;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.service.ExoDownloadService;
import com.fanok.audiobooks.service.MediaPlayerService;
import com.fanok.audiobooks.service.PlaybackSettings;
import com.fanok.audiobooks.util.DownloadUtil;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;


@InjectViewState
public class BookPresenter extends MvpPresenter<Activity> implements ActivityPresenter {
    private static final String TAG = "BookPresenter";

    public static final String Broadcast_PLAY_PREVIOUS = "PlayPriveos";
    public static final String Broadcast_PLAY_NEXT = "PlayNext";
    public static final String Broadcast_PLAY = "Play";
    public static final String Broadcast_SEEK_TO = "SeekTo";
    public static final String Broadcast_SEEK_NEXT_30 = "SeekToNext30";

    public static final String Broadcast_SEEK_PREVIOUS_10 = "SeekToPrevious10";

    public static final String Broadcast_SHOW_TITLE = "SHOW_TITLE";

    public static final String Broadcast_GET_POSITION = "GET_POSITION";

    public static final String Broadcast_SET_SPEED = "SET_SPEED";

    public static final String Broadcast_CloseNotPrepered = "CloseNotPrepered";

    public static final String Broadcast_Equalizer = "Equalizer";

    public static boolean start = false;

    public static boolean resume = false;

    private final BookPOJO mBookPOJO;

    private AudioDBModel mAudioDBModel;

    private AudioListDBModel mAudioListDBModel;

    private final OtherSourceModel mOtherSourceModel;

    private AudioModelInterfece mAudioModel;

    private ArrayList<AudioPOJO> mAudioPOJO;

    private String last = "";

    private BooksDBModel mBooksDBModel;

    private boolean serviceBound = false;

    private static float speed = 1;

    private boolean mIsOfflineFallback = false;

    private Throwable mLastError = null;

    private final ServiceConnection serviceConnection;

    //the context the service was bound through; unbinding must use the very same one
    private Context bindContext;

    private Context mContext;

    private boolean error = false;

    private SharedPreferences pref;

    private ArrayList<OtherArtistPOJO> mOtherArtistPOJOS;

    private int state = BottomSheetBehavior.STATE_COLLAPSED;

    public int getState() {
        return state;
    }

    public BookPresenter(@NonNull BookPOJO bookPOJO, @NonNull Context context) {
        mBookPOJO = bookPOJO;
        mBooksDBModel = new BooksDBModel(context);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mBooksDBModel.addHistory(mBookPOJO);
                } catch (Exception ignored) {}
            }
        }).start();
        mAudioDBModel = new AudioDBModel(context);
        mAudioModel = new AudioModel(context);
        mAudioListDBModel = new AudioListDBModel(context);
        mOtherSourceModel = new OtherSourceModel();
        pref = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serviceBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                serviceBound = false;
            }
        };
    }

    public ArrayList<OtherArtistPOJO> getOtherArtistPOJOS() {
        return mOtherArtistPOJOS;
    }

    public static void setSpeedWithoutBroadcast(float value) {
        speed = PlaybackSettings.clampSpeed(value);
    }

    public static float getSpeed() {
        return speed;
    }

    private void setSpeed(float value) {
        value = PlaybackSettings.clampSpeed(value);
        speed = value;
        new StorageUtil(mContext).storeSpeed(value);
        Intent intent = new Intent(Broadcast_SET_SPEED);
        intent.putExtra("speed", value);
        getViewState().broadcastSend(intent);
    }

    public boolean isServiceBound() {
        return serviceBound;
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        if (mAudioDBModel.isset(mBookPOJO.getUrl())) {
            last = mAudioDBModel.getName(mBookPOJO.getUrl());
        }

        if (!MediaPlayerService.isPlay()) {
            if (!last.isEmpty()) {
                getViewState().showTitle(last);
            }
        } else {
            getViewState().setImageDrawable(R.drawable.ic_pause);
            Intent broadcastIntent = new Intent(Broadcast_SHOW_TITLE);
            getViewState().broadcastSend(broadcastIntent);
        }
        getAudio();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu) {
        if (mBookPOJO != null && mBooksDBModel != null) {
            getViewState().setIsFavorite(mBooksDBModel.inFavorite(mBookPOJO));
        }
    }

    @Override
    public void onStop() {
        //the service binding is released in unbindPlayerService() when the screen is really finished:
        //unbinding on every stop (rotation, opening another screen) would release a paused player
    }

    @Override
    public void onCreate(final Context context) {
        if (mBooksDBModel == null || mContext == null) {
            mBooksDBModel = new BooksDBModel(context);
        }
        if (mAudioDBModel == null || mContext == null) {
            mAudioDBModel = new AudioDBModel(context);
        }
        if (mAudioModel == null || mContext == null) {
            mAudioModel = new AudioModel(context);
        }
        if (mAudioListDBModel == null || mContext == null) {
            mAudioListDBModel = new AudioListDBModel(context);
        }
        if (pref == null || mContext == null) {
            pref = PreferenceManager.getDefaultSharedPreferences(context);
        }
        mContext = context;
    }

    public void setServiceBound(boolean serviceBound) {
        this.serviceBound = serviceBound;
    }

    @Override
    public void onItemSelected(View view, int position) {
        if (mAudioDBModel.isset(mBookPOJO.getUrl())) {
            mAudioDBModel.remove(mBookPOJO.getUrl());
        }
        if (mAudioPOJO != null) {
            mAudioDBModel.add(mBookPOJO.getUrl(), mAudioPOJO.get(position).getName());
            last = mAudioPOJO.get(position).getName();
            getViewState().showTitle(last);
            playAudio(position, 0);
        }

    }

    @Override
    public void onOptionsMenuItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.addFavorite) {
            if (!mBooksDBModel.inFavorite(mBookPOJO)) {
                mBooksDBModel.addFavorite(mBookPOJO);
                getViewState().setIsFavorite(true);
            }
        } else if (itemId == R.id.removeFavorite) {
            if (mBooksDBModel.inFavorite(mBookPOJO)) {
                mBooksDBModel.removeFavorite(mBookPOJO);
                getViewState().setIsFavorite(false);
            }
        } else if (itemId == R.id.refresh) {
            getViewState().refreshActivity();
        } else if (itemId == R.id.share) {
            getViewState().shareTextUrl();
        } else if (itemId == R.id.addMainScreen) {
            getViewState().addToMainScreen(mBookPOJO);
        } else if (itemId == R.id.openSite) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(mBookPOJO.getUrl()));
            getViewState().activityStart(browserIntent);
        } else if (itemId == R.id.sleep_timer) {
            Intent sleepIntent = new Intent(mContext, SleepTimerActivity.class);
            getViewState().activityStart(sleepIntent);
        } else if (itemId == R.id.equalizer) {
            Intent breadCast = new Intent(Broadcast_Equalizer);
            getViewState().broadcastSend(breadCast);
        } else if (itemId == R.id.settings) {
            getViewState().startMainActivity(Consts.FRAGMENT_SETTINGS);
        }
    }

    @Override
    public void onDestroy() {
        mAudioDBModel.closeDB();
        mAudioListDBModel.closeDB();
        mBooksDBModel.closeDB();
        if (mContext != null) {
            Intent broadcastIntent = new Intent(Broadcast_CloseNotPrepered);
            broadcastIntent.setPackage(mContext.getPackageName());
            mContext.sendBroadcast(broadcastIntent);
        }
        super.onDestroy();
    }

    @Override
    public void addDecodeData(final String string) {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        JsonElement jsonTree = JsonParser.parseString(string);
        if (jsonTree.isJsonArray()) {
            JsonArray jsonArray = jsonTree.getAsJsonArray();
            for (JsonElement element : jsonArray) {
                if (element.isJsonObject()) {
                    AudioPOJO audioPOJO = new AudioPOJO();
                    JsonObject jsonObject = element.getAsJsonObject();
                    audioPOJO.setName(jsonObject.get("title").getAsString());
                    audioPOJO.setUrl(jsonObject.get("file").getAsString());
                    audioPOJO.setBookName(mBookPOJO.getName());
                    result.add(audioPOJO);
                }
            }
        }
        mAudioPOJO = result;
        getViewState().showData(mAudioPOJO, mBookPOJO.getUrl());
        error = false;
        onCompleteAudio();

    }

    private void onCompleteAudio() {
        if (mAudioPOJO != null && !mAudioPOJO.isEmpty()) {
            if (mAudioPOJO.size() == 1 && mAudioPOJO.get(0).getUrl() != null && mAudioPOJO.get(0).getUrl().isEmpty()) {
                getViewState().showToast(mAudioPOJO.get(0).getName());
                return;
            }
            Log.d(TAG, "onComplete");
            int curentTrack = 0;
            AudioPOJO pojo = null;
            for (int i = 0; i < mAudioPOJO.size(); i++) {
                if (mAudioPOJO.get(i).getName().equals(last)) {
                    pojo = mAudioPOJO.get(i);
                    curentTrack = i;
                }
            }
            if (pojo == null) {
                pojo = mAudioPOJO.get(0);
                curentTrack = 0;
                if (!MediaPlayerService.isPlay()) {
                    getViewState().showTitle(pojo.getName());
                }
                mAudioDBModel.add(mBookPOJO.getUrl(), pojo.getName());
            }

            if (!error && !mIsOfflineFallback) {
                if (mAudioListDBModel.isset(mBookPOJO.getUrl())) {
                    mAudioListDBModel.remove(mBookPOJO.getUrl());
                }
                for (int i = 0; i < mAudioPOJO.size(); i++) {
                    AudioListPOJO audioListPOJO = new AudioListPOJO();
                    audioListPOJO.setBookUrl(mBookPOJO.getUrl());
                    audioListPOJO.setBookName(mBookPOJO.getName());
                    audioListPOJO.setAudioName(mAudioPOJO.get(i).getName());
                    audioListPOJO.setAudioUrl(mAudioPOJO.get(i).getUrl());
                    audioListPOJO.setTime(mAudioPOJO.get(i).getTime());
                    audioListPOJO.setTimeStart(mAudioPOJO.get(i).getTimeStart());
                    audioListPOJO.setTimeEnd(mAudioPOJO.get(i).getTimeFinish());
                    mAudioListDBModel.add(audioListPOJO);
                }
            }

            boolean b = pref.getBoolean("reproductionPref", true);
            boolean autoStart = pref.getBoolean("autoPlayPref", false);

            if (autoStart) {
                if (!MediaPlayerService.isPlay()) {
                    start = true;
                    resume = true;
                    playAudio(curentTrack,
                            mAudioDBModel.getTime(mBookPOJO.getUrl()));
                } else if (!b) {
                    start = true;
                    resume = true;
                    playAudio(curentTrack,
                            mAudioDBModel.getTime(mBookPOJO.getUrl()));
                }

            } else if (!MediaPlayerService.isPlay() || !b) {
                start = false;
                playAudio(curentTrack, mAudioDBModel.getTime(mBookPOJO.getUrl()));
            } else {
                start = true;
            }
            getViewState().setSelected(curentTrack,
                    mAudioPOJO.get(curentTrack).getName());

            getViewState().showProgres(false);
        }
    }

    @Override
    public void buttonSpeedClick(View view) {

        final int step = 10;

        final AlertDialog.Builder popDialog = new AlertDialog.Builder(view.getContext());
        final LinearLayout linearLayout = new LinearLayout(view.getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        final TextView textView = new TextView(view.getContext());
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText(String.valueOf(speed));
        textView.setTextColor(getAttributeColor(textView.getContext(), R.attr.colorPrimaryText));
        linearLayout.addView(textView);
        final SeekBar seek = new SeekBar(view.getContext());
        seek.setMax(300);
        seek.incrementProgressBy(step);
        seek.setProgress((int) (speed * 100));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = progress / step;
                progress = progress * step;
                String s = progress / 100 + "." + progress % 100;
                textView.setText(s);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        linearLayout.addView(seek);

        popDialog.setIcon(R.drawable.ic_play_speed);
        popDialog.setTitle(view.getContext().getString(R.string.title_set_speed_popup));
        popDialog.setView(linearLayout);

        // Button OK
        popDialog.setPositiveButton("OK",
                (dialog, which) -> {
                    float s = PlaybackSettings.clampSpeed(Float.parseFloat(textView.getText().toString()));
                    if (s != speed) {
                        setSpeed(s);
                    }
                    dialog.dismiss();
                });

        popDialog.create();
        popDialog.show();
    }


    @Override
    public void buttomPreviousClick(View view) {
        if (mAudioPOJO != null && !mAudioPOJO.isEmpty()) {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_PREVIOUS);
            getViewState().broadcastSend(broadcastIntent);
        }
    }

    @Override
    public void buttomPlayClick(View view) {
        if (!isServiceRunning(mContext, MediaPlayerService.class) && mAudioPOJO != null
                && !mAudioPOJO.isEmpty()) {
            serviceBound = false;
            start = true;
            resume = true;
            int curentTrack = 0;
            for (int i = 0; i < mAudioPOJO.size(); i++) {
                if (mAudioPOJO.get(i).getName().equals(last)) {
                    curentTrack = i;
                }
            }

            playAudio(curentTrack, mAudioDBModel.getTime(mBookPOJO.getUrl()));
        }
        Intent broadcastIntent = new Intent(Broadcast_PLAY);
        getViewState().broadcastSend(broadcastIntent);
    }

    @Override
    public void buttomRewindClick(View view) {
        Intent broadcastIntent = new Intent(Broadcast_SEEK_PREVIOUS_10);
        getViewState().broadcastSend(broadcastIntent);
    }

    @Override
    public void buttomForwardClick(View view) {
        Intent broadcastIntent = new Intent(Broadcast_SEEK_NEXT_30);
        getViewState().broadcastSend(broadcastIntent);
    }

    @Override
    public void seekChange(int progress) {
        Intent broadcastIntent = new Intent(Broadcast_SEEK_TO);
        broadcastIntent.putExtra("postion", progress);
        getViewState().broadcastSend(broadcastIntent);
    }

    @Override
    public void buttomNextClick(View view) {
        if (mAudioPOJO != null && !mAudioPOJO.isEmpty()) {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEXT);
            getViewState().broadcastSend(broadcastIntent);
        }

    }

    @Override
    public void setImageDrawable(int id) {
        getViewState().setImageDrawable(id);
    }

    @Override
    public void updateTime(int timeCurrent, int timeEnd, int buffered) {
        getViewState().updateTime(timeCurrent, timeEnd, buffered);
    }

    @Override
    public void setSelected(int pos, @NonNull String name) {
        getViewState().setSelected(pos, name);
    }

    @Override
    public void showTitle(@NonNull String title) {
        getViewState().showTitle(title);
    }

    @Override
    public void stateCollapsed() {
        state = BottomSheetBehavior.STATE_COLLAPSED;
        getViewState().stateCollapsed();
    }

    @Override
    public void stateExpanded() {
        state = BottomSheetBehavior.STATE_EXPANDED;
        getViewState().stateExpanded();
    }

    @Override
    public void stateElse() {
        getViewState().stateElse();
    }

    @UnstableApi
    @Override
    public void dowland(HashSet<String> data) {
        for (String url : data) {
            Uri uri = Uri.parse(url);
            String id = new Uri.Builder()
                    .scheme(uri.getScheme())
                    .authority(uri.getAuthority())
                    .path(uri.getPath())
                    .build()
                    .toString();
            DownloadRequest downloadRequest = new DownloadRequest.Builder(id, DownloadUtil.buildAnnotatedUri(url, mBookPOJO.getUrl()))
                    .setData(new Gson().toJson(mBookPOJO).getBytes())
                    //.setCustomCacheKey(url)
                    .build();
            DownloadService.sendAddDownload(mContext, ExoDownloadService.class, downloadRequest, false);
        }
    }
    @UnstableApi
    @Override
    public void delete(HashSet<String> data) {
        if (data == null || data.isEmpty()) return;

        HashSet<String> chapterNames = new HashSet<>();
        if (mAudioPOJO != null) {
            for (AudioPOJO pojo : mAudioPOJO) {
                if (pojo != null && pojo.getUrl() != null && data.contains(pojo.getUrl())) {
                    if (pojo.getName() != null && !pojo.getName().isEmpty()) {
                        chapterNames.add(pojo.getName());
                    }
                }
            }
        }

        boolean isImported = mBookPOJO != null && mBookPOJO.getUrl() != null && mBookPOJO.getUrl().contains("/imported/");

        new Thread(() -> {
            com.fanok.audiobooks.util.DownloadStorageHelper.deleteChapterFiles(mContext, mBookPOJO.getUrl(), mBookPOJO, data, chapterNames);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (isImported) {
                    getAudio();
                } else {
                    getViewState().updateAdapter();
                }
            });
        }).start();
        getViewState().showToast(R.string.delete_complite);
    }

    @Override
    public void getAudio() {
        error = false;
        mIsOfflineFallback = false;
        mLastError = null;
        getViewState().showProgres(true);

        // For imported/local books — skip network, load from DB directly
        if (mBookPOJO.getUrl() != null && mBookPOJO.getUrl().contains("/imported/")) {
            try {
                ArrayList<AudioListPOJO> list = mAudioListDBModel.get(mBookPOJO.getUrl());
                ArrayList<AudioPOJO> audioPOJOS = new ArrayList<>();
                if (list != null) {
                    for (AudioListPOJO pojo : list) {
                        AudioPOJO audioPOJO = new AudioPOJO();
                        audioPOJO.setName(pojo.getAudioName());
                        audioPOJO.setBookName(pojo.getBookName() != null ? pojo.getBookName() : "");
                        audioPOJO.setUrl(pojo.getAudioUrl() != null ? pojo.getAudioUrl() : "");
                        audioPOJO.setTime(pojo.getTime());
                        audioPOJO.setTimeStart(pojo.getTimeStart());
                        audioPOJO.setTimeFinish(pojo.getTimeEnd());
                        audioPOJOS.add(audioPOJO);
                    }
                }
                mAudioPOJO = audioPOJOS;
                getViewState().showData(mAudioPOJO, mBookPOJO.getUrl());
                getViewState().showProgres(false);
                error = false;
                onCompleteAudio();
            } catch (Exception e) {
                Log.e(TAG, "getAudio: imported book error", e);
                getViewState().showProgres(false);
                getViewState().showToast("Ошибка загрузки импортированной книги: " + e.getMessage());
            }
            return;
        }
        if (!Consts.isNetworkAvailable(mContext)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    loadOfflineLocalAudioSync();
                }
            }, "offline-fallback-thread").start();
            return;
        }

        mAudioModel.getAudio(mBookPOJO.getUrl())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<AudioPOJO>>() {
                    @Override
                    public void onComplete() {
                        onCompleteAudio();
                    }
                    @UnstableApi
                    @Override
                    public void onError(@NotNull Throwable e) {
                        Log.e(TAG, "getAudio network error: ", e);
                        mLastError = e;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                loadOfflineLocalAudioSync();
                            }
                        }, "offline-fallback-thread").start();
                    }

                    @Override
                    public void onNext(@NotNull ArrayList<AudioPOJO> bookPOJOS) {
                        mAudioPOJO = bookPOJOS;
                        getViewState().showData(mAudioPOJO, mBookPOJO.getUrl());
                        error = false;
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }
                });

        mOtherSourceModel.getOtherArtist(mBookPOJO)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ArrayList<OtherArtistPOJO>>() {
                    @Override
                    public void onComplete() {
                        if (!mOtherArtistPOJOS.isEmpty()) {
                            getViewState().showOtherSource();
                        }
                    }

                    @Override
                    public void onError(@NotNull Throwable e) {
                        Log.d(TAG, String.valueOf(e.getMessage()));
                    }

                    @Override
                    public void onNext(@NotNull ArrayList<OtherArtistPOJO> bookPOJOS) {
                        mOtherArtistPOJOS = bookPOJOS;
                    }

                    @Override
                    public void onSubscribe(@NotNull Disposable d) {
                    }
                });
    }

    @UnstableApi
    private void loadOfflineLocalAudioSync() {
        ArrayList<AudioPOJO> localAudio = new ArrayList<>();

        boolean foundSaved = false;
        if (mBookPOJO != null && mBookPOJO.getUrl() != null) {
            try {
                ArrayList<AudioListPOJO> savedList = mAudioListDBModel.get(mBookPOJO.getUrl());
                if (savedList != null && !savedList.isEmpty()) {
                    DownloadIndex downloadIndex = null;
                    try {
                        downloadIndex = getDownloadManager().getDownloadIndex();
                    } catch (Exception ignored) {}

                    for (AudioListPOJO pojo : savedList) {
                        if (pojo == null) continue;
                        String audioUrl = pojo.getAudioUrl();
                        String resolvedUrl = null;

                        if (audioUrl != null && (audioUrl.startsWith("content://") || audioUrl.startsWith("file://"))) {
                            resolvedUrl = audioUrl;
                        } else if (audioUrl != null && audioUrl.startsWith("/")) {
                            java.io.File f = new java.io.File(audioUrl);
                            if (f.exists()) resolvedUrl = audioUrl;
                        } else if (audioUrl != null && !audioUrl.isEmpty() && downloadIndex != null) {
                            try {
                                Download download = downloadIndex.getDownload(pojo.getCleanAudioUrl());
                                if (download == null) {
                                    download = downloadIndex.getDownload(audioUrl);
                                }
                                if (download != null && download.state == Download.STATE_COMPLETED) {
                                    resolvedUrl = audioUrl;
                                }
                            } catch (IOException ignored) {}
                        }

                        if (resolvedUrl == null && pojo.getAudioName() != null && !pojo.getAudioName().isEmpty()) {
                            java.io.File bookDir = com.fanok.audiobooks.util.DownloadStorageHelper.getBookDownloadDir(mContext, mBookPOJO.getUrl(), mBookPOJO.getName(), mBookPOJO.getArtist(), mBookPOJO.getAutor());
                            if (bookDir != null && bookDir.exists()) {
                                java.io.File found = findExportedFileByName(bookDir, pojo.getAudioName());
                                if (found != null) resolvedUrl = found.getAbsolutePath();
                            }
                            if (resolvedUrl == null) {
                                String dlPath = com.fanok.audiobooks.util.DownloadStorageHelper.getDownloadPath(mContext);
                                java.io.File baseDir = new java.io.File(dlPath);
                                if (baseDir.exists()) {
                                    java.io.File found = findExportedFileByName(baseDir, pojo.getAudioName());
                                    if (found != null) resolvedUrl = found.getAbsolutePath();
                                }
                            }
                        }

                        if (resolvedUrl != null) {
                            AudioPOJO audioPOJO = new AudioPOJO();
                            audioPOJO.setName(pojo.getAudioName());
                            audioPOJO.setBookName(pojo.getBookName() != null ? pojo.getBookName() : "");
                            audioPOJO.setUrl(resolvedUrl);
                            audioPOJO.setTime(pojo.getTime());
                            audioPOJO.setTimeStart(pojo.getTimeStart());
                            audioPOJO.setTimeFinish(pojo.getTimeEnd());
                            localAudio.add(audioPOJO);
                        }
                    }

                    if (!localAudio.isEmpty()) {
                        foundSaved = true;
                    }
                }
            } catch (Exception ex) {
                Log.w(TAG, "getAudio: offline fallback failed", ex);
            }
        }
        final boolean finalFoundSaved = foundSaved;
        final ArrayList<AudioPOJO> finalLocalAudio = localAudio;

        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mIsOfflineFallback = true;
                mAudioPOJO = finalLocalAudio;
                getViewState().showData(mAudioPOJO, mBookPOJO.getUrl());
                getViewState().showProgres(false);
                if (!finalLocalAudio.isEmpty()) {
                    error = false;
                    onCompleteAudio();
                } else {
                    String errDetail = mLastError != null ? (" (" + mLastError.getClass().getSimpleName() + (mLastError.getMessage() != null ? ": " + mLastError.getMessage() : "") + ")") : "";
                    getViewState().showToast(mContext.getString(R.string.error_load_data)
                            + errDetail + "\n" + mContext.getString(R.string.go_offline));
                    error = true;
                }
            }
        });
    }

    /** Binds through the application context; {@link #unbindPlayerService()} releases it through the same one. */
    private void bindPlayerService(Intent playerIntent) {
        if (bindContext != null) {
            return; //already bound: a single binding, released exactly once
        }
        Context app = mContext.getApplicationContext();
        app.bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        bindContext = app;
    }

    /** Releases the binding made by {@link #playAudio}; safe to call any number of times. */
    public void unbindPlayerService() {
        Context app = bindContext;
        if (app == null) {
            return;
        }
        bindContext = null;
        serviceBound = false;
        try {
            app.unbindService(serviceConnection);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "service was not bound", e);
        }
    }

    private void playAudio(int audioIndex, int timeStart) {
        if (mAudioPOJO != null && mAudioPOJO.size() > audioIndex && mAudioPOJO.get(audioIndex).getUrl().isEmpty()) {
            getViewState().showToast(mAudioPOJO.get(audioIndex).getName());
            return;
        }
        StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
        storage.storeAudioIndex(audioIndex);
        if (!serviceBound || !isServiceRunning(mContext, MediaPlayerService.class)) {
            storage.storeAudio(mAudioPOJO);
            storage.storeUrlBook(mBookPOJO.getUrl());
            storage.storeImageUrl(mBookPOJO.getPhoto());
            storage.storeTimeStart(timeStart);
            Intent playerIntent = new Intent(mContext, MediaPlayerService.class);
            playerIntent.setAction("start");
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mContext.startForegroundService(playerIntent);
                } else {
                    mContext.startService(playerIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to startForegroundService, fallback to startService", e);
                try {
                    mContext.startService(playerIntent);
                } catch (Exception ex) {
                    Log.e(TAG, "Failed startService fallback", ex);
                }
            }
            bindPlayerService(playerIntent);
        } else {
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            getViewState().broadcastSend(broadcastIntent);
        }
    }

    /**
     * Recursively searches downloadDir for an audio file matching trackName (without extension).
     */
    private java.io.File findExportedFileByName(java.io.File dir, String trackName) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
        if (dir.getName().startsWith(".")) return null;
        String normalized = trackName.toLowerCase().replaceAll("[^a-z0-9а-яё]", "");
        java.io.File[] files = dir.listFiles();
        if (files == null) return null;
        for (java.io.File f : files) {
            if (f.isFile()) {
                String fname = f.getName().toLowerCase();
                int dot = fname.lastIndexOf('.');
                String ext = dot > 0 ? fname.substring(dot) : "";
                String nameNoExt = dot > 0 ? fname.substring(0, dot) : fname;
                String normFile = nameNoExt.replaceAll("[^a-z0-9а-яё]", "");
                boolean isAudio = ext.equals(".mp3") || ext.equals(".m4a") || ext.equals(".m4b")
                        || ext.equals(".mp4") || ext.equals(".aac") || ext.equals(".ogg")
                        || ext.equals(".flac") || ext.equals(".wav") || ext.equals(".ts");
                if (isAudio && !normalized.isEmpty() && normFile.equals(normalized)) return f;
            }
        }
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                java.io.File result = findExportedFileByName(f, trackName);
                if (result != null) return result;
            }
        }
        return null;
    }
}
