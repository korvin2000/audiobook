package com.fanok.audiobooks.presenter;


import static androidx.preference.PreferenceManager.getDefaultSharedPreferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.arellomobile.mvp.InjectViewState;
import com.arellomobile.mvp.MvpPresenter;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.fragment.BooksFragment;
import com.fanok.audiobooks.interface_pacatge.books.BooksView;
import com.fanok.audiobooks.model.AutorsModel;
import com.fanok.audiobooks.model.BooksDBModel;
import com.fanok.audiobooks.model.BooksModel;
import com.fanok.audiobooks.model.GenreModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.policy.ParentalControl;
import com.fanok.audiobooks.search.ListingCursor;
import com.fanok.audiobooks.search.ProviderPage;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.squareup.picasso.Picasso;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Objects;

@InjectViewState
public class BooksPresenter extends MvpPresenter<BooksView> implements
        com.fanok.audiobooks.interface_pacatge.books.BooksPresenter {

    private boolean isLoading = false;
    private boolean isRefreshing = false;
    private final int mModelId;
    private final String mSubTitle;
    private ArrayList<BookPOJO> books;
    private ArrayList<GenrePOJO> genre;
    private final ListingCursor<BookPOJO> mBookCursor = new ListingCursor<>(b -> Url.canonical(b.getUrl()));
    private final ListingCursor<GenrePOJO> mGenreCursor = new ListingCursor<>(GenrePOJO::getUrl);
    private final CompositeDisposable mDisposables = new CompositeDisposable();
    private com.fanok.audiobooks.interface_pacatge.books.BooksModel mModelBook;
    private com.fanok.audiobooks.interface_pacatge.books.GenreModel mModelGenre;
    private final BooksDBModel mBooksDBModel;
    private final Context mContext;


    private final String mUrl;

    public BooksPresenter(@NonNull String url, int modelId, @NonNull String subTitle,
            @NonNull Context context) {
        mUrl = url;
        mModelId = modelId;
        mSubTitle = subTitle;
        mBooksDBModel = new BooksDBModel(context);
        switch (mModelId) {
            case Consts.MODEL_BOOKS:
                books = new ArrayList<>();
                mModelBook = new BooksModel();
                break;
            case Consts.MODEL_GENRE:
                genre = new ArrayList<>();
                mModelGenre = new GenreModel();
                break;
            case Consts.MODEL_AUTOR:
            case Consts.MODEL_ARTIST:
                genre = new ArrayList<>();
                AutorsModel autorsModel = new AutorsModel();
                if (mModelId == Consts.MODEL_ARTIST) {
                    autorsModel.setPerformerMode(true);
                }
                mModelGenre = autorsModel;
                break;
        }
        mContext = context;

    }

    @Override
    public void onDestroy() {
        cancel();
        mBooksDBModel.closeDB();
    }

    /** Cancels the running load and resets the loading state with it, so no spinner or flag stays stuck. */
    private void cancel() {
        mDisposables.clear();
        isLoading = false;
        isRefreshing = false;
        getViewState().showProgres(false);
        getViewState().showRefreshing(false);
    }

    @Override
    protected void onFirstViewAttach() {
        super.onFirstViewAttach();
        loadBoks();
    }

    @Override
    public void attachView(BooksView view) {
        super.attachView(view);
        // a listing whose load was cancelled before anything arrived starts again
        if (!isLoading && !cursor().isEnd() && cursor().items().isEmpty()) {
            loadBoks();
        }
    }

    private ListingCursor<?> cursor() {
        return mModelId == Consts.MODEL_BOOKS ? mBookCursor : mGenreCursor;
    }

    @Override
    public void loadBoks() {
        if (!cursor().isEnd() && !isLoading) {
            getViewState().showProgres(true);
            getData(cursor().nextPage());
        } else {
            getViewState().showRefreshing(false);
        }
    }

    /** The listing URL of {@code page} in the provider's own scheme; genre lists have a single page. */
    private String pageUrl(int page) {
        if (mModelId == Consts.MODEL_GENRE) {
            return mUrl;
        }
        if (Url.isHost(mUrl, Url.SERVER_AUDIOBOO)) {
            //the index, genres and the author, reader and series links of cards share one scheme
            return Url.audiobooListingPage(mUrl, page);
        } else if (mUrl.contains(Url.SERVER_KNIGOBLUD)) {
            return mUrl.contains("?") ? mUrl + page : mUrl + "/" + page;
        } else if (mUrl.contains(Url.SERVER_OTRUB) || mUrl.contains(Url.SERVER_POLEKNIG)) {
            return mUrl.contains("?") ? mUrl + page : mUrl + "?p=" + page;
        } else if ((!mUrl.contains("genre") || mUrl.contains(Url.SERVER_IZIBUK) || mUrl.contains(Url.SERVER_ABMP3)
                || mUrl.contains(Url.SERVER_BAZA_KNIG))
                && !mUrl.contains(Url.SERVER_AKNIGA)) {
            return mUrl + page + "/";
        } else {
            return mUrl.replace("<page>", Integer.toString(page));
        }
    }

    @Override
    public void onBookItemLongClick(View view, int position, LayoutInflater layoutInflater) {

        @SuppressLint("InflateParams") View layout = layoutInflater.inflate(
                R.layout.bootom_sheet_books_menu, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(view.getContext());
        dialog.setContentView(layout);

        TextView open = layout.findViewById(R.id.open);
        TextView add = layout.findViewById(R.id.addFavorite);
        TextView remove = layout.findViewById(R.id.removeFavorite);
        TextView genre = layout.findViewById(R.id.genre);
        TextView author = layout.findViewById(R.id.author);
        TextView artist = layout.findViewById(R.id.artist);
        TextView series = layout.findViewById(R.id.series);

        Drawable mask = ParentalControl.coverMask(view.getContext(), books.get(position));
        if (mask != null) {
            ImageView imageView = layout.findViewById(R.id.imageView);
            imageView.setImageDrawable(mask);
        } else if (books.get(position).getPhoto() != null) {
            ImageView imageView = layout.findViewById(R.id.imageView);
            Picasso.get()
                    .load(books.get(position).getPhoto())
                    .error(android.R.drawable.ic_menu_gallery)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(imageView);
        }

        TextView title = layout.findViewById(R.id.title);
        title.setText(books.get(position).getName());

        TextView authorName = layout.findViewById(R.id.authorName);
        if (books.get(position).getAutor() != null) {
            authorName.setText(books.get(position).getAutor());
            authorName.setVisibility(View.VISIBLE);
        } else {
            authorName.setVisibility(View.GONE);
        }

        if (books.get(position).getSeries() == null || books.get(position).getUrlSeries() == null) {
            series.setVisibility(View.GONE);
        } else {
            series.setVisibility(View.VISIBLE);
        }

        if (mBooksDBModel.inFavorite(books.get(position))) {
            add.setVisibility(View.GONE);
            remove.setVisibility(View.VISIBLE);
        } else {
            add.setVisibility(View.VISIBLE);
            remove.setVisibility(View.GONE);
        }

        open.setOnClickListener(view1 -> {
            dialog.dismiss();
            getViewState().showBooksActivity(books.get(position));
        });

        add.setOnClickListener(view1 -> {
            dialog.dismiss();
            mBooksDBModel.addFavorite(books.get(position));
        });

        remove.setOnClickListener(view1 -> {
            dialog.dismiss();
            mBooksDBModel.removeFavorite(books.get(position));
        });

        if (books.get(position).getUrlGenre() != null) {
            genre.setVisibility(View.VISIBLE);
            genre.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().showFragment(BooksFragment.newInstance(
                        books.get(position).getUrlGenre(),
                        R.string.menu_audiobooks,
                        books.get(position).getGenre(), Consts.MODEL_BOOKS),
                        "genreBooks");
            });
        } else {
            genre.setVisibility(View.GONE);
        }

        if (books.get(position).getUrlAutor() != null) {
            author.setVisibility(View.VISIBLE);
            author.setOnClickListener(view1 -> {
                dialog.dismiss();
                if (!books.get(position).getUrlAutor().isEmpty()) {
                    getViewState().showFragment(BooksFragment.newInstance(
                            books.get(position).getUrlAutor(),
                            R.string.menu_audiobooks,
                            books.get(position).getAutor(), Consts.MODEL_BOOKS),
                            "autorBooks");
                }
            });
        } else {
            author.setVisibility(View.GONE);
        }

        if (books.get(position).getUrlArtist() != null) {
            artist.setVisibility(View.VISIBLE);
            artist.setOnClickListener(view1 -> {
                dialog.dismiss();
                getViewState().showFragment(BooksFragment.newInstance(
                        books.get(position).getUrlArtist(),
                        R.string.menu_audiobooks,
                        books.get(position).getArtist(), Consts.MODEL_BOOKS),
                        "artistBooks");
            });
        } else {
            artist.setVisibility(View.GONE);
        }

        series.setOnClickListener(view12 -> {
            dialog.dismiss();
            getViewState().showFragment(BooksFragment.newInstance(
                    books.get(position).getUrlSeries(),
                    R.string.menu_audiobooks,
                    books.get(position).getSeries(), Consts.MODEL_BOOKS),
                    "seriesBooks");
        });
        dialog.show();
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onOptionItemSelected(int itemId) {
        String subTitle = mSubTitle.replace(" " + getStringById(R.string.order_new), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_reting), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_popular), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_coments), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_year), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_title), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.order_duration), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.knigoblud_today), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.knigoblud_trending), "");
        subTitle = subTitle.replace(" " + getStringById(R.string.knigoblud_finished), "");
        String url = "";

        if (itemId == R.id.source_izi_book || itemId == R.id.source_kniga_v_uhe
                || itemId == R.id.source_audio_book_mp3 || itemId == R.id.source_abook
                || itemId == R.id.source_baza_knig||itemId == R.id.source_knigoblud || itemId == R.id.source_audioboo
                || itemId == R.id.source_otrub || itemId == R.id.source_poleknig) {
            SharedPreferences pref = getDefaultSharedPreferences(Objects.requireNonNull(mContext));
            SharedPreferences.Editor editor = pref.edit();
            String sourceValue = "";
            if (itemId == R.id.source_kniga_v_uhe) {
                sourceValue = getStringById(R.string.kniga_v_uhe_value);
            } else if (itemId == R.id.source_izi_book) {
                sourceValue = getStringById(R.string.izibuc_value);
            } else if (itemId == R.id.source_audio_book_mp3) {
                sourceValue = getStringById(R.string.audiobook_mp3_value);
            } else if (itemId == R.id.source_abook) {
                sourceValue = getStringById(R.string.abook_value);
            } else if (itemId == R.id.source_baza_knig) {
                sourceValue = getStringById(R.string.baza_knig_value);
            } else if (itemId == R.id.source_knigoblud){
                sourceValue = getStringById(R.string.knigoblud_value);
            } else if (itemId == R.id.source_audioboo){
                sourceValue = getStringById(R.string.audioboo_value);
            } else if (itemId == R.id.source_otrub){
                sourceValue = getStringById(R.string.otrub_value);
            } else if (itemId == R.id.source_poleknig){
                sourceValue = getStringById(R.string.poleknig_value);
            }
            editor.putString("sorce_books", sourceValue);
            editor.apply();
            // Обновляем статическое поле сразу — процесс не перезапускается
            Consts.setSOURCE(mContext, sourceValue);
            getViewState().recreate();
        }

        if (mUrl.contains(Url.SERVER)) {
            if (!mUrl.contains("genre")) {
                if (itemId == R.id.new_data) {
                    url = Url.NEW_BOOK;
                } else if (itemId == R.id.reting_all_time) {
                    url = Url.RATING_ALL_TIME;
                } else if (itemId == R.id.reting_month) {
                    url = Url.RATING_MONTH;
                } else if (itemId == R.id.reting_week) {
                    url = Url.RATING_WEEK;
                } else if (itemId == R.id.reting_day) {
                    url = Url.RATING_TODATY;
                } else if (itemId == R.id.popular_all_time) {
                    url = Url.BEST_ALL_TIME;
                } else if (itemId == R.id.popular_month) {
                    url = Url.BEST_MONTH;
                } else if (itemId == R.id.popular_week) {
                    url = Url.BEST_WEEK;
                } else if (itemId == R.id.popular_day) {
                    url = Url.BEST_TODAY;
                }
            } else {
                url = mUrl.substring(0, Consts.indexOfByNumber(mUrl, '/', 5) + 1);
                if (itemId == R.id.new_data) {
                    url = url + "<page>/";
                } else if (itemId == R.id.reting_all_time) {
                    url = url + "rating/<page>/?period=alltime";
                } else if (itemId == R.id.reting_month) {
                    url = url + "rating/<page>/?period=month";
                } else if (itemId == R.id.reting_week) {
                    url = url + "rating/<page>/?period=week";
                } else if (itemId == R.id.reting_day) {
                    url = url + "rating/<page>/?period=today";
                } else if (itemId == R.id.popular_all_time) {
                    url = url + "popular/<page>/?period=alltime";
                } else if (itemId == R.id.popular_month) {
                    url = url + "popular/<page>/?period=month";
                } else if (itemId == R.id.popular_week) {
                    url = url + "popular/<page>/?period=week";
                } else if (itemId == R.id.popular_day) {
                    url = url + "popular/<page>/?period=today";
                }
            }
        } else if (mUrl.contains(Url.SERVER_IZIBUK)) {
            if (itemId == R.id.new_data) {
                Consts.izibuk_reiting = false;
                url = Url.INDEX_IZIBUK;
            } else if (itemId == R.id.popular || itemId == R.id.popular_all_time
                    || itemId == R.id.popular_day || itemId == R.id.popular_week
                    || itemId == R.id.popular_month
                    || itemId == R.id.reting || itemId == R.id.reting_all_time
                    || itemId == R.id.reting_day || itemId == R.id.reting_week
                    || itemId == R.id.reting_month) {
                Consts.izibuk_reiting = true;
                url = Url.INDEX_IZIBUK;
            }
        } else if (mUrl.contains(Url.SERVER_ABMP3)) {
            if (itemId == R.id.new_data) {
                url = Url.INDEX_ABMP3;
            } else if (itemId == R.id.abmp3_top100) {
                url = Url.ABMP3_TOP;
            } else if (itemId == R.id.abmp3_random) {
                url = Url.ABMP3_RANDOM;
            }
        } else if (mUrl.contains(Url.SERVER_AKNIGA)) {
            if (itemId == R.id.new_data) {
                url = Url.NEW_BOOK_AKNIGA;
            } else if (itemId == R.id.popular_day) {
                url = Url.BEST_TODAY_AKNIGA;
            } else if (itemId == R.id.popular_week) {
                url = Url.BEST_WEEK_AKNIGA;
            } else if (itemId == R.id.popular_month) {
                url = Url.BEST_MONTH_AKNIGA;
            } else if (itemId == R.id.popular_year) {
                url = Url.BEST_YEAR_AKNIGA;
            } else if (itemId == R.id.popular_all_time || itemId == R.id.popular) {
                url = Url.BEST_ALL_TIME_AKNIGA;
            } else if (itemId == R.id.reting_day) {
                url = Url.RATING_TODATY_AKNIGA;
            } else if (itemId == R.id.reting_week) {
                url = Url.RATING_WEEK_AKNIGA;
            } else if (itemId == R.id.reting_month) {
                url = Url.RATING_MONTH_AKNIGA;
            } else if (itemId == R.id.reting_year) {
                url = Url.RATING_YEAR_AKNIGA;
            } else if (itemId == R.id.reting_all_time || itemId == R.id.reting) {
                url = Url.RATING_ALL_TIME_AKNIGA;
            } else if (itemId == R.id.collections_new) {
                url = Url.COLLECTIONS_AKNIGA;
            } else if (itemId == R.id.collections_top) {
                url = Url.COLLECTIONS_TOP_AKNIGA;
            } else if (itemId == R.id.collections_discussed) {
                url = Url.COLLECTIONS_DISCUSSED_AKNIGA;
            } else {
                url = mUrl;
            }
        } else if (mUrl.contains(Url.SERVER_BAZA_KNIG)) {
            if (itemId == R.id.new_data) {
                url = Url.NEW_BOOK_BAZA_KNIG;
            } else if (itemId == R.id.reting) {
                url = Url.RATING_BAZA_KNIG;
            } else if (itemId == R.id.popular) {
                url = Url.BEST_BAZA_KNIG;
            } else if (itemId == R.id.coments) {
                url = Url.COMENTS_BAZA_KNIG;
            } else if (itemId == R.id.years) {
                url = Url.YEARS_BAZA_KNIG;
            }
        } else if (mUrl.contains(Url.SERVER_AUDIOBOO)) {
            url = mUrl;
        } else if (mUrl.contains(Url.SERVER_OTRUB)) {
            if (itemId == R.id.new_data) {
                url = Url.NEW_BOOK_OTRUB;
            } else if (itemId == R.id.popular_week || itemId == R.id.reting_week) {
                url = Url.BEST_WEEK_OTRUB;
            } else if (itemId == R.id.popular_month || itemId == R.id.reting_month) {
                url = Url.BEST_MONTH_OTRUB;
            } else {
                url = mUrl;
            }
        } else if (mUrl.contains(Url.SERVER_POLEKNIG)) {
            if (itemId == R.id.new_data) {
                url = Url.NEW_BOOK_POLEKNIG;
            } else if (itemId == R.id.reting || itemId == R.id.reting_all_time
                    || itemId == R.id.reting_month || itemId == R.id.reting_week
                    || itemId == R.id.reting_day || itemId == R.id.popular
                    || itemId == R.id.popular_all_time || itemId == R.id.popular_month
                    || itemId == R.id.popular_week || itemId == R.id.popular_day) {
                url = Url.BEST_POLEKNIG;
            } else {
                url = mUrl;
            }
        } else if (mUrl.contains(Url.SERVER_KNIGOBLUD)) {
            if (itemId == R.id.new_data) {
                url = Url.INDEX_KNIGOBLUD;
            } else if (itemId == R.id.popular_week) {
                url = Url.POPULAR_WEEK_KNIGOBLUD;
            } else if (itemId == R.id.popular_month) {
                url = Url.POPULAR_MONTH_KNIGOBLUD;
            } else if (itemId == R.id.knigoblud_today) {
                url = Url.TODAY_KNIGOBLUD;
            } else if (itemId == R.id.knigoblud_trending) {
                url = Url.TRENDING_KNIGOBLUD;
            } else if (itemId == R.id.knigoblud_finished) {
                url = Url.FINISHED_KNIGOBLUD;
            } else {
                url = mUrl;
            }
        }

        if (itemId == R.id.app_bar_search) {
            if (mUrl.contains(Url.SERVER_ABMP3)) {
                if (mModelId == Consts.MODEL_AUTOR) {
                    getViewState().showSearchActivity(Consts.MODEL_AUTOR);
                } else {
                    getViewState().showSearchActivity(Consts.MODEL_BOOKS);
                }
            } else {
                getViewState().showSearchActivity(mModelId);
            }
        } else if (itemId == R.id.new_data) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url, R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_new), Consts.MODEL_BOOKS),
                    "audioBooksOrederNew");
        } else if (itemId == R.id.reting_all_time || itemId == R.id.reting) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                    "audioBooksOrederBestAllTime");
        } else if (itemId == R.id.reting_month) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                    "audioBooksOrederBestMonth");
        } else if (itemId == R.id.reting_week) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                    "audioBooksOrederBestWeek");
        } else if (itemId == R.id.reting_day) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting), Consts.MODEL_BOOKS),
                    "audioBooksOrederBestDay");
                            } else if (itemId == R.id.reting_year) {
                                getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_reting),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederBestMonth");
                            } else if (itemId == R.id.popular_all_time || itemId == R.id.popular) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedAllTime");
        } else if (itemId == R.id.popular_month) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedMonth");
        } else if (itemId == R.id.popular_week) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedWeek");
        } else if (itemId == R.id.popular_day) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedDay");
        } else if (itemId == R.id.popular_year) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedMonth");
                            } else if (itemId == R.id.collections_new) {
                                getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_new),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederNew");
                            } else if (itemId == R.id.collections_top) {
                                getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_popular),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederDiscussedAllTime");
                            } else if (itemId == R.id.collections_discussed) {
                                getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_discussed),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederBestAllTime");
                            } else if (itemId == R.id.coments) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_coments),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederComents");
        } else if (itemId == R.id.years) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_year),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrederYears");
        } else if (itemId == R.id.sort_title) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_title),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrderTitle");
        } else if (itemId == R.id.sort_duration) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.order_duration),
                            Consts.MODEL_BOOKS),
                    "audioBooksOrderDuration");
        } else if (itemId == R.id.knigoblud_today) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.knigoblud_today),
                            Consts.MODEL_BOOKS),
                    "knigobludToday");
        } else if (itemId == R.id.knigoblud_trending) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.knigoblud_trending),
                            Consts.MODEL_BOOKS),
                    "knigobludTrending");
        } else if (itemId == R.id.knigoblud_finished) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.knigoblud_finished),
                            Consts.MODEL_BOOKS),
                    "knigobludFinished");
        } else if (itemId == R.id.abmp3_top100) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.abmp3_top100),
                            Consts.MODEL_BOOKS),
                    "abmp3Top100");
        } else if (itemId == R.id.abmp3_random) {
            getViewState().showFragment(BooksFragment.newInstance(
                            url,
                            R.string.menu_audiobooks,
                            subTitle + " " + getStringById(R.string.abmp3_random),
                            Consts.MODEL_BOOKS),
                    "abmp3Random");
        }
            }


    @Override
    public void onGenreItemClick(View view, int position) {
        String tag;
        switch (mModelId) {
            case Consts.MODEL_GENRE:
                tag = "genreBooks";
                break;
            case Consts.MODEL_AUTOR:
                tag = "autorBooks";
                break;
            case Consts.MODEL_ARTIST:
                tag = "artistBooks";
                break;
            default:
                return;
        }

        if (genre != null && genre.size() - 1 >= position) {
            String url = genre.get(position).getUrl();
            getViewState().showFragment(BooksFragment.newInstance(
                    url,
                    R.string.menu_audiobooks,
                    genre.get(position).getName(), Consts.MODEL_BOOKS), tag);
        }
    }

    @Override
    public void onBookItemClick(View view, int position) {
        getViewState().showBooksActivity(books.get(position));
    }

    @Override
    public void onRefresh() {
        if (!isLoading) {
            isRefreshing = true;
            getViewState().showRefreshing(true);
            getData(1); // the cursor moves only when page 1 arrives
        } else {
            getViewState().showRefreshing(false);
        }
    }


    private void getData(int page) {
        if (isLoading) {
            return;
        }
        isLoading = true;
        String url = pageUrl(page);
        if (mModelId == Consts.MODEL_BOOKS) {
            mDisposables.add(mModelBook.getBooks(url, page, mSubTitle.trim(), mContext.getApplicationContext())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> onPage(page, result, mBookCursor, () -> {
                                books = mBookCursor.items();
                                getViewState().showDataBooks(books);
                            }),
                            e -> onPage(page, ProviderPage.<BookPOJO>failed(e), mBookCursor, null)));
        } else {
            mDisposables.add(mModelGenre.getBooks(url, page)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(result -> onPage(page, result, mGenreCursor, () -> {
                                genre = mGenreCursor.items();
                                getViewState().showDataGenres(genre);
                            }),
                            e -> onPage(page, ProviderPage.<GenrePOJO>failed(e), mGenreCursor, null)));
        }
    }

    /**
     * Applies one page: a failure (of a refresh too) keeps cursor and list and re-asks the same page next
     * time; a success moves the cursor, page 1 replacing the list and its dedupe set ({@link ListingCursor}).
     */
    private <T> void onPage(int page, ProviderPage<T> result, ListingCursor<T> cursor, Runnable show) {
        try {
            if (result.isFailure()) {
                if (result.status == ProviderPage.Status.BLOCKED) {
                    getViewState().showToast(CookesExeption.messageFor(mUrl));
                } else {
                    getViewState().showToast(R.string.error_load_data);
                }
                return;
            }
            cursor.apply(page, result);
            show.run();
            if (isRefreshing) {
                getViewState().setPosition(0);
            }
        } finally {
            isLoading = false;
            isRefreshing = false;
            getViewState().showProgres(false);
            getViewState().showRefreshing(false);
        }
    }

    private String getStringById(int id) {
        return mContext.getString(id);
    }

    @Override
    public void onActivityResult(@NonNull Intent intent) {
        String url = intent.getStringExtra("url");
        String name = intent.getStringExtra("name");
        int modelId = intent.getIntExtra("modelId", 0);
        String tag = intent.getStringExtra("tag");

        if (url != null) {
            Fragment fragment = BooksFragment.newInstance(url,
                    R.string.menu_audiobooks, name, modelId);
            getViewState().showFragment(fragment, tag);
        }

    }
}
