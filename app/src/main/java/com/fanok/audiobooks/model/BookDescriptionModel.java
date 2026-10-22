package com.fanok.audiobooks.model;




import static com.fanok.audiobooks.App.useProxy;
import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.interface_pacatge.book_content.DescriptionModel;
import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.DescriptionPOJO;
import com.fanok.audiobooks.pojo.OtherArtistPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.ArrayList;
import java.util.Objects;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class BookDescriptionModel implements DescriptionModel {

    private final String mUrl;

    private Document mDocument;

    public BookDescriptionModel(String url) {
        mUrl = url;
    }

    private Document getDocument() {
        return mDocument;
    }

    @UnstableApi
    @Override
    public Observable<ArrayList<BookPOJO>> getBooks() {
        return Observable.create(observableEmitter -> {
            ArrayList<BookPOJO> bookPOJOArrayList;
            try {
                if (mUrl.contains(Url.SERVER)) {
                    bookPOJOArrayList = loadBooks();
                } else if (mUrl.contains(Url.SERVER_ABMP3)) {
                    bookPOJOArrayList = loadBooksABMP3();
                } else if (mUrl.contains(Url.SERVER_AKNIGA)) {
                    bookPOJOArrayList = loadBooksAbook();
                } else if (mUrl.contains(Url.SERVER_BAZA_KNIG)) {
                    bookPOJOArrayList = loadBooksBazaKnig();
                } else if (mUrl.contains(Url.SERVER_AUDIOBOO)) {
                    bookPOJOArrayList = loadBooksAudioboo();
                }else {
                    bookPOJOArrayList = new ArrayList<>();
                }
                observableEmitter.onNext(bookPOJOArrayList);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    @UnstableApi
    @Override
    public Observable<DescriptionPOJO> getDescription() {
        return Observable.create(observableEmitter -> {
            DescriptionPOJO articlesModels = null;
            try {
                if (mUrl.contains(Url.SERVER)) {
                    articlesModels = loadDescription();
                } else if (mUrl.contains(Url.SERVER_IZIBUK)) {
                    articlesModels = loadDescriptionIzibuk();
                } else if (mUrl.contains(Url.SERVER_ABMP3)) {
                    articlesModels = loadDescriptionABMP3();
                } else if (mUrl.contains(Url.SERVER_AKNIGA)) {
                    articlesModels = loadDescriptionAkniga();
                } else if (mUrl.contains(Url.SERVER_BAZA_KNIG)) {
                    articlesModels = loadDescriptionBazaKnig();
                } else if (mUrl.contains(Url.SERVER_KNIGOBLUD)){
                    articlesModels = loadDescriptionKnigoblud();
                } else if (mUrl.contains(Url.SERVER_AUDIOBOO)){
                    articlesModels = loadDescriptionAudioboo();
                } else if (mUrl.contains(Url.SERVER_OTRUB)) {
                    articlesModels = loadDescriptionOtrub();
                } else if (mUrl.contains(Url.SERVER_POLEKNIG)) {
                    articlesModels = loadDescriptionPoleknig();
                } else {
                    articlesModels = new DescriptionPOJO();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (articlesModels == null || articlesModels.getTitle() == null || articlesModels.getTitle().isEmpty() || "Ошибка".equals(articlesModels.getTitle())) {
                if (App.getInstance() != null) {
                    BooksDBModel dbModel = new BooksDBModel(App.getInstance());
                    BookPOJO savedBook = dbModel.getSomewhere(mUrl);
                    dbModel.closeDB();
                    if (savedBook != null) {
                        articlesModels = new DescriptionPOJO();
                        articlesModels.setTitle(savedBook.getName() != null ? savedBook.getName() : "");
                        articlesModels.setAutor(savedBook.getAutor() != null ? savedBook.getAutor() : "");
                        articlesModels.setArtist(savedBook.getArtist() != null ? savedBook.getArtist() : "");
                        articlesModels.setPoster(savedBook.getPhoto() != null ? savedBook.getPhoto() : "");
                        articlesModels.setDescription(savedBook.getDesc() != null ? savedBook.getDesc() : "");
                        articlesModels.setTime(savedBook.getTime() != null ? savedBook.getTime() : "");
                        articlesModels.setReiting(savedBook.getReting() != null ? savedBook.getReting() : "");
                        articlesModels.setSeries(savedBook.getSeries() != null ? savedBook.getSeries() : "");
                        articlesModels.setGenre(savedBook.getGenre() != null ? savedBook.getGenre() : "");
                        articlesModels.setAutorUrl(savedBook.getUrlAutor() != null ? savedBook.getUrlAutor() : "");
                        articlesModels.setArtistUrl(savedBook.getUrlArtist() != null ? savedBook.getUrlArtist() : "");
                    }
                }
            }

            if (articlesModels == null) {
                articlesModels = new DescriptionPOJO();
                articlesModels.setTitle("Ошибка загрузки");
            }

                if (articlesModels == null) {
                    articlesModels = new DescriptionPOJO();
                }
                observableEmitter.onNext(articlesModels);
            observableEmitter.onComplete();
        });
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksBazaKnig() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Element root = document.getElementById("releative-slider");
        if (root != null) {
            Elements elements = root.getElementsByTag("a");
            if (elements != null && elements.size() > 0) {
                for (Element book : elements) {
                    BookPOJO bookPOJO = new BookPOJO();

                    String url = book.attr("href");
                    if (url != null && !url.isEmpty()) {
                        bookPOJO.setUrl(url);
                    }

                    Elements img = book.getElementsByTag("img");
                    if (img != null && img.size() > 0) {
                        String src = img.first().attr("src");
                        if (src != null&&!src.isEmpty()) {
                            if(src.contains(Url.SERVER_BAZA_KNIG)){
                                bookPOJO.setPhoto(src);
                            }else {
                                bookPOJO.setPhoto(Url.SERVER_BAZA_KNIG+src);
                            }
                        }
                        String name = img.first().attr("alt");
                        if (name != null && !name.isEmpty()) {
                            bookPOJO.setName(name);
                        }

                    }

                    if (!bookPOJO.isNull()) {
                        books.add(bookPOJO);
                    }
                }
            }
        }

        return books;
    }


    @UnstableApi
    private ArrayList<BookPOJO> loadBooksAudioboo() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Element root = document.getElementById("owl-rels");
        if (root != null) {
            Elements elements = root.getElementsByClass("popular-item");
            for (Element book : elements) {
                BookPOJO bookPOJO = new BookPOJO();
                Element a = book.getElementsByTag("a").first();
                if (a!=null){
                    String url = a.attr("href");
                    bookPOJO.setUrl(url);
                    Element name = a.getElementsByClass("popular-item-title anim").first();
                    if (name!=null) {
                        bookPOJO.setName(name.text());
                    }
                    Element img = a.getElementsByTag("img").first();
                    if (img!=null) {
                        String src = img.attr("data-src");
                        bookPOJO.setPhoto(Url.SERVER_AUDIOBOO+src);
                    }
                }
                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }

        }
        return books;
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooks() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();
        Elements root = document.getElementsByClass("suggested");
        if (root.size() > 0) {
            Elements booksConteiner = root.get(0).getElementsByTag("td");
            for (int i = 0; i < booksConteiner.size(); i++) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                if (aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(Url.SERVER + a.attr("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img.size() > 0) {
                        String imgUrl = img.first().attr("src");
                        int lastPos = imgUrl.indexOf("?");
                        if (lastPos != -1) {
                            imgUrl = imgUrl.substring(0, lastPos);
                        }
                        bookPOJO.setPhoto(imgUrl);
                    }
                    Elements name = a.getElementsByClass("suggested_book_name");
                    if (name.size() > 0) {
                        bookPOJO.setName(name.first().text());
                    }
                }
                books.add(bookPOJO);
            }
        }

        return books;
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksABMP3() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements root = document.getElementsByClass("similar-abooks-block");
        if (root != null && root.size() > 0) {
            Elements booksConteiner = root.get(0).getElementsByClass("abook-similar-item");
            if (booksConteiner != null) {
                for (int i = 0; i < booksConteiner.size(); i++) {
                    BookPOJO bookPOJO = new BookPOJO();
                    Elements aTag = booksConteiner.get(i).getElementsByTag("a");
                    if (aTag != null && aTag.size() > 0) {
                        Element a = aTag.first();
                        bookPOJO.setUrl(Url.SERVER_ABMP3 + a.attr("href"));
                        Elements img = a.getElementsByTag("img");
                        if (img != null && img.size() > 0) {
                            String imgUrl = img.first().attr("src");
                            bookPOJO.setPhoto(imgUrl);
                        }
                        Elements name = a.getElementsByTag("span");
                        if (name != null && name.size() > 0) {
                            bookPOJO.setName(name.first().text());
                        }
                    }
                    if (!bookPOJO.isNull()) {
                        books.add(bookPOJO);
                    }
                }
            }
        }

        return books;
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksAbook() throws IOException {
        ArrayList<BookPOJO> books = new ArrayList<>();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements root = document.getElementsByClass("block-container");
        if (root != null) {
            Elements booksConteiner = new Elements();
            for (Element element : root) {
                booksConteiner.addAll(element.children());
            }
            for (Element element : booksConteiner) {
                BookPOJO bookPOJO = new BookPOJO();
                Elements aTag = element.getElementsByTag("a");
                if (aTag != null && aTag.size() > 0) {
                    Element a = aTag.first();
                    bookPOJO.setUrl(a.attr("href"));
                    Elements img = a.getElementsByTag("img");
                    if (img != null && img.size() > 0) {
                        String imgUrl = img.first().attr("data-src");
                        if (imgUrl == null || imgUrl.isEmpty()) {
                            imgUrl = img.first().attr("src");
                        }
                        bookPOJO.setPhoto(imgUrl);
                    }

                    Elements name = a.getElementsByClass("caption");
                    if (name != null && name.size() > 0) {
                        bookPOJO.setName(name.first().text());
                    }
                }
                if (!bookPOJO.isNull()) {
                    books.add(bookPOJO);
                }
            }

        }
        return books;
    }

    @UnstableApi
    private DescriptionPOJO loadDescription() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();
        Elements titleElement = document.getElementsByClass("book_title_elem book_title_name");
        if (titleElement.size() != 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim());
        }
        Elements retingElements = document.getElementsByClass("book_actions_plays");
        if (retingElements.size() != 0) {
            descriptionPOJO.setReiting(retingElements.first().text().replaceAll(" ", ""));
        }
        Elements posterElements = document.getElementsByClass("book_cover");
        if (posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img.size() != 0) {
                String imgUrl = img.first().attr("src");
                int lastPos = imgUrl.indexOf("?");
                if (lastPos != -1) {
                    imgUrl = imgUrl.substring(0, lastPos);
                }

                descriptionPOJO.setPoster(imgUrl);
            }
        }

        Elements timeElements = document.getElementsByClass("book_info_label");
        if (timeElements.size() != 0) {
            Node time = timeElements.first().nextSibling();
            if (time != null) {
                descriptionPOJO.setTime(time.toString().trim());
            }
        }

        Elements autorElements = document.getElementsByAttributeValue("itemprop", "author");
        if (autorElements.size() != 0) {
            Elements aElements = autorElements.first().getElementsByTag("a");
            if (aElements.size() != 0) {
                descriptionPOJO.setAutor(aElements.first().text());
                descriptionPOJO.setAutorUrl(Url.SERVER + aElements.first().attr("href"));
            }
        }

        Elements artistElements = document.getElementsByClass("book_title_elem");

        for (int i = 0; i < artistElements.size(); i++) {
            Element element = artistElements.get(i);
            if (element.text().contains("читает") || element.text().contains("читают")) {
                Elements aTag = element.getElementsByTag("a");
                if (aTag.size() != 0) {
                    descriptionPOJO.setArtist(aTag.first().text());
                    descriptionPOJO.setArtistUrl(Url.SERVER + aTag.first().attr("href"));
                }
            }
        }

        Elements seriesSisterElements = document.getElementsByClass("book_serie_block_title");

        for (int i = 0; i < seriesSisterElements.size(); i++) {
            Element element = seriesSisterElements.get(i);
            if (element.text().contains("Цикл")) {
                Elements seriesElement = element.getElementsByTag("a");
                if (seriesElement.size() != 0) {
                    descriptionPOJO.setSeries(seriesElement.first().text());
                    descriptionPOJO.setSeriesUrl(Url.SERVER + seriesElement.first().attr("href"));
                }
            } else if (element.text().contains("Другие варианты озвучки")||element.text().contains("Другие озвучки")) {
                descriptionPOJO.setOtherReader(true);
            }
        }

        Elements descriptionElements = document.getElementsByAttributeValue("itemprop",
                "description");
        if (descriptionElements.size() != 0) {
            descriptionPOJO.setDescription(descriptionElements.first().text().trim());
        }

        Elements genreConteiner = document.getElementsByClass("book_genre_pretitle");
        if (genreConteiner.size() != 0) {
            Elements aTag = genreConteiner.first().getElementsByTag("a");
            if (aTag.size() != 0) {
                Element a = aTag.first();
                descriptionPOJO.setGenre(a.text());
                descriptionPOJO.setGenreUrl(Url.SERVER + a.attr("href") + "<page>/");
            }
        }

        Element favorite = document.getElementById("book_fave_count");
        if (favorite != null) {
            descriptionPOJO.setFavorite(favorite.text());
        }

        Element like = document.getElementById("book_likes_count");
        if (like != null) {
            descriptionPOJO.setLike(like.text());
        }

        Element disLike = document.getElementById("book_dislikes_count");
        if (disLike != null) {
            descriptionPOJO.setDisLike(disLike.text());
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionABMP3() throws IOException {

        String autor = "";

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements img = document.getElementsByClass("abook_image");
        if (img != null && img.size() != 0) {
            descriptionPOJO.setPoster(img.first().attr("src"));
        }

        Elements desc = document.getElementsByClass("abook-desc");
        if (desc != null && desc.size() != 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements infos = document.getElementsByClass("panel-item");
        if (infos != null) {
            for (Element info : infos) {
                if (info.text().contains("Автор")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && element.size() != 0) {
                        autor = element.first().text();
                        descriptionPOJO.setAutor(autor);
                        descriptionPOJO.setAutorUrl(Url.SERVER_ABMP3 + element.first().attr("href") + "?page=");
                    }
                } else if (info.text().contains("Читает")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && element.size() != 0) {
                        descriptionPOJO.setArtist(element.first().text());
                        descriptionPOJO.setArtistUrl(Url.SERVER_ABMP3 + element.first().attr("href") + "?page=");
                    }
                } else if (info.text().contains("Жанры")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && element.size() != 0) {
                        descriptionPOJO.setGenre(element.first().text());
                        descriptionPOJO.setGenreUrl(Url.SERVER_ABMP3 + element.first().attr("href") + "?page=");
                    }
                } else {
                    Elements clock = info.getElementsByClass("fa-clock-o");
                    if (clock != null && clock.size() != 0) {
                        descriptionPOJO.setTime(info.text().trim());
                    }

                    Elements reting = info.getElementsByClass("fa-eye");
                    if (reting != null && reting.size() != 0) {
                        descriptionPOJO.setReiting(info.text().trim());
                    }

                }
            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (titleElement.size() != 0) {
            String name = titleElement.first().text().trim();
            if (autor.isEmpty()) {
                descriptionPOJO.setTitle(name);
            } else {
                descriptionPOJO.setTitle(name.replace(autor + " - ", ""));
            }
        }

        Elements like = document.getElementsByClass("vote-count");
        if (!like.isEmpty()) {
            descriptionPOJO.setLike(Objects.requireNonNull(like.first()).text());
        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistABMP3(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(!arrayList.isEmpty());
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionKnigoblud() throws IOException {

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Element img = document.getElementById("BookCoverImage");
        if (img != null) {
            descriptionPOJO.setPoster(img.attr("src"));
        }

        Elements desc = document.getElementsByClass("BookDescriptionContent");
        if (desc != null && desc.size() != 0) {
            descriptionPOJO.setDescription(desc.first().ownText());
        }

        Elements clock = document.getElementsByClass("PageTitle_Subtitle");
        if (clock != null && clock.size() != 0) {
            descriptionPOJO.setTime(clock.first().ownText());
        }

        Elements seriesConteiner = document.getElementsByClass("BookDescription BookSeries");
        if (seriesConteiner!=null&&seriesConteiner.size()!=0){
            Elements series = seriesConteiner.first().getElementsByTag("a");
            if (series!=null&&series.size()!=0){
                descriptionPOJO.setSeries(series.first().text());
                descriptionPOJO.setSeriesUrl(Url.SERVER_KNIGOBLUD + series.first().attr("href"));
            }
        }


        Elements elements = document.getElementsByClass("BookMetaBlock");
        if(!elements.isEmpty()) {
            Elements infos = Objects.requireNonNull(elements.first()).getElementsByClass("BookMetaBlockLine");
            for (Element info : infos) {
                if (info.text().contains("✍")) {
                    Elements element = info.getElementsByTag("a");
                    if (!element.isEmpty()) {
                        String autor = Objects.requireNonNull(element.first()).text();
                        descriptionPOJO.setAutor(autor);
                        descriptionPOJO.setAutorUrl(Url.SERVER_KNIGOBLUD + Objects.requireNonNull(element.first()).attr("href"));
                    }
                } else if (info.text().contains("\uD83C\uDF99")) {
                    Elements element = info.getElementsByTag("a");
                    if (!element.isEmpty()) {
                        descriptionPOJO.setArtist(Objects.requireNonNull(element.first()).text());
                        descriptionPOJO.setArtistUrl(Url.SERVER_KNIGOBLUD + Objects.requireNonNull(element.first()).attr("href"));
                    }
                } else if (info.text().contains("\uD83D\uDCD5")) {
                    Elements element = info.getElementsByTag("a");
                    if (!element.isEmpty()) {
                        descriptionPOJO.setGenre(Objects.requireNonNull(element.first()).text());
                        descriptionPOJO.setGenreUrl(Url.SERVER_KNIGOBLUD + Objects.requireNonNull(element.first()).attr("href"));
                    }
                }
            }
        }

        Elements titleElement = document.getElementsByTag("h1");
        if (!titleElement.isEmpty()) {
            String name = Objects.requireNonNull(titleElement.first()).text().trim();
            descriptionPOJO.setTitle(name);
        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistKnigoblud(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(!arrayList.isEmpty());
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionOtrub() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        setDocument();
        if (mDocument != null) {
            Elements titleElement = mDocument.select("h1 span[itemprop=name]");
            if (titleElement != null && titleElement.size() > 0) {
                descriptionPOJO.setTitle(titleElement.first().text().trim());
            }

            Elements img = mDocument.select("._5e0b77 img");
            if (img != null && img.size() > 0) {
                descriptionPOJO.setPoster(img.first().attr("src"));
            }

            Elements infoBlocks = mDocument.select("._72ce76 ._5bef20");
            if (infoBlocks != null) {
                for (Element block : infoBlocks) {
                    String text = block.text();
                    Element a = block.select("a").first();
                    if (text.contains("Жанр") && a != null) {
                        descriptionPOJO.setGenre(a.text());
                        String href = a.attr("href");
                        if (href != null && !href.isEmpty()) {
                            descriptionPOJO.setGenreUrl(Url.SERVER_OTRUB + href);
                        }
                    } else if (text.contains("Автор") && a != null) {
                        descriptionPOJO.setAutor(a.text());
                        String href = a.attr("href");
                        if (href != null && !href.isEmpty()) {
                            descriptionPOJO.setAutorUrl(Url.SERVER_OTRUB + href);
                        }
                    } else if (text.contains("Читает") && a != null) {
                        descriptionPOJO.setArtist(a.text());
                        String href = a.attr("href");
                        if (href != null && !href.isEmpty()) {
                            descriptionPOJO.setArtistUrl(Url.SERVER_OTRUB + href);
                        }
                    } else if ((text.contains("Цикл") || text.contains("Серия")) && a != null) {
                        descriptionPOJO.setSeries(a.text());
                        String href = a.attr("href");
                        if (href != null && !href.isEmpty()) {
                            if (href.startsWith("/")) href = Url.SERVER_OTRUB + href;
                            descriptionPOJO.setSeriesUrl(href);
                        }
                    } else if (text.contains("Длительность")) {
                        String time = text.replace("Длительность:", "").trim();
                        if (!time.isEmpty()) {
                            descriptionPOJO.setTime(time);
                        }
                    }
                }
            }

            if (descriptionPOJO.getSeries() == null || descriptionPOJO.getSeries().isEmpty()) {
                Elements seriesElements = mDocument.select("a[href*=/series/]");
                if (seriesElements != null && !seriesElements.isEmpty()) {
                    Element a = seriesElements.first();
                    descriptionPOJO.setSeries(a.text());
                    String href = a.attr("href");
                    if (href != null && !href.isEmpty()) {
                        if (href.startsWith("/")) href = Url.SERVER_OTRUB + href;
                        descriptionPOJO.setSeriesUrl(href);
                    }
                }
            }

            Elements desc = mDocument.select("._034fb7[itemprop=description]");
            if (desc != null && desc.size() > 0) {
                descriptionPOJO.setDescription(desc.first().text());
            }
        }
        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionAudioboo() throws IOException {

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Element descContainer = document.getElementsByClass("page__text").first();
        if (descContainer != null) {
            Element img = descContainer.getElementsByTag("img").first();
            if (img != null) {
                String src = img.attr("data-src");
                if (src != null && !src.isEmpty()) {
                    if (src.startsWith("http")) {
                        descriptionPOJO.setPoster(src);
                    } else {
                        descriptionPOJO.setPoster(Url.SERVER_AUDIOBOO + src);
                    }
                }
            }
            String descText = descContainer.text();
            descriptionPOJO.setDescription(descText);
            
            if (descText.contains("Продолжительность:")) {
                String timeStr = descText.substring(descText.indexOf("Продолжительность:") + "Продолжительность:".length()).trim();
                if (timeStr.contains(" ")) {
                    timeStr = timeStr.substring(0, timeStr.indexOf(" "));
                }
                descriptionPOJO.setTime(timeStr);
            }
        }

        Elements infos = document.getElementsByClass("duration list");
        if (infos != null) {
            for (Element info : infos) {
                String text = info.text();
                Element a = info.getElementsByTag("a").first();
                if (a != null) {
                    if (text.contains("Автор")) {
                        descriptionPOJO.setAutor(a.text());
                        descriptionPOJO.setAutorUrl(a.attr("href"));
                    } else if (text.contains("Исполнитель")) {
                        descriptionPOJO.setArtist(a.text());
                        descriptionPOJO.setArtistUrl(a.attr("href"));
                    } else if (text.contains("Цикл")) {
                        descriptionPOJO.setSeries(a.text());
                        descriptionPOJO.setSeriesUrl(a.attr("href"));
                    } else if (text.contains("Жанр")) {
                        descriptionPOJO.setGenre(a.text());
                        descriptionPOJO.setGenreUrl(a.attr("href"));
                    }
                }
            }
        }

        Element titleElement = document.getElementsByTag("h1").first();
        if (titleElement != null) {
            String name = titleElement.text().trim();
            String author = descriptionPOJO.getAutor();
            if (author != null && !author.isEmpty() && name.contains(author + " - ")) {
                name = name.replace(author + " - ", "");
            }
            descriptionPOJO.setTitle(name);
        }

        Element ratingContainer = document.getElementsByClass("figstar").first();
        if (ratingContainer != null) {
            descriptionPOJO.setReiting(ratingContainer.text().trim());
        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistAudioboo(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(!arrayList.isEmpty());
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionAkniga() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();
        String autor = "";

        Elements titleElement = document.getElementsByClass("caption__article-main");

        Elements autorElements = document.getElementsByClass("about-author");
        if (!autorElements.isEmpty()) {
            Elements aElements = Objects.requireNonNull(autorElements.first()).getElementsByTag("a");
            if (!aElements.isEmpty()) {
                autor = Objects.requireNonNull(aElements.first()).text();
                if (!autor.isEmpty()) {
                    descriptionPOJO.setAutor(autor);
                }
                String href = Objects.requireNonNull(aElements.first()).attr("href");
                if (!href.isEmpty()) {
                    descriptionPOJO.setAutorUrl(href + "page<page>/");
                }
            }
        }

        if (titleElement != null && titleElement.size() != 0) {
            descriptionPOJO.setTitle(titleElement.first().text().trim().replace(autor + " - ", ""));
        }

        Elements posterElements = document.getElementsByClass("cover__wrapper--image");
        if (posterElements != null && posterElements.size() != 0) {
            Elements img = posterElements.first().getElementsByTag("img");
            if (img != null && img.size() != 0) {
                String imgUrl = img.first().attr("src");
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    descriptionPOJO.setPoster(imgUrl);
                }
            }
        }
        if (descriptionPOJO.getPoster() == null || descriptionPOJO.getPoster().isEmpty()) {
            Elements bgElems = document.getElementsByClass("collection-header-bg");
            if (bgElems == null || bgElems.isEmpty()) {
                bgElems = document.select("[style*='background-image']");
            }
            if (bgElems != null && !bgElems.isEmpty()) {
                for (Element bgElem : bgElems) {
                    String style = bgElem.attr("style");
                    if (style != null && style.contains("url(")) {
                        int start = style.indexOf("url(");
                        int end = style.indexOf(")", start);
                        if (start >= 0 && end > start) {
                            String imgUrl = style.substring(start + 4, end).replace("'", "").replace("\"", "").trim();
                            if (!imgUrl.isEmpty()) {
                                if (imgUrl.startsWith("//")) imgUrl = "https:" + imgUrl;
                                else if (imgUrl.startsWith("/")) imgUrl = Url.SERVER_AKNIGA + imgUrl;
                                descriptionPOJO.setPoster(imgUrl);
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (descriptionPOJO.getPoster() == null || descriptionPOJO.getPoster().isEmpty()) {
            Elements metaImg = document.select("meta[property=og:image], meta[name=twitter:image]");
            if (metaImg != null && !metaImg.isEmpty()) {
                String imgUrl = metaImg.first().attr("content");
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    if (imgUrl.startsWith("//")) imgUrl = "https:" + imgUrl;
                    else if (imgUrl.startsWith("/")) imgUrl = Url.SERVER_AKNIGA + imgUrl;
                    descriptionPOJO.setPoster(imgUrl);
                }
            }
        }

        String time = "";
        Elements timeElements = document.getElementsByClass("hours");
        if (timeElements != null && timeElements.size() != 0) {
            time = timeElements.first().text();
        }

        timeElements = document.getElementsByClass("minutes");
        if (timeElements != null && timeElements.size() != 0) {
            time += " " + timeElements.first().text();
        }
        if (!time.isEmpty()) {
            descriptionPOJO.setTime(time.trim());
        }

        Elements artistElements = document.getElementsByClass("link__reader");
        if (artistElements != null && artistElements.size() != 0) {
            String href = artistElements.first().attr("href");
            if (href != null && !href.isEmpty()) {
                descriptionPOJO.setArtistUrl(href + "page<page>/");
            }
            String name = artistElements.first().text();
            if (name != null && !name.isEmpty()) {
                descriptionPOJO.setArtist(name);
            }
        }

        Elements seriesElements = document.select("a[href*=/series/]");
        if (seriesElements != null && !seriesElements.isEmpty()) {
            Element aTag = seriesElements.first();
            String href = aTag.attr("href");
            if (href != null && !href.isEmpty()) {
                if (href.startsWith("/")) href = Url.SERVER_AKNIGA + href;
                descriptionPOJO.setSeriesUrl(href);
            }
            String title = aTag.text();
            if (title != null && !title.isEmpty()) {
                descriptionPOJO.setSeries(title);
            }
        }

        Elements descriptionElements = document.getElementsByClass("description__article-main");
        if (descriptionElements.size() != 0) {
            StringBuilder descBuilder = new StringBuilder();
            for (Element element : descriptionElements) {
                String t = element.text().trim();
                if (!t.isEmpty()) {
                    descBuilder.append(t).append("\n");
                }
            }
            descriptionPOJO.setDescription(descBuilder.toString().trim());
        } else {
            Elements scripts = document.getElementsByTag("script");
            for (Element script : scripts) {
                if ("application/ld+json".equals(script.attr("type"))) {
                    String json = script.html();
                    if (json.contains("\"description\":\"")) {
                        String desc = json.substring(json.indexOf("\"description\":\"") + 15);
                        int end = desc.indexOf("\"");
                        if (end > 0) {
                            descriptionPOJO.setDescription(desc.substring(0, end));
                            break;
                        }
                    }
                }
            }
        }

        Elements genreConteiner = document.getElementsByClass("section__title");
        if (genreConteiner != null && genreConteiner.size() != 0) {
            Element a = genreConteiner.first();
            String href = a.attr("href");
            if (href != null && !href.isEmpty()) {
                descriptionPOJO.setGenreUrl(href + "page<page>/");
            }
            String text = a.text();
            if (text != null && !text.isEmpty()) {
                descriptionPOJO.setGenre(text);
            }
        }

        Elements retingElements = document
                .getElementsByClass("link__action--label link__action--label--views pull-right");
        if (retingElements != null && retingElements.size() != 0) {
            descriptionPOJO.setReiting(retingElements.first().text().trim());
        }

        Elements parents = document.getElementsByClass("cover__wrapper--buttons");
        if (parents != null && parents.size() != 0) {
            Element parent = parents.first();

            Elements favorite = parent.getElementsByClass("ls-favourite-count");
            if (favorite != null && favorite.size() != 0) {
                descriptionPOJO.setFavorite(favorite.first().text().trim());
            }

            Elements like = parent.getElementsByClass("js-vote-rating-up");
            if (like != null && like.size() != 0) {
                descriptionPOJO.setLike(like.first().text().trim());
            }

            Elements disLike = parent.getElementsByClass("js-vote-rating-down");
            if (disLike != null && disLike.size() != 0) {
                descriptionPOJO.setDisLike(disLike.first().text().trim());
            }

        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistAbook(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(arrayList.size() != 0);
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionBazaKnig() throws IOException {

        int indexSorce = mUrl.indexOf("?sorce");
        int sorce = 1;
        if (indexSorce != -1) {
            String substring = mUrl.substring(indexSorce).replace("?sorce=", "");
            sorce = Integer.parseInt(substring);
        }

        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        if (document.title().contains("Just a moment")) {
            throw new CookesExeption(document.location());
        }

        Elements cont = document.getElementsByClass("reset full-items");
        if (cont != null && cont.size() > 0) {
            Elements liElem = cont.first().getElementsByTag("li");
            if (liElem != null && liElem.size() > 0) {
                for (Element li : liElem) {
                    String liText = li.text();
                    if (liText != null) {
                        if (liText.contains("Автор")) {
                            Elements aTag = li.getElementsByTag("a");
                            if (aTag != null && aTag.size() > 0) {
                                String aHref = aTag.first().attr("href");
                                if (aHref != null) {
                                    descriptionPOJO.setAutorUrl(aHref + "page/");
                                }
                                String autorName = aTag.first().text();
                                if (autorName != null && !autorName.isEmpty()) {
                                    descriptionPOJO.setAutor(autorName);

                                }
                            }
                        }

                        if (liText.contains("Читает")) {

                            if (sorce == 1) {
                                Elements aTag = li.getElementsByTag("a");
                                if (aTag != null && aTag.size() > 0) {
                                    String aHref = aTag.first().attr("href");
                                    if (aHref != null) {
                                        descriptionPOJO.setArtistUrl(aHref + "page/");
                                    }
                                    String artistName = aTag.first().text();
                                    if (artistName != null && !artistName.isEmpty()) {
                                        descriptionPOJO.setArtist(artistName);

                                    }
                                }
                            } else if (sorce == 2) {
                                Element otherReader = document.getElementById("content-tab2");
                                if (otherReader != null) {
                                    String text = otherReader.ownText();
                                    if (text != null && text.contains("Озвучивает:")) {
                                        String name = text.replace("Озвучивает:", "").trim();
                                        descriptionPOJO.setArtist(name);
                                    }
                                }
                            } else if (sorce == 3) {
                                Element otherReader = document.getElementById("content-tab3");
                                if (otherReader != null) {
                                    String text = otherReader.ownText();
                                    if (text != null && text.contains("Озвучивает:")) {
                                        String name = text.replace("Озвучивает:", "").trim();
                                        descriptionPOJO.setArtist(name);
                                    }
                                }
                            }
                        }

                        if (liText.contains("Длительность")) {
                            Elements timeConteiner = li.getElementsByTag("b");
                            if (timeConteiner != null && timeConteiner.size() > 0) {
                                String time = timeConteiner.first().text();
                                if (time != null && !time.isEmpty()) {
                                    descriptionPOJO.setTime(time);
                                }
                            }
                        }

                        if (liText.contains("Цикл")) {
                            Elements seriesConteiner = li.getElementsByTag("a");
                            if (seriesConteiner != null && seriesConteiner.size() > 0) {
                                Element a = seriesConteiner.first();
                                String text = a.text();
                                if (text != null && !text.isEmpty()) {
                                    descriptionPOJO.setSeries(text);
                                    String href = a.attr("href");
                                    if (href != null) {
                                        descriptionPOJO.setSeriesUrl(href + "page/");
                                    }
                                }
                            }
                        }

                        if (liText.contains("Жанр")) {
                            Elements genreConteiner = li.getElementsByTag("a");
                            if (genreConteiner != null && genreConteiner.size() > 0) {
                                String text = genreConteiner.first().text();
                                if (text != null && !text.isEmpty()) {
                                    descriptionPOJO.setGenre(text);
                                    String href = genreConteiner.first().attr("href");
                                    if (href != null) {
                                        descriptionPOJO.setGenreUrl(href + "page/");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Elements imgConteiner = document.getElementsByClass("full-img");
        if (imgConteiner != null && imgConteiner.size() > 0) {
            Element element = imgConteiner.first();
            Elements img = element.getElementsByTag("img");
            if (img != null && img.size() > 0) {
                String src = img.first().attr("src");
                if (src != null&&!src.isEmpty()) {
                    if(src.contains(Url.SERVER_BAZA_KNIG)){
                        descriptionPOJO.setPoster(src);
                    }else {
                        descriptionPOJO.setPoster(Url.SERVER_BAZA_KNIG+src);
                    }
                }
            }
        }

        Elements title = document.getElementsByTag("h1");
        if (title != null && title.size() > 0) {
            String text = title.first().ownText();
            descriptionPOJO.setTitle(text.substring(0, text.indexOf(" - ")));
        }

        Elements desc = document.getElementsByClass("short-text");
        if (desc != null && desc.size() > 0) {
            String text = desc.first().ownText();
            descriptionPOJO.setDescription(text);
        }

        Elements comentsClass = document.getElementsByClass("short-rate main-sliders-rate ignore-select");
        if (comentsClass != null && comentsClass.size() > 0) {
            Elements elements = comentsClass.first().getElementsByTag("a");
            if (elements != null && elements.size() == 2) {
                String like = elements.first().text();
                if (like != null && !like.isEmpty()) {
                    descriptionPOJO.setLike(like);
                }

                String dislike = elements.last().text();
                if (dislike != null && !dislike.isEmpty()) {
                    descriptionPOJO.setDisLike(dislike);
                }
            }
        }

        try {
            ArrayList<OtherArtistPOJO> arrayList = OtherArtistModel
                    .loadOtherArtistBazaKnig(descriptionPOJO.getTitle(), descriptionPOJO.getAutor(), mUrl,
                            descriptionPOJO.getArtist());
            descriptionPOJO.setOtherReader(arrayList.size() != 0);
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    @UnstableApi
    private DescriptionPOJO loadDescriptionIzibuk() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        if (getDocument() == null) {
            setDocument();
        }
        Document document = getDocument();

        Elements elements = document.getElementsByAttributeValue("itemprop", "name");
        if (elements != null && elements.size() != 0) {
            String name = elements.first().text();
            if (name != null) {
                descriptionPOJO.setTitle(name);
            }
        }

        Elements imgParent = document.getElementsByClass("_5e0b77");
        if (imgParent != null && imgParent.size() != 0) {
            Element img = imgParent.first().child(0);
            if (img != null) {
                String src = img.attr("src");
                if (src != null) {
                    descriptionPOJO.setPoster(src);
                }
            }
        }

        Elements infoParent = document.getElementsByClass("_b264b2");
        if (infoParent != null && infoParent.size() != 0) {
            Elements info = infoParent.first().children();
            if (info != null) {
                for (Element element : info) {
                    String text = element.text();
                    if (text != null) {
                        if (text.contains("Автор")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.attr("href");
                                if (href != null) {
                                    descriptionPOJO.setAutorUrl(Url.SERVER_IZIBUK + href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    descriptionPOJO.setAutor(name);
                                }
                            }
                        } else if (text.contains("Читает")) {
                            Elements aTag = element.getElementsByTag("a");
                            if (aTag != null && aTag.size() != 0) {
                                Element a = aTag.first();
                                String href = a.attr("href");
                                if (href != null) {
                                    descriptionPOJO.setArtistUrl(Url.SERVER_IZIBUK + href + "?p=");
                                }
                                String name = a.text();
                                if (name != null) {
                                    descriptionPOJO.setArtist(name);
                                }
                            }
                        } else if (text.contains("Время")) {
                            String time = text.replace("Время:", "").trim();
                            descriptionPOJO.setTime(time);
                        }
                    }
                }
            }
        }

        Elements seriesParent = document.getElementsByClass("_c337c7");
        if (seriesParent != null && seriesParent.size() != 0) {
            Elements aTag = seriesParent.first().getElementsByTag("a");
            if (aTag != null && aTag.size() != 0) {
                Element a = aTag.first();
                String href = a.attr("href");
                if (href != null) {
                    descriptionPOJO.setSeriesUrl(Url.SERVER_IZIBUK + href + "?p=");
                }
                String text = a.text();
                if (text != null) {
                    descriptionPOJO.setSeries(text);
                }
            }
        }

        Elements genreParent = document.getElementsByClass("_7e215f");
        if (genreParent != null && genreParent.size() != 0) {
            Element genreElement = genreParent.first().child(0);
            if (genreElement != null) {
                String href = genreElement.attr("href");
                if (href != null) {
                    descriptionPOJO.setGenreUrl(Url.SERVER_IZIBUK + href + "?p=");
                }
                String text = genreElement.text();
                if (text != null) {
                    descriptionPOJO.setGenre(text);
                }
            }
        }

        Elements descriptionList = document.getElementsByAttributeValue("itemprop", "description");
        if (descriptionList != null && descriptionList.size() != 0) {
            String text = descriptionList.first().text();
            if (text != null) {
                descriptionPOJO.setDescription(text);
            }
        }


        try {



            Connection connection = Jsoup.connect(
                            Url.SERVER_IZIBUK + "/search?q=" + descriptionPOJO.getTitle() + " "
                                    + descriptionPOJO.getAutor())
                    .userAgent(Consts.USER_AGENT)
                    .referrer("http://www.google.com")
                    .sslSocketFactory(Consts.socketFactory())
                    .maxBodySize(0);

            if(App.useProxy) {
                Proxy proxy = new Proxy(Type.SOCKS,
                        new InetSocketAddress(PROXY_HOST, PROXY_PORT));
                connection.proxy(proxy);
            }

            Document doc = connection.get();

            Element element = doc.getElementById("books_list");
            if (element != null) {
                Elements list = element.getElementsByClass("_ccb9b7");
                if (list != null && list.size() > 1) {
                    descriptionPOJO.setOtherReader(true);
                }
            }
        } catch (Exception ignored) {
            descriptionPOJO.setOtherReader(false);
        }

        return descriptionPOJO;
    }

    private DescriptionPOJO loadDescriptionPoleknig() throws IOException {
        DescriptionPOJO descriptionPOJO = new DescriptionPOJO();
        setDocument();
        if (mDocument != null) {
            Elements titleElement = mDocument.select("h1[itemprop=name]");
            if (titleElement != null && titleElement.size() > 0) {
                descriptionPOJO.setTitle(titleElement.first().text().trim());
            }

            Elements img = mDocument.select("img.cover, img[src*=\"/covers/\"], img[data-original*=\"/covers/\"]");
            if (img != null && img.size() > 0) {
                String src = img.first().attr("src");
                if (src == null || src.isEmpty() || src.contains("placeholder")) {
                    src = img.first().attr("data-original");
                }
                if (src != null && !src.isEmpty()) {
                    if (!src.startsWith("http")) {
                        src = Url.SERVER_POLEKNIG + src;
                    }
                    descriptionPOJO.setPoster(src);
                }
            }

            Elements authorEl = mDocument.select("h2.book-author > a");
            if (authorEl != null && authorEl.size() > 0) {
                descriptionPOJO.setAutor(authorEl.first().text());
                String href = authorEl.first().attr("href");
                if (href != null && !href.isEmpty()) {
                    descriptionPOJO.setAutorUrl(Url.SERVER_POLEKNIG + href);
                }
            }

            Elements genreBlocks = mDocument.select("div.book-genres > div > a");
            if (genreBlocks != null && genreBlocks.size() > 0) {
                descriptionPOJO.setGenre(genreBlocks.first().text());
                String href = genreBlocks.first().attr("href");
                if (href != null && !href.isEmpty()) {
                    descriptionPOJO.setGenreUrl(Url.SERVER_POLEKNIG + href);
                }
            }

            Elements readerEl = mDocument.select("div.book-reader > div > a");
            if (readerEl != null && readerEl.size() > 0) {
                descriptionPOJO.setArtist(readerEl.first().text());
                String href = readerEl.first().attr("href");
                if (href != null && !href.isEmpty()) {
                    descriptionPOJO.setArtistUrl(Url.SERVER_POLEKNIG + href);
                }
            }

            Elements seriesEl = mDocument.select("a[href*=/series/]");
            if (seriesEl == null || seriesEl.isEmpty()) {
                seriesEl = mDocument.select("div.book-series > div > a");
            }
            if (seriesEl != null && seriesEl.size() > 0) {
                descriptionPOJO.setSeries(seriesEl.first().text());
                String href = seriesEl.first().attr("href");
                if (href != null && !href.isEmpty()) {
                    if (href.startsWith("/")) href = Url.SERVER_POLEKNIG + href;
                    descriptionPOJO.setSeriesUrl(href);
                }
            }

            Elements durationEl = mDocument.select("div.book-duration > div");
            if (durationEl != null && durationEl.size() > 0) {
                descriptionPOJO.setTime(durationEl.first().text());
            }

            Elements desc = mDocument.select("div#description[itemprop=description]");
            if (desc != null && desc.size() > 0) {
                descriptionPOJO.setDescription(desc.first().text());
            }
        }
        return descriptionPOJO;
    }

    @UnstableApi
    private void setDocument() throws IOException {
        Connection connection = Jsoup.connect(mUrl)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .header("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);
        if (!Consts.getBazaKnigCookies().isEmpty() && mUrl.contains(Url.SERVER_BAZA_KNIG)) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }

        if(useProxy && (mUrl.contains(Url.SERVER_IZIBUK)||mUrl.contains(Url.SERVER_ABMP3)||mUrl.contains(Url.SERVER_BAZA_KNIG)
                ||mUrl.contains(Url.SERVER_AUDIOBOO))){
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }
        mDocument = connection.get();
    }
}
