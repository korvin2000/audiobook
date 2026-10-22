package com.fanok.audiobooks.model;

import static com.fanok.audiobooks.util.DownloadUtil.getDownloadManager;

import android.content.Context;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadIndex;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

public class FavoriteModel implements
        com.fanok.audiobooks.interface_pacatge.favorite.FavoriteModel {

    private final BooksDBModel mBooksDBModel;
    private final AudioListDBModel mAudioListDBModel;
    private final Context mContext;

    public FavoriteModel(Context context) {
        mBooksDBModel = new BooksDBModel(context);
        mAudioListDBModel = new AudioListDBModel(context);
        mContext = context;
    }

    @UnstableApi
    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(int table) {
        return Observable.create(observableEmitter -> {
            try {
                ArrayList<BookPOJO> books;
                switch (table) {
                    case Consts.TABLE_FAVORITE:
                        books = mBooksDBModel.getAllFavorite();
                        break;
                    case Consts.TABLE_HISTORY:
                        books = mBooksDBModel.getAllHistory();
                        break;
                    case Consts.TABLE_SAVED:
                        books = mBooksDBModel.getAllSaved();
                        break;
                    default:
                        books = new ArrayList<>();
                        break;
                }

                if (table == Consts.TABLE_SAVED) {
                    if (mContext != null) {
                        DownloadIndex downloadIndex = getDownloadManager().getDownloadIndex();
                        Set<String> cacheKeys = null;
                        try {
                            cacheKeys = ((com.fanok.audiobooks.App) mContext.getApplicationContext()).getDownloadCache().getKeys();
                        } catch (Exception ignored) {
                        }

                        ArrayList<BookPOJO> visible = new ArrayList<>();
                        for (BookPOJO bookPOJO : books) {
                            boolean hasDownloads = false;
                            ArrayList<AudioListPOJO> arrayList = mAudioListDBModel.get(bookPOJO.getUrl());

                            if (arrayList != null && !arrayList.isEmpty()) {
                                try {
                                    for (AudioListPOJO pojo : arrayList) {
                                        // 1) The track has an active ExoPlayer download entry
                                        //    (downloading / queued / paused / completed -> visible)
                                        String cleanUrl = pojo.getCleanAudioUrl();
                                        if (cleanUrl != null && !cleanUrl.isEmpty()) {
                                            Download download = downloadIndex.getDownload(cleanUrl);
                                            if (download != null && download.state != Download.STATE_FAILED) {
                                                if (download.state == Download.STATE_COMPLETED) {
                                                    if (cacheKeys == null) {
                                                        hasDownloads = true;
                                                        break;
                                                    }
                                                    String id = download.request.id;
                                                    boolean foundInCache = false;
                                                    for (String key : cacheKeys) {
                                                        if (key.equals(id) || key.contains(id) || id.contains(key)) {
                                                            foundInCache = true;
                                                            break;
                                                        }
                                                    }
                                                    if (foundInCache) {
                                                        hasDownloads = true;
                                                        break;
                                                    }
                                                    // Not in cache any more - keep checking local files below,
                                                    // but never remove the download/book from the database here.
                                                } else {
                                                    // active / paused / queued download
                                                    hasDownloads = true;
                                                    break;
                                                }
                                            }
                                        }

                                        // 2) The track is exported to a local file path/content://
                                        if (pojo.getAudioUrl() != null && !pojo.getAudioUrl().startsWith("http")) {
                                            String url = pojo.getAudioUrl();
                                            if (url.startsWith("/") || url.startsWith("file://")) {
                                                if (new java.io.File(url.replace("file://", "")).exists()) {
                                                    hasDownloads = true;
                                                    break;
                                                }
                                            } else if (url.startsWith("content://")) {
                                                try {
                                                    androidx.documentfile.provider.DocumentFile doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(mContext, android.net.Uri.parse(url));
                                                    if (doc != null && doc.exists()) {
                                                        hasDownloads = true;
                                                        break;
                                                    }
                                                } catch (Exception ignored) {}
                                            } else {
                                                hasDownloads = true;
                                                break;
                                            }
                                        }

                                        // 3) Exported files exist in the book download directory
                                        java.io.File bookDir = com.fanok.audiobooks.util.DownloadStorageHelper.getBookDownloadDir(mContext, bookPOJO.getUrl(), bookPOJO.getName(), bookPOJO.getArtist(), bookPOJO.getAutor());
                                        if (bookDir.exists()) {
                                            java.io.File[] files = bookDir.listFiles();
                                            if (files != null && files.length > 0) {
                                                hasDownloads = true;
                                                break;
                                            }
                                        }
                                    }
                                } catch (IOException e) {
                                    // ignore
                                }
                            }

                            // Show only books that actually have a local copy, but do NOT
                            // delete anything from the DB - transient states must be safe.
                            if (hasDownloads) {
                                visible.add(bookPOJO);
                            }
                        }
                        books = visible;
                    }
                }
                observableEmitter.onNext(books);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    public void closeDB() {
        mBooksDBModel.closeDB();
        mAudioListDBModel.closeDB();
    }
}