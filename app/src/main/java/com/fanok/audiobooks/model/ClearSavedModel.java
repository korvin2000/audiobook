package com.fanok.audiobooks.model;


import android.content.Context;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.fanok.audiobooks.util.DownloadUtil;
import java.util.ArrayList;
import io.reactivex.Observable;

public class ClearSavedModel {

    private final BooksDBModel mBooksDBModel;
    private final AudioListDBModel mAudioListDBModel;
    private Context mContext;


    public ClearSavedModel(Context mContext) {
        mBooksDBModel = new BooksDBModel(mContext);
        mAudioListDBModel = new AudioListDBModel(mContext);
        this.mContext = mContext;
    }

    @UnstableApi
    public Observable<ArrayList<DownloadItem>> getAllSaved() {
        return Observable.create(observableEmitter -> {
            ArrayList<DownloadItem> downloadItems = new ArrayList<>();
            java.util.HashSet<String> addedUrls = new java.util.HashSet<>();

            try {
                ArrayList<BookPOJO> savedBooks = mBooksDBModel.getAllSaved();
                if (savedBooks != null) {
                    for (BookPOJO book : savedBooks) {
                        if (book.getUrl() == null || book.getUrl().isEmpty()) continue;
                        DownloadItem item = new DownloadItem(book.getUrl(), Download.STATE_COMPLETED);
                        item.setBookName(book.getName());
                        item.setChapterName(book.getName());
                        item.setAuthor(book.getAutor());
                        item.setReader(book.getArtist());
                        item.setFileIcon(book.getPhoto());
                        item.setSource(com.fanok.audiobooks.util.DownloadStorageHelper.getSourceName(book.getUrl()));
                        item.setProgress(100);
                        downloadItems.add(item);
                        addedUrls.add(book.getUrl());
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("ClearSavedModel", "Failed to load saved books from Room DB", e);
            }

            try {
                DownloadIndex index = DownloadUtil.getDownloadManager().getDownloadIndex();
                if (index != null) {
                    try (DownloadCursor cursor = index.getDownloads()) {
                        while (cursor.moveToNext()) {
                            Download d = cursor.getDownload();
                            String ref = d.request.uri != null ? d.request.uri.getQueryParameter("__ref") : null;
                            if (ref != null) {
                                if (ref.contains("%2F") || ref.contains("%3A")) {
                                    ref = android.net.Uri.decode(ref);
                                }
                                if (!ref.startsWith("http://") && !ref.startsWith("https://")) {
                                    ref = "https://" + ref;
                                }
                            }
                            String urlKey = ref != null ? ref : d.request.id;

                            if (!addedUrls.contains(urlKey) && !addedUrls.contains(d.request.id)) {
                                DownloadItem item = new DownloadItem(urlKey, d.state);
                                int progress = 0;
                                if (d.getPercentDownloaded() != C.PERCENTAGE_UNSET) {
                                    progress = Math.max(0, Math.min(100, Math.round(d.getPercentDownloaded())));
                                } else if (d.contentLength > 0) {
                                    progress = (int) Math.round(100.0 * d.getBytesDownloaded() / (double) d.contentLength);
                                    progress = Math.max(0, Math.min(100, progress));
                                }
                                item.setProgress(progress);

                                if (ref != null) {
                                    AudioListPOJO audioListPOJO = mAudioListDBModel.get(ref, d.request.id);
                                    if (audioListPOJO.getBookUrl() != null && audioListPOJO.getBookUrl().equals(ref)) {
                                        item.setBookName(audioListPOJO.getBookName());
                                        item.setChapterName(audioListPOJO.getAudioName());
                                    }
                                    BookPOJO bookPOJO = mBooksDBModel.getSomewhere(ref);
                                    if (bookPOJO != null) {
                                        item.setAuthor(bookPOJO.getAutor());
                                        item.setFileIcon(bookPOJO.getPhoto());
                                        item.setReader(bookPOJO.getArtist());
                                        if (item.getBookName() == null || item.getBookName().isEmpty()) {
                                            item.setBookName(bookPOJO.getName());
                                        }
                                    }
                                }
                                downloadItems.add(item);
                                addedUrls.add(urlKey);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("ClearSavedModel", "ExoPlayer index query skipped", e);
            }

            if (!observableEmitter.isDisposed()) {
                observableEmitter.onNext(downloadItems);
                observableEmitter.onComplete();
            }
        });
    }

    public void closeDB() {
        mBooksDBModel.closeDB();
        mAudioListDBModel.closeDB();
        mContext = null;
    }


    @UnstableApi
    public DownloadItem buildDownloadItem(Download newDownload) {
        DownloadItem item = new DownloadItem(newDownload.request.id, newDownload.state);
        item.setProgress((int) newDownload.getPercentDownloaded());
        String ref = newDownload.request.uri.getQueryParameter("__ref");
        if (ref!=null){
            if (ref.contains("%2F") || ref.contains("%3A")) {
                ref = android.net.Uri.decode(ref);
            }
            if (!ref.startsWith("http://") && !ref.startsWith("https://")) {
                ref = "https://" + ref;
            }
            AudioListPOJO audioListPOJO = mAudioListDBModel.get(ref, newDownload.request.id);
            if (audioListPOJO.getBookUrl()!=null && audioListPOJO.getBookUrl().equals(ref)){
                item.setBookName(audioListPOJO.getBookName());
                item.setChapterName(audioListPOJO.getAudioName());
            }
            if (ref.contains(Url.SERVER)) {
                item.setSource(mContext.getString(R.string.kniga_v_uhe));
            } else if (ref.contains(Url.SERVER_IZIBUK)) {
                item.setSource(mContext.getString(R.string.izibuc));
            } else if (ref.contains(Url.SERVER_ABMP3)) {
                item.setSource(mContext.getString(R.string.audionook_mp3));
            } else if (ref.contains(Url.SERVER_AKNIGA)) {
                item.setSource(mContext.getString(R.string.abook));
            } else if (ref.contains(Url.SERVER_BAZA_KNIG)) {
                item.setSource(mContext.getString(R.string.baza_knig));
            } else if (ref.contains(Url.SERVER_KNIGOBLUD)) {
                item.setSource(mContext.getString(R.string.knigoblud));
            } else if (ref.contains(Url.SERVER_OTRUB)) {
                item.setSource(mContext.getString(R.string.otrub));
            } else if (ref.contains(Url.SERVER_POLEKNIG)) {
                item.setSource(mContext.getString(R.string.poleknig));
            } else {
                item.setSource("");
            }
            BookPOJO bookPOJO = mBooksDBModel.getSomewhere(ref);
            if (bookPOJO!=null){
                item.setAuthor(bookPOJO.getAutor());
                item.setFileIcon(bookPOJO.getPhoto());
                item.setReader(bookPOJO.getArtist());
                if (item.getBookName()==null||item.getBookName().isEmpty()){
                    item.setBookName(bookPOJO.getName());
                }
            }
            if (ref.contains(Url.SERVER_AKNIGA)){
                item.setChapterName(item.getBookName());
            }
        }
        return item;
    }
}
