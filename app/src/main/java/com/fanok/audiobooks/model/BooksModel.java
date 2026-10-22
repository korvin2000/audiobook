package com.fanok.audiobooks.model;


import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import android.content.Context;
import android.util.Log;

import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.BookPOJO;
import io.reactivex.Observable;
import okhttp3.HttpUrl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.util.ArrayList;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class BooksModel implements com.fanok.audiobooks.interface_pacatge.books.BooksModel {

    private String genreName;

    private String genreSrc;

    private String authorName;

    private String authorUrl;

    private boolean end = false;


    @UnstableApi
    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(String url, int page, String nameModel, Context context) {
        genreName = null;
        genreSrc = null;
        authorName = null;
        authorUrl = null;
        int size = 4;
        return Observable.create(observableEmitter -> {
            ArrayList<BookPOJO> articlesModels;
            try {
                for (int i = 1; i <= size; i++) {

                    int temp = (page - 1) * size + i;
                    if (url.contains(Url.SERVER)) {
                        articlesModels = loadBooksList(
                                url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                    } else if (url.contains(Url.SERVER_IZIBUK)) {
                        articlesModels = loadBooksListIzibuk(
                                url.replace("?p=" + page, "?p=" + temp), temp);
                    } else if (url.contains(Url.SERVER_ABMP3)) {
                        articlesModels = loadBooksListABMP3(
                                url.replace("?page=" + page + "/", "?page=" + temp), temp);
                    } else if (url.contains(Url.SERVER_AKNIGA)) {
                        articlesModels = loadBooksListAbook(
                                url.replace("/page" + page + "/", "/page" + temp + "/"), temp);
                    } else if (url.contains(Url.SERVER_BAZA_KNIG)) {
                        articlesModels = loadBooksListBazaKnig(
                                url.replace("/page/" + page + "/", "/page/" + temp + "/"), temp);
                    } else if (url.contains(Url.SERVER_KNIGOBLUD)) {
                        String knUrl = url.contains("?page=" + page)
                                ? url.replace("?page=" + page, "?page=" + temp)
                                : url.replace("/" + page, "/" + temp);
                        articlesModels = loadBooksListKnigoblud(knUrl, temp);
                    } else if (url.contains(Url.SERVER_AUDIOBOO)) {
                        articlesModels = loadBooksListAudioboo(
                                url.replace("/page/" + page + "/", "/page/" + temp + "/"),
                                temp, nameModel, context);
                    } else if (url.contains(Url.SERVER_OTRUB)) {
                        String searchUrl = url.replace("?p=" + page, "?p=" + temp)
                                .replace("&p=" + page, "&p=" + temp);
                        articlesModels = loadBooksListOtrub(searchUrl, temp);
                    } else if (url.contains(Url.SERVER_POLEKNIG)) {
                        String searchUrl = url.replace("?p=" + page, "?p=" + temp)
                                .replace("&p=" + page, "&p=" + temp);
                        articlesModels = loadBooksListPoleknig(searchUrl, temp);
                    }else {
                        articlesModels = new ArrayList<>();
                    }
                    observableEmitter.onNext(articlesModels);
                }
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    @UnstableApi
    @Override
    public Observable<ArrayList<BookPOJO>> getBooks(String url, int page, boolean speedUp) {
        //for search
        genreName = null;
        genreSrc = null;
        authorName = null;
        authorUrl = null;
        return Observable.create(observableEmitter -> {
            int size = 4;
            try {
                for (int i = 1; i <= size; i++) {
                    ArrayList<BookPOJO> articlesModels = new ArrayList<>();
                    int temp = (page - 1) * size + i;
                    if (url.contains(Url.SERVER)) {
                        Log.d("SEARCH_SERVER", "fetching url=" + url);
                        try {
                            articlesModels = loadBooksList(url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                        } catch (Exception e) {
                            Log.e("SEARCH_SERVER", "error: " + e.getMessage());
                            BookPOJO _eb = new BookPOJO();
                            _eb.setName("⚠ Сервер: " + e.getMessage());
                            _eb.setUrl("about:blank");
                            articlesModels.add(_eb);
                        }
                        Log.d("SEARCH_SERVER", "result size=" + articlesModels.size());
                    } else if (url.contains(Url.SERVER_IZIBUK)) {
                        if (temp % size == 0) {
                            Log.d("SEARCH_IZIBUK", "fetching url=" + url);
                            try {
                                articlesModels = loadBooksListIzibuk(url, 1);
                            } catch (Exception e) {
                                Log.e("SEARCH_IZIBUK", "error: " + e.getMessage());
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Izibuk: " + e.getMessage());
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            if (articlesModels.isEmpty()) {
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Izibuk: нет результатов / ошибка подключения");
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            Log.d("SEARCH_IZIBUK", "result size=" + articlesModels.size());
                        }
                    } else if (url.contains(Url.SERVER_ABMP3)) {
                        if (temp % size == 0) {
                            Log.d("SEARCH_ABMP3", "fetching url=" + url);
                            try {
                                if (!speedUp) {
                                    articlesModels = loadBooksListSearchABMP3(url, temp / size);
                                } else {
                                    articlesModels = loadBooksListSearchABMP3SpeedUp(url, temp / size);
                                }
                            } catch (Exception e) {
                                Log.e("SEARCH_ABMP3", "error: " + e.getMessage());
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ ABMP3: " + e.getMessage());
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            if (articlesModels.isEmpty()) {
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ ABMP3: нет результатов / ошибка подключения");
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            Log.d("SEARCH_ABMP3", "result size=" + articlesModels.size());
                        }
                    } else if (url.contains(Url.SERVER_AKNIGA)) {
                        if (temp % size == 0) {
                            Log.d("SEARCH_AKNIGA", "fetching url=" + url);
                            try {
                                articlesModels = loadBooksListAbook(url.replace("/page" + page + "/", "/page" + (temp / size) + "/"),
                                        temp / size);
                            } catch (Exception e) {
                                Log.e("SEARCH_AKNIGA", "error: " + e.getMessage());
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Akniga: " + e.getMessage());
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            if (articlesModels.isEmpty()) {
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Akniga: нет результатов / ошибка подключения");
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            Log.d("SEARCH_AKNIGA", "result size=" + articlesModels.size());
                        }
                    } else if (url.contains(Url.SERVER_BAZA_KNIG)) {
                        if (url.contains("///qery=")) {
                            String query = URLEncoder.encode(url.substring(url.indexOf("///qery=") + 8).replaceAll("&.*", ""), "UTF-8");
                            String[] suffixes = {
                                "/f/nazvanie=" + query + "/v.litres_buy=litres/sort=date/order=desc/",
                                "/f/cikl=" + query + "/v.litres_buy=litres/sort=date/order=desc/",
                                "/f/j.x.ispolnitel;x.ispolnitel2;x.ispolnitel3=" + query + "/v.litres_buy=litres/sort=date/order=desc/",
                                "/f/j.x.autor;p.title=" + query + "/v.litres_buy=litres/sort=news_read/order=desc/"
                            };
                            if (i <= suffixes.length) {
                                String bazaUrl = Url.SERVER_BAZA_KNIG + suffixes[i - 1];
                                Log.d("SEARCH_BAZA", "///qery= url=" + bazaUrl);
                                try {
                                    articlesModels = loadBooksListBazaKnig(bazaUrl, 1);
                                } catch (Exception e) {
                                    Log.e("SEARCH_BAZA", "error: " + e.getMessage());
                                }
                            }
                            Log.d("SEARCH_BAZA", "result size=" + articlesModels.size());
                        } else {
                            if (temp % size == 0) {
                                Log.d("SEARCH_BAZA", "fetching url=" + url);
                                try {
                                    articlesModels = loadBooksListBazaKnig(url.replace("&page=" + page, ""), temp / size);
                                } catch (Exception e) {
                                    Log.e("SEARCH_BAZA", "error: " + e.getMessage());
                                    BookPOJO _eb = new BookPOJO();
                                    _eb.setName("⚠ Baza-knig: " + e.getMessage());
                                    _eb.setUrl("about:blank");
                                    articlesModels.add(_eb);
                                }
                                Log.d("SEARCH_BAZA", "result size=" + articlesModels.size());
                            }
                        }
                    } else if (url.contains(Url.SERVER_KNIGOBLUD)) {
                        if (temp % size == 0) {
                            Log.d("SEARCH_KNIGOBLUD", "fetching url=" + url);
                            try {
                                articlesModels = loadBooksListKnigoblud(url.replace("&page" + page, "&page" + (temp / size)),
                                        temp / size);
                            } catch (Exception e) {
                                Log.e("SEARCH_KNIGOBLUD", "error: " + e.getMessage());
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Knigoblud: " + e.getMessage());
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            if (articlesModels.isEmpty()) {
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Knigoblud: нет результатов / ошибка подключения");
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            Log.d("SEARCH_KNIGOBLUD", "result size=" + articlesModels.size());
                        }
                    } else if (url.contains(Url.SERVER_AUDIOBOO)) {
                        if (temp % size == 0) {
                            Log.d("SEARCH_AUDIOBOO", "fetching url=" + url);
                            try {
                                articlesModels = loadBooksListAudiobooSearch(url, temp / size);
                            } catch (Exception e) {
                                Log.e("SEARCH_AUDIOBOO", "error: " + e.getMessage());
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Audioboo: " + e.getMessage());
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            if (articlesModels.isEmpty()) {
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Audioboo: нет результатов / ошибка подключения");
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            Log.d("SEARCH_AUDIOBOO", "result size=" + articlesModels.size());
                        }
                    } else if (url.contains(Url.SERVER_OTRUB)) {
                        if (temp % size == 0) {
                            Log.d("SEARCH_OTRUB", "fetching url=" + url);
                            try {
                                String searchUrl = url.replace("?p=" + page, "?p=" + (temp / size))
                                        .replace("&p=" + page, "&p=" + (temp / size));
                                articlesModels = loadBooksListOtrub(searchUrl, temp / size);
                            } catch (Exception e) {
                                Log.e("SEARCH_OTRUB", "error: " + e.getMessage());
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Otrub: " + e.getMessage());
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            if (articlesModels.isEmpty()) {
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Otrub: нет результатов / ошибка подключения");
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            Log.d("SEARCH_OTRUB", "result size=" + articlesModels.size());
                        }
                    } else if (url.contains(Url.SERVER_POLEKNIG)) {
                        if (temp % size == 0) {
                            Log.d("SEARCH_POLEKNIG", "fetching url=" + url);
                            try {
                                String searchUrl = url.replace("?p=" + page, "?p=" + (temp / size))
                                        .replace("&p=" + page, "&p=" + (temp / size));
                                articlesModels = loadBooksListPoleknig(searchUrl, temp / size);
                            } catch (Exception e) {
                                Log.e("SEARCH_POLEKNIG", "error: " + e.getMessage());
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Poleknig: " + e.getMessage());
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            if (articlesModels.isEmpty()) {
                                BookPOJO _eb = new BookPOJO();
                                _eb.setName("⚠ Poleknig: нет результатов / ошибка подключения");
                                _eb.setUrl("about:blank");
                                articlesModels.add(_eb);
                            }
                            Log.d("SEARCH_POLEKNIG", "result size=" + articlesModels.size());
                        }
                    }
                    observableEmitter.onNext(articlesModels);
                                    }
                                } catch (Exception e) {
                                    Log.e("SEARCH_DISPATCH", "Unhandled exception for url=" + url + " msg=" + e.getMessage(), e);
                                    if (e.getClass() == NullPointerException.class || (e.getClass() == SocketTimeoutException.class && url
                        .contains(Url.SERVER_BAZA_KNIG))) {
                    observableEmitter.onError(new NullPointerException(url));
                                    } else {
                    observableEmitter.onError(e);
                                    }
                                } finally {
                observableEmitter.onComplete();
            }
        });
    }



    private ArrayList<BookPOJO> loadBooksList(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        String autor = "";
        String autorUrl = "";
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements pagesConteiner = doc.getElementsByClass("pn_page_buttons");
        if (pagesConteiner.size() != 0) {
            Elements pagesElements = pagesConteiner.first().children();
            if (pagesElements.size() != 0) {
                Element lastPageElement = pagesElements.last();
                try {
                    int lastPage = Integer.parseInt(lastPageElement.text());
                    if (lastPage < page) {
                        return result;
                    }
                } catch (NumberFormatException e) {
                    return result;
                }
            }
        } else if (page > 1) return result;

        Elements pageTitle = doc.getElementsByClass("page_title");
        if (pageTitle.size() != 0) {
            if (pageTitle.first().text().contains("Цикл")) {
                Elements aList = pageTitle.first().getElementsByTag("a");
                if (aList.size() != 0) {
                    autor = aList.first().text();
                    autorUrl = Url.SERVER + aList.first().attr("href");
                }
            } else if (pageTitle.first().text().contains("Все авторы")) {
                autorUrl = url;
                Elements h1 = pageTitle.first().getElementsByTag("h1");
                if (h1.size() != 0) {
                    autor = h1.first().text();
                } else {
                    autor = "";
                }
            }
        }
        Element bookList = doc.getElementById("books_updates_list");
        if (bookList == null) bookList = doc.getElementById("books_list");
        if (bookList != null) {
            Elements books = bookList.getElementsByClass("bookkitem");
            if (books.size() == 0) return result;
            for (Element book : books) {
                Elements litRes = book.getElementsByClass("bookkitem_litres_icon");
                if (litRes.size() != 0) continue;
                BookPOJO bookPOJO = new BookPOJO();
                String img = book.getElementsByTag("img").get(0).attr("src");
                if (img != null) {
                    int lastPos = img.indexOf("?");
                    if (lastPos != -1) {
                        img = img.substring(0, lastPos);
                    }
                    bookPOJO.setPhoto(img);
                }
                Elements aTags = book.getElementsByClass("bookkitem_name");
                if (aTags.size() != 0) {
                    Element a = aTags.first().child(0);
                    if (a != null) {
                        bookPOJO.setUrl(Url.SERVER + a.attr("href"));
                        bookPOJO.setName(a.text());
                    }
                }

                Element aGenre = book.getElementsByClass("bookkitem_genre").first().child(0);
                if (aGenre != null) {
                    bookPOJO.setUrlGenre(Url.SERVER + aGenre.attr("href") + "<page>/");
                    bookPOJO.setGenre(aGenre.text());
                }

                Elements ratingConteiner = book.getElementsByClass("bookkitem_icon -views");
                if (ratingConteiner.size() != 0) {
                    bookPOJO.setReting(ratingConteiner.first().nextElementSibling().text());
                }

                Elements comentsConteiner = book.getElementsByClass("bookkitem_icon -comments");
                if (comentsConteiner.size() != 0) {
                    bookPOJO.setComents(comentsConteiner.first().nextElementSibling().text());
                }

                Elements autorConteiner = book.getElementsByClass("bookkitem_author");
                if (autorConteiner.size() != 0) {
                    Elements aAutor = autorConteiner.first().getElementsByTag("a");
                    if (aAutor.size() != 0) {
                        bookPOJO.setAutor(aAutor.first().text());
                        bookPOJO.setUrlAutor(Url.SERVER + aAutor.first().attr("href"));
                    }
                } else {
                    if (!autor.isEmpty()) {
                        bookPOJO.setAutor(autor);
                    }
                    if (!autorUrl.isEmpty()) {
                        bookPOJO.setUrlAutor(autorUrl);
                    }
                }

                Elements artistConteiner = book.getElementsByClass("bookkitem_icon -reader");
                if (artistConteiner.size() != 0) {
                    Element parent = artistConteiner.first().parent();
                    Elements artist = parent.getElementsByTag("a");
                    if (artist.size() != 0) {
                        bookPOJO.setArtist(artist.first().text());
                        bookPOJO.setUrlArtist(Url.SERVER + artist.first().attr("href"));
                    }
                }

                Elements timeConteinr = book.getElementsByClass("bookkitem_meta_time");
                if (timeConteinr.size() != 0) {
                    bookPOJO.setTime(timeConteinr.first().text());
                }

                Elements seriesConteiner = book.getElementsByClass("bookkitem_icon -serie");
                if (seriesConteiner.size() != 0) {
                    Element parent = seriesConteiner.first().parent();
                    Elements series = parent.getElementsByTag("a");
                    if (series.size() != 0) {
                        bookPOJO.setSeries(series.first().text());
                        bookPOJO.setUrlSeries(Url.SERVER + series.first().attr("href") + "?page=");
                    }
                }

                if (url.contains("/serie/")) {
                    if (bookPOJO.getSeries() == null || bookPOJO.getUrlSeries() == null
                            || bookPOJO.getSeries().isEmpty()
                            || bookPOJO.getUrlSeries().isEmpty()) {
                        bookPOJO.setUrlSeries(url);
                        Elements elements = doc.getElementsByClass("page_title");
                        if (elements != null && elements.size() != 0) {
                            Elements b = elements.first().getElementsByTag("b");
                            if (b != null && b.size() != 0) {
                                String text = b.first().text();
                                if (text != null && !text.isEmpty()) {
                                    bookPOJO.setSeries(text);
                                }
                            }
                        }
                    }
                }

                Elements about = book.getElementsByClass("bookkitem_about");
                if (about != null && about.size() != 0) {
                    bookPOJO.setDesc(about.first().text());
                }

                if (bookPOJO.isNull()) {
                    continue;
                }
                result.add(bookPOJO);
            }
        }
        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Сервер: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksListABMP3(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
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

        Elements pagesConteiner = doc.getElementsByClass("pagination");
        if (pagesConteiner.size() != 0) {
            Elements pagesElements = pagesConteiner.first().children();
            if (pagesElements.size() != 0) {
                Element lastPageElement = pagesElements.get(pagesElements.size() - 2);
                Element aLastPage = lastPageElement.child(0);
                if (aLastPage != null) {
                    int lastPage = Integer.parseInt(aLastPage.text());
                    if (lastPage < page) {
                        return result;
                    }
                } else {
                    return result;
                }
            } else {
                return result;
            }
        } else if (page > 1) {
            return result;
        }

        Elements bookList = doc.getElementsByClass("b-posts");
        if (bookList != null && bookList.size() > 0) {
            Elements books = bookList.first().getElementsByClass("abook-item");
            if (books.size() == 0) {
                return result;
            }
            for (Element book : books) {
                BookPOJO bookPOJO = new BookPOJO();
                String autorName = "";

                String img = book.getElementsByTag("img").get(0).attr("src");
                if (img != null) {
                    bookPOJO.setPhoto(img);
                }

                Elements bookInfo = book.getElementsByClass("content-abook-info");
                if (bookInfo != null && bookInfo.size() != 0) {
                    Elements cont = bookInfo.first().children();
                    if (cont != null && cont.size() == 3) {
                        Elements aAutor = cont.get(0).getElementsByTag("a");
                        if (aAutor != null && aAutor.size() > 0) {
                            autorName = aAutor.first().text();
                            bookPOJO.setAutor(autorName);
                            bookPOJO.setUrlAutor(Url.SERVER_ABMP3 + aAutor.first().attr("href") + "?page=");
                        }
                        Elements aReader = cont.get(1).getElementsByTag("a");
                        if (aReader != null && aReader.size() > 0) {
                            bookPOJO.setArtist(aReader.first().text());
                            bookPOJO.setUrlArtist(Url.SERVER_ABMP3 + aReader.first().attr("href") + "?page=");
                        }
                        bookPOJO.setTime(cont.get(2).text().trim());
                    }
                }

                Elements aTags = book.getElementsByClass("abook-title");
                if (aTags != null && aTags.size() != 0) {
                    Element a = aTags.first().child(0);
                    if (a != null) {
                        bookPOJO.setUrl(Url.SERVER_ABMP3 + a.attr("href"));
                        bookPOJO.setName(a.text().replace(autorName + " - ", ""));
                    }
                }

                Element aGenre = book.getElementsByClass("abook-genre").first().child(0);
                if (aGenre != null) {
                    bookPOJO.setUrlGenre(Url.SERVER_ABMP3 + aGenre.attr("href") + "?page=");
                    bookPOJO.setGenre(aGenre.text());
                }

                Elements about = book.getElementsByClass("abook-content");
                if (about != null && about.size() != 0) {
                    bookPOJO.setDesc(about.first().text());
                }

                if (bookPOJO.isNull()) {
                    continue;
                }
                result.add(bookPOJO);
            }
        }
        return result;
    }

    private ArrayList<BookPOJO> loadBooksListAbook(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        Document document = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true)
                .get();

        Elements bootom = document.getElementsByClass("page__nav");
        if (bootom != null && bootom.size() != 0) {
            Elements nextButton = bootom.get(0).getElementsByClass("page__nav--next");
            if (!(nextButton != null && nextButton.size() != 0)) {
                if (end) {
                    return result;
                }
                end = true;
            }
        } else if (page > 1) {
            return result;
        }

        Elements listElements = document.getElementsByClass("content__main__articles--item");
        if (listElements != null && listElements.size() != 0) {
            for (Element book : listElements) {
                Elements paid = book.getElementsByAttributeValue("href", Url.SERVER_AKNIGA + "/paid/");
                if (paid != null && paid.size() != 0) {
                    continue;
                }
                Elements fragment = book.getElementsByClass("caption__article-preview");
                if (fragment != null && fragment.size() != 0) {
                    String text = fragment.first().text();
                    if (text != null && text.equals("Фрагмент")) {
                        continue;
                    }
                }

                BookPOJO bookPOJO = new BookPOJO();
                Elements genreParent = book.getElementsByClass("section__title");
                if (genreParent != null && genreParent.size() != 0) {
                    Element element = genreParent.first();
                    String genreName = element.text();
                    if (genreName != null) {
                        bookPOJO.setGenre(genreName);
                    }
                    String genreUrl = element.attr("href");
                    if (genreUrl != null) {
                        bookPOJO.setUrlGenre(genreUrl + "page<page>/");
                    }
                }
                Elements imageParent = book.getElementsByClass("container__remaining-width article--cover pull-left");
                if (imageParent != null && imageParent.size() != 0) {
                    Elements image = imageParent.first().getElementsByTag("img");
                    if (image != null && image.size() != 0) {
                        String imageSrc = image.first().attr("data-src");
                        if (imageSrc == null || imageSrc.isEmpty()) {
                            imageSrc = image.first().attr("data-original");
                        }
                        if (imageSrc == null || imageSrc.isEmpty()) {
                            imageSrc = image.first().attr("src");
                        }
                        if (imageSrc != null && !imageSrc.isEmpty()) {
                            bookPOJO.setPhoto(imageSrc);
                        }
                    }
                }

                if (bookPOJO.getPhoto() == null || bookPOJO.getPhoto().isEmpty()) {
                    Elements imgs = book.select("img");
                    if (imgs != null && !imgs.isEmpty()) {
                        for (Element img : imgs) {
                            String src = img.attr("data-src");
                            if (src == null || src.isEmpty()) src = img.attr("data-original");
                            if (src == null || src.isEmpty()) src = img.attr("src");
                            if (src != null && !src.isEmpty() && !src.contains("blank.gif") && !src.contains("pixel.gif")) {
                                bookPOJO.setPhoto(src);
                                break;
                            }
                        }
                    }
                }

                if (bookPOJO.getPhoto() == null || bookPOJO.getPhoto().isEmpty()) {
                    Elements bgElems = book.select("[style*='background-image']");
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
                                        bookPOJO.setPhoto(imgUrl);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                Elements elements = book.getElementsByClass("link__action--author");
                if (elements != null) {
                    for (Element element : elements) {
                        Elements aTag = element.getElementsByTag("a");
                        if (aTag != null && aTag.size() != 0) {
                            Element a = aTag.first();
                            String src = a.attr("href");
                            String name = a.text();
                            
                            String typeStr = "";
                            Elements use = element.getElementsByTag("use");
                            if (use != null && use.size() != 0) {
                                typeStr = use.first().attr("xlink:href");
                            }
                            if (typeStr == null || typeStr.isEmpty()) {
                                if (src != null) {
                                    typeStr = src;
                                }
                            }
                            
                            if (typeStr != null && src != null && !src.isEmpty()) {
                                if (typeStr.contains("author")) {
                                    bookPOJO.setUrlAutor(src + "page<page>/");
                                } else if (typeStr.contains("performer") || typeStr.contains("reader")) {
                                    bookPOJO.setUrlArtist(src + "page<page>/");
                                } else if (typeStr.contains("series") || typeStr.contains("cycle")) {
                                    bookPOJO.setUrlSeries(src);
                                }
                            }

                            if (typeStr != null && name != null && !name.isEmpty()) {
                                if (typeStr.contains("author")) {
                                    bookPOJO.setAutor(name);
                                } else if (typeStr.contains("performer") || typeStr.contains("reader")) {
                                    bookPOJO.setArtist(name);
                                } else if (typeStr.contains("series") || typeStr.contains("cycle")) {
                                    bookPOJO.setSeries(name);
                                }
                            }
                        }
                    }
                }

                Elements titleElements = book.getElementsByClass("caption__article-main");
                if (titleElements != null && titleElements.size() != 0) {
                    String title = titleElements.first().text();
                    if (title != null && !title.isEmpty()) {
                        bookPOJO.setName(title.replace(bookPOJO.getAutor() + " - ", ""));
                    }
                }

                Elements link = book.getElementsByClass("content__article-main-link");
                if (link != null && link.size() != 0) {
                    String src = link.first().attr("href");
                    if (src != null && !src.isEmpty()) {
                        bookPOJO.setUrl(src);
                    }
                }

                Elements desc = book.getElementsByClass("description__article-main");
                if (desc != null && desc.size() != 0) {
                    String text = desc.first().text();
                    if (text != null && !text.isEmpty()) {
                        bookPOJO.setDesc(text);
                    }
                }

                Elements time = book.getElementsByClass("link__action--label--time");
                if (time != null && time.size() != 0) {
                    String text = time.first().text();
                    if (text != null && !text.isEmpty()) {
                        bookPOJO.setTime(text);
                    }
                }

                Elements coments = book.getElementsByClass("link__action--comment");
                if (coments != null && coments.size() != 0) {
                    String text = coments.first().text();
                    if (text != null && !text.contains("нет комментариев")) {
                        String count = text.trim().substring(0, text.trim().indexOf(" "));
                        bookPOJO.setComents(count);
                    }
                }

                Elements reting = book.getElementsByClass("link__action--label--views");
                if (reting != null && reting.size() != 0) {
                    String text = reting.text();
                    if (text != null && !text.isEmpty()) {
                        bookPOJO.setReting(text.trim());
                    }
                }

                if (bookPOJO.isNull()) {
                    continue;
                }
                result.add(bookPOJO);
            }

        }

        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Akniga: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksListBazaKnig(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();

        Document doc;
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        if (!Consts.getBazaKnigCookies().isEmpty()) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }

        if (url.contains("?do=search")) {
            String query = url.substring(url.indexOf("///qery=")).replace("///qery=", "");
            url = url.substring(0, url.indexOf("///qery="));
            doc = connection
                    .data("do", "search")
                    .data("subaction", "search")
                    .data("search_start", String.valueOf(page))
                    .data("full_search", "0")
                    .data("result_from", "0")
                    .data("story", query)
                    .post();
        } else {
            doc = connection.get();
        }

        if (doc.title().contains("Just a moment")) {
            throw new CookesExeption(url);
        }

        Elements navConteiner = doc.getElementsByClass("page-nav");
        if (page > 1) {
            if (navConteiner != null && navConteiner.size() > 0) {
                Elements navigainClass = navConteiner.first().getElementsByClass("navigation");
                if (navigainClass != null && navigainClass.size() > 0) {
                    Elements children = navigainClass.first().children();
                    if (children != null && children.size() > 0) {
                        int last = Integer.parseInt(children.last().text());
                        if (page > last) {
                            return result;
                        }
                    } else {
                        return result;
                    }
                } else {
                    return result;
                }
            } else {
                return result;
            }
        }

        Element conteiner = doc.getElementById("dle-content");
        if (conteiner != null) {
            Elements bookList = conteiner.getElementsByClass("short");
            if (bookList != null && bookList.size() > 0) {
                for (Element book : bookList) {
                    BookPOJO bookPOJO = new BookPOJO();
                    Elements imgConteiner = book.getElementsByClass("short-img");
                    if (imgConteiner != null && imgConteiner.size() > 0) {
                        Element element = imgConteiner.first();
                        Elements aElement = element.getElementsByTag("a");
                        if (aElement != null && aElement.size() > 0) {
                            String aHref = aElement.first().attr("href");
                            if (aHref != null && !aHref.isEmpty()) {
                                bookPOJO.setUrl(aHref);
                            } else {
                                continue;
                            }
                            Elements img = element.getElementsByTag("img");
                            if (img != null && img.size() > 0) {
                                String src = img.first().attr("src");
                                if (src != null) {
                                    bookPOJO.setPhoto(Url.SERVER_BAZA_KNIG+src);
                                }
                            }
                        }
                    }

                    Elements nameConteiner = book.getElementsByClass("short-title");
                    if (nameConteiner != null && nameConteiner.size() > 0) {
                        Elements aTag = nameConteiner.first().getElementsByTag("a");
                        if (aTag != null && aTag.size() > 0) {
                            String name = aTag.first().text();
                            if (name != null && !name.isEmpty()) {
                                bookPOJO.setName(name);
                            } else {
                                continue;
                            }
                        }
                    }

                    Elements descCont = book.getElementsByClass("short-text");
                    if (descCont != null && descCont.size() > 0) {
                        String text = descCont.first().text();
                        if (text != null) {
                            bookPOJO.setDesc(text);
                        }
                    }

                    Elements cont = book.getElementsByClass("reset short-items");
                    if (cont != null && cont.size() > 0) {
                        Elements liElem = cont.first().getElementsByTag("li");
                        if (liElem != null && liElem.size() > 0) {
                            for (Element li : liElem) {
                                String liText = li.text();
                                if (liText != null) {
                                    if (liText.contains("Автор")) {
                                        Elements autorElements = li.getElementsByTag("b");
                                        if (autorElements != null && autorElements.size() > 0) {
                                            String autorName = autorElements.first().text();
                                            if (autorName != null && !autorName.isEmpty()) {
                                                bookPOJO.setAutor(autorName);
                                                Elements aTag = autorElements.first().getElementsByTag("a");
                                                if (aTag != null && aTag.size() > 0) {
                                                    String aHref = aTag.first().attr("href");
                                                    if (aHref != null) {
                                                        bookPOJO.setUrlAutor(aHref + "page/");
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (liText.contains("Читает")) {
                                        Elements artistElements = li.getElementsByTag("b");
                                        if (artistElements != null && artistElements.size() > 0) {
                                            String artistName = liText.replace("Читает: ", "").trim();
                                            if (!artistName.isEmpty()) {
                                                bookPOJO.setArtist(artistName);
                                                Elements aTag = artistElements.first().getElementsByTag("a");
                                                if (aTag != null && aTag.size() > 0) {
                                                    String aHref = aTag.first().attr("href");
                                                    if (aHref != null) {
                                                        bookPOJO.setUrlArtist(aHref + "page/");
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (liText.contains("Длительность")) {
                                        Elements timeConteiner = li.getElementsByTag("b");
                                        if (timeConteiner != null && timeConteiner.size() > 0) {
                                            String time = timeConteiner.first().text();
                                            if (time != null && !time.isEmpty()) {
                                                bookPOJO.setTime(time);
                                            }
                                        }
                                    }

                                    if (liText.contains("Цикл")) {
                                        Elements seriesConteiner = li.getElementsByTag("a");
                                        if (seriesConteiner != null && seriesConteiner.size() > 0) {
                                            Element a = seriesConteiner.first();
                                            String text = a.text();
                                            if (text != null && !text.isEmpty()) {
                                                bookPOJO.setSeries(text);
                                                String href = a.attr("href");
                                                if (href != null) {
                                                    bookPOJO.setUrlSeries(href + "page/");
                                                }
                                            }
                                        }
                                    }

                                    if (liText.contains("Жанр")) {
                                        Elements genreConteiner = li.getElementsByTag("a");
                                        if (genreConteiner != null && genreConteiner.size() > 0) {
                                            String text = genreConteiner.first().text();
                                            if (text != null && !text.isEmpty()) {
                                                bookPOJO.setGenre(text);
                                                String href = genreConteiner.first().attr("href");
                                                if (href != null) {
                                                    bookPOJO.setUrlGenre(href + "page/");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Elements shortBottom = book.getElementsByClass("short-bottom");
                    if (shortBottom != null && shortBottom.size() > 0) {
                        Elements comentsClass = shortBottom.first().getElementsByClass("comments");
                        if (comentsClass != null && comentsClass.size() == 3) {
                            String text = comentsClass.last().text();
                            if (text != null) {
                                bookPOJO.setComents(text);
                            }
                        }
                    }

                    if (bookPOJO.isNull()) {
                        continue;
                    }
                    result.add(bookPOJO);
                }

            }
        }
        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Baza-knig: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksListIzibuk(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        int cookie;
        if (Consts.izibuk_reiting) {
            cookie = 1;
        } else {
            cookie = 0;
        }


        Connection connection = Jsoup.connect(url).userAgent(Consts.USER_AGENT).sslSocketFactory(
                Consts.socketFactory()).referrer(
                Url.INDEX_IZIBUK + "1").maxBodySize(0).ignoreHttpErrors(true).cookie("sort_pop", String.valueOf(cookie));

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }


        Document document = connection.get();

        Log.d("IZIBUK_DEBUG", "url=" + url + " htmlLen=" + document.html().length() + " title=" + document.title());

        Element bootom = document.getElementById("books_updates_list__pn");
        if (bootom == null) {
            bootom = document.getElementById("books_list__pn");
        }
        if (bootom != null) {
            Element bootomChild = bootom.child(0);
            if (bootomChild != null) {
                Elements list = bootomChild.children();
                if (list != null && list.size() > 1) {
                    Element pagesParent = list.last();
                    Elements pages = pagesParent.children();
                    if (pages != null && pages.size() > 0) {
                        Element lastPage = pages.last();
                        if (lastPage != null) {
                            String last = lastPage.text();
                            if (last != null) {
                                try {
                                    if (page > Integer.parseInt(last)) {
                                        return result;
                                    }
                                } catch (NumberFormatException e) {
                                    return result;
                                }

                            } else {
                                return result;
                            }
                        } else {
                            return result;
                        }
                    } else {
                        return result;
                    }
                } else {
                    return result;
                }
            } else {
                return result;
            }
        } else if (page > 1) {
            Log.d("IZIBUK_DEBUG", "pagination check failed: bootom=null page=" + page + " url=" + url);
            return result;
        }

        Log.d("IZIBUK_DEBUG", "pagination OK, now looking for books_list");
        Element listParent = document.getElementById("books_list");
        if (listParent == null) {
            listParent = document.getElementById("books_updates_list");
        }
        if (listParent == null) {
            Log.d("IZIBUK_DEBUG", "books_list AND books_updates_list BOTH null, url=" + url);
        }
        if (listParent != null) {
            Elements listElements = listParent.getElementsByClass("_ccb9b7");
            Log.d("IZIBUK_DEBUG", "books_list found, _ccb9b7 count=" + (listElements != null ? listElements.size() : 0));
            if (listElements != null && listElements.size() != 0) {
                for (Element book : listElements) {
                    BookPOJO bookPOJO = new BookPOJO();
                    Elements genreParent = book.getElementsByClass("_680f12");
                    if (genreParent != null && genreParent.size() != 0) {
                        Element element = genreParent.first();
                        if (element != null && element.children().size() != 0) {
                            Element genre = element.child(0);
                            if (genre != null) {
                                String genreName = genre.text();
                                if (genreName != null) {
                                    bookPOJO.setGenre(genreName);
                                }
                                String genreUrl = genre.attr("href");
                                if (genreUrl != null) {
                                    bookPOJO.setUrlGenre(Url.SERVER_IZIBUK + genreUrl + "?p=");
                                }
                            }
                        }
                    } else {
                        if (url.contains("genre")) {
                            bookPOJO.setUrlGenre(url);
                            Elements elements = document.getElementsByTag("h1");
                            if (elements != null && elements.size() != 0) {
                                String genre = elements.first().text();
                                if (genre != null) {
                                    bookPOJO.setGenre(genre);
                                }
                            }
                        }
                    }
                    Elements imageParent = book.getElementsByClass("_bce453");
                    if (imageParent != null && imageParent.size() != 0) {
                        Element image = imageParent.first().child(0);
                        if (image != null) {
                            String imageSrc = image.attr("src");
                            if (imageSrc != null) {
                                bookPOJO.setPhoto(imageSrc);
                            }
                        }
                    }
                    Elements contentlist = book.getElementsByClass("_802db0");
                    if (contentlist != null && contentlist.size() != 0) {
                        Element content = contentlist.first();
                        if (content != null) {
                            Element titleParent = content.child(1);
                            if (titleParent != null) {
                                Elements fragments = titleParent.getElementsByClass("_09ddb7");
                                if (fragments != null && fragments.size() > 0) continue;
                                Element title = titleParent.child(0);
                                if (title != null) {
                                    String bookUrl = title.attr("href");
                                    if (bookUrl != null) {
                                        bookPOJO.setUrl(Url.SERVER_IZIBUK + bookUrl);
                                        if (url.contains("serie")
                                                && bookPOJO.getUrlGenre() == null) {
                                            setSiries(Url.SERVER_IZIBUK + bookUrl);
                                            bookPOJO.setGenre(genreName);
                                            bookPOJO.setUrlGenre(genreSrc);
                                            bookPOJO.setAutor(authorName);
                                            bookPOJO.setUrlAutor(authorUrl);
                                        }
                                    }
                                    String name = title.text();
                                    if (name != null) {
                                        bookPOJO.setName(name);
                                    }
                                }
                            }
                            Element child = content.child(2);
                            if (child != null) {
                                Elements elements = child.children();
                                if (elements != null) {
                                    for (Element element : elements) {
                                        if (element.childrenSize() > 1) {
                                            Element conteinter = element.child(1);
                                            if (conteinter != null) {
                                                String href = conteinter.attr("href");
                                                if (href != null) {
                                                    String name = conteinter.text();
                                                    if (href.contains("author")) {
                                                        bookPOJO.setUrlAutor(
                                                                Url.SERVER_IZIBUK + href + "?p=");
                                                        if (name != null) {
                                                            bookPOJO.setAutor(name);
                                                        }
                                                    } else if (href.contains("reader")) {
                                                        bookPOJO.setUrlArtist(
                                                                Url.SERVER_IZIBUK + href + "?p=");
                                                        if (name != null) {
                                                            bookPOJO.setArtist(name);
                                                        }
                                                    } else if (href.contains("serie")) {
                                                        bookPOJO.setUrlSeries(
                                                                Url.SERVER_IZIBUK + href + "?p=");
                                                        if (name != null) {
                                                            bookPOJO.setSeries(name);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (bookPOJO.getAutor() == null || bookPOJO.getAutor().isEmpty()) {
                                if (url.contains("author")) {
                                    bookPOJO.setUrlAutor(url + "?p=");
                                    Elements elements = document.getElementsByTag("h1");
                                    if (elements != null && elements.size() != 0) {
                                        String genre = elements.first().text();
                                        if (genre != null) {
                                            bookPOJO.setAutor(genre);
                                        }
                                    }
                                }
                            }

                            if (bookPOJO.getArtist() == null || bookPOJO.getArtist().isEmpty()) {
                                if (url.contains("reader")) {
                                    bookPOJO.setUrlArtist(url + "?p=");
                                    Elements elements = document.getElementsByTag("h1");
                                    if (elements != null && elements.size() != 0) {
                                        String genre = elements.first().text();
                                        if (genre != null) {
                                            bookPOJO.setArtist(genre);
                                        }
                                    }
                                }
                            }

                            if (bookPOJO.getSeries() == null || bookPOJO.getSeries().isEmpty()) {
                                if (url.contains("serie")) {
                                    bookPOJO.setUrlSeries(url + "?p=");
                                    Elements elements = document.getElementsByTag("h1");
                                    if (elements != null && elements.size() != 0) {
                                        String genre = elements.first().text();
                                        if (genre != null) {
                                            bookPOJO.setSeries(genre);
                                        }
                                    }
                                }
                            }

                            Element description = content.child(3);
                            if (description != null) {
                                String text = description.text();
                                if (text != null) {
                                    bookPOJO.setDesc(text);
                                }
                            }
                        }
                    }
                    if (bookPOJO.isNull()) {
                        continue;
                    }
                    result.add(bookPOJO);
                }

            }
        }
        Log.d("IZIBUK_DEBUG", "result size=" + result.size());

        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Izibuk: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

            @UnstableApi
            private ArrayList<BookPOJO> loadBooksListSearchABMP3(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url)
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

        Elements elements = doc.getElementsByClass("b-statictop-search");
        if (elements != null) {
            for (Element item : elements) {
                Elements title = item.getElementsByClass("b-statictop__title");
                if (title != null && title.size() != 0) {
                    if (title.first().text().contains("в книгах")) {
                        Elements parent = item.getElementsByClass("b-statictop__items");
                        if (parent != null && parent.size() != 0) {
                            Elements books = parent.first().children();
                            for (int i = (page - 1) * 7; i < page * 7; i++) {
                                if (i >= books.size()) {
                                    break;
                                }
                                Element book = books.get(i);
                                BookPOJO bookPOJO = new BookPOJO();
                                Elements aTag = book.getElementsByTag("a");
                                if (aTag != null && aTag.size() != 0) {
                                    bookPOJO.setUrl(Url.SERVER_ABMP3 + aTag.first().attr("href"));
                                    String name = aTag.first().text();
                                    int nameEnd = name.indexOf(" (");
                                    if (nameEnd > 0) {
                                        name = name.substring(0, nameEnd);
                                    }
                                    bookPOJO.setName(name);


                                    Connection connection1 = Jsoup.connect(bookPOJO.getUrl())
                                            .userAgent(Consts.USER_AGENT)
                                            .referrer("http://www.google.com")
                                            .maxBodySize(0)
                                            .sslSocketFactory(Consts.socketFactory());

                                    if(App.useProxy) {
                                        Proxy proxy = new Proxy(Type.SOCKS,
                                                new InetSocketAddress(PROXY_HOST, PROXY_PORT));
                                        connection1.proxy(proxy);
                                    }

                                    Document bookPage = connection1.get();
                                    Elements img = bookPage.getElementsByClass("abook_image");
                                    if (img != null && img.size() != 0) {
                                        bookPOJO.setPhoto(img.first().attr("src"));
                                    } else {
                                        String cover = getAbmp3CoverUrl(bookPOJO.getUrl());
                                        if (cover != null) {
                                            bookPOJO.setPhoto(cover);
                                        }
                                    }

                                    Elements desc = bookPage.getElementsByClass("abook-desc");
                                    if (desc != null && desc.size() != 0) {
                                        bookPOJO.setDesc(desc.first().text());
                                    }

                                    Elements infos = bookPage.getElementsByClass("panel-item");
                                    if (infos != null) {
                                        for (Element info : infos) {
                                            if (info.text().contains("Автор")) {
                                                Elements element = info.getElementsByTag("a");
                                                if (element != null && element.size() != 0) {
                                                    bookPOJO.setAutor(element.first().text());
                                                    bookPOJO.setUrlAutor(
                                                            Url.SERVER_ABMP3 + element.first().attr("href")
                                                                    + "?page=");
                                                }
                                            } else if (info.text().contains("Читает")) {
                                                Elements element = info.getElementsByTag("a");
                                                if (element != null && element.size() != 0) {
                                                    bookPOJO.setArtist(element.first().text());
                                                    bookPOJO.setUrlArtist(
                                                            Url.SERVER_ABMP3 + element.first().attr("href")
                                                                    + "?page=");
                                                }
                                            } else if (info.text().contains("Жанры")) {
                                                Elements element = info.getElementsByTag("a");
                                                if (element != null && element.size() != 0) {
                                                    bookPOJO.setGenre(element.first().text());
                                                    bookPOJO.setUrlGenre(
                                                            Url.SERVER_ABMP3 + element.first().attr("href")
                                                                    + "?page=");
                                                }
                                            } else {
                                                Elements clock = info.getElementsByClass("fa-clock-o");
                                                if (clock != null && clock.size() != 0) {
                                                    bookPOJO.setTime(info.text().trim());
                                                }

                                                Elements reting = info.getElementsByClass("fa-eye");
                                                if (reting != null && reting.size() != 0) {
                                                    bookPOJO.setReting(info.text().trim());
                                                }

                                            }
                                        }
                                    }

                                }
                                if (!bookPOJO.isNull()) {
                                    result.add(bookPOJO);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ ABMP3: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

    @UnstableApi
    private String getAbmp3CoverUrl(String href) {
        if (href == null || href.isEmpty()) {
            return null;
        }
        int idStart = href.indexOf("/audio-");
        if (idStart < 0) {
            return null;
        }
        String rest = href.substring(idStart + 7);
        int dash = rest.indexOf('-');
        if (dash <= 0) {
            return null;
        }
        String id = rest.substring(0, dash);
        String slug = rest.substring(dash + 1);
        if (id.isEmpty() || slug.isEmpty() || !id.matches("\\d+")) {
            return null;
        }
        StringBuilder digitsPath = new StringBuilder();
        for (char c : id.toCharArray()) {
            digitsPath.append(c).append('/');
        }
        return Url.CDN_ABMP3 + "/audiobooks/" + digitsPath + "audiobook-" + slug + ".jpg";
    }

    private ArrayList<BookPOJO> loadBooksListSearchABMP3SpeedUp(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();


        Connection connection = Jsoup.connect(url)
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

        Elements elements = doc.getElementsByClass("b-statictop-search");
        if (elements != null) {
            for (Element item : elements) {
                Elements title = item.getElementsByClass("b-statictop__title");
                if (title != null && title.size() != 0) {
                    if (title.first().text().contains("в книгах")) {
                        Elements parent = item.getElementsByClass("b-statictop__items");
                        if (parent != null && parent.size() != 0) {
                            Elements books = parent.first().children();
                            for (int i = (page - 1) * 40; i < page * 40; i++) {
                                if (i >= books.size()) {
                                    break;
                                }
                                Element book = books.get(i);
                                BookPOJO bookPOJO = new BookPOJO();
                                Elements aTag = book.getElementsByTag("a");
                                if (aTag != null && aTag.size() != 0) {
                                    String href = aTag.first().attr("href");
                                    if (href != null && !href.isEmpty()) {
                                        bookPOJO.setUrl(Url.SERVER_ABMP3 + href);
                                        String cover = getAbmp3CoverUrl(href);
                                        if (cover != null) {
                                            bookPOJO.setPhoto(cover);
                                        }
                                    }
                                    String fullText = aTag.first().text();
                                    if (fullText != null && !fullText.isEmpty()) {
                                        String text = fullText;
                                        int nameEnd = text.indexOf(" (");
                                        if (nameEnd > 0) {
                                            text = text.substring(0, nameEnd);
                                        }
                                        if (!text.isEmpty()) {
                                            bookPOJO.setName(text.trim());
                                        }
                                    }

                                    Elements info = aTag.first().getElementsByClass("add_info");
                                    if (info != null && info.size() != 0) {
                                        String infoText = info.first().text();
                                        String autorName = infoText
                                                .substring(infoText.indexOf("(") + 1, infoText.indexOf(")")).trim();
                                        bookPOJO.setAutor(autorName);
                                        String readerName = infoText
                                                .substring(infoText.lastIndexOf("(") + 1, infoText.lastIndexOf(")"))
                                                .trim();
                                        if (!autorName.equals(readerName)) {
                                            bookPOJO.setArtist(readerName);
                                        }

                                    }

                                }

                                if (!bookPOJO.isNull()) {
                                    result.add(bookPOJO);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ ABMP3 SpeedUp: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

    private ArrayList<BookPOJO> loadBooksListKnigoblud(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();


        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .ignoreHttpErrors(true)
                .maxBodySize(0);

        Document document = connection.get();


        Elements bootom = document.getElementsByClass("PageNavLine1 clearfix");
        if (bootom != null&&bootom.size()>0) {
            Element bootomChild = bootom.first().child(0);
            if (bootomChild != null) {
                Elements list = bootomChild.children();
                if (list != null && list.size() > 1) {
                    Element pages = list.last();
                    if (pages != null) {
                        String last = pages.text();
                        if (last != null) {
                            try {
                                if (page > Integer.parseInt(last)) {
                                    return result;
                                }
                            } catch (NumberFormatException e) {
                                return result;
                            }
                        } else {
                            return result;
                        }
                    } else {
                        return result;
                    }
                } else {
                    return result;
                }
            } else {
                return result;
            }
        } else if (page > 1) {
            return result;
        }

        Element listParent = document.getElementById("BL");
        if (listParent != null) {
            Elements listElements = listParent.getElementsByClass("bookListItem");
            if (listElements != null && listElements.size() != 0) {
                for (Element book : listElements) {
                    BookPOJO bookPOJO = new BookPOJO();

                    Elements imageConteiner = book.getElementsByClass("bookListItemCoverImg js-parallax");
                    if(imageConteiner!=null&&imageConteiner.size()!=0){
                        String image = imageConteiner.first().attr("data-img");
                        if(image!=null&&!image.isEmpty()){
                            bookPOJO.setPhoto(image);
                        }
                    }

                    Elements urlConteiner = book.getElementsByClass("bookListItemCover");
                    if(urlConteiner!=null&&urlConteiner.size()!=0){
                        String href = urlConteiner.first().attr("href");
                        if(href!=null&&!href.isEmpty()){
                            bookPOJO.setUrl(Url.SERVER_KNIGOBLUD+href);
                        }
                    }

                    Elements nameConteiner = book.getElementsByClass("bookListItemCoverNameText");
                    if(nameConteiner!=null&&nameConteiner.size()!=0){
                        String text = nameConteiner.first().text();
                        if(text!=null&&!text.isEmpty()){
                            bookPOJO.setName(text);
                        }
                    }

                    Elements descriptionConteiner = book.getElementsByClass("bookListItemDescription");
                    if(descriptionConteiner!=null&&descriptionConteiner.size()!=0){
                        String text = descriptionConteiner.first().text();
                        if(text!=null&&!text.isEmpty()){
                            bookPOJO.setDesc(text);
                        }
                    }

                    Elements timeConteiner = book.getElementsByClass("bookListItemNameDur");
                    if(timeConteiner!=null&&timeConteiner.size()!=0){
                        String text = timeConteiner.first().text();
                        if(text!=null&&!text.isEmpty()){
                            bookPOJO.setTime(text);
                        }
                    }

                    Elements gnreConteiner = book.getElementsByClass("bookListItemGenreLink");
                    if(gnreConteiner!=null&&gnreConteiner.size()!=0){
                        String href = gnreConteiner.first().attr("href");
                        String text = gnreConteiner.first().text();
                        if(href!=null&&!href.isEmpty()){
                            bookPOJO.setUrlGenre(Url.SERVER_KNIGOBLUD+href);
                        }

                        if(text!=null&&!text.isEmpty()){
                            bookPOJO.setGenre(text);
                        }

                    }

                    Elements metaBloks = book.getElementsByClass("bookListItemMetaBlock");
                    if(metaBloks!=null){
                        for(Element metaBlock:metaBloks){
                            String metaBlocktext = metaBlock.text();
                            if(metaBlocktext.contains("\uD83D\uDCDA")){
                                Elements aTag = metaBlock.getElementsByTag("a");
                                if(aTag!=null&&aTag.size()!=0){
                                    String href = aTag.first().attr("href");
                                    String text = aTag.first().text();
                                    if(href!=null&&!href.isEmpty()){
                                        bookPOJO.setUrlSeries(Url.SERVER_KNIGOBLUD+href);
                                    }
                                    if(text!=null&&!text.isEmpty()){
                                        bookPOJO.setSeries(text);
                                    }

                                }
                            }else if (metaBlocktext.contains("✍")&&metaBlocktext.contains("\uD83C\uDF99")){
                                Element element = metaBlock.child(1);
                                if(element!=null){
                                    String href = element.attr("href");
                                    String text = element.text();
                                    if(href!=null&&!href.isEmpty()){
                                        bookPOJO.setUrlAutor(Url.SERVER_KNIGOBLUD+href);
                                    }
                                    if(text!=null&&!text.isEmpty()){
                                        bookPOJO.setAutor(text);
                                    }
                                }
                                element = metaBlock.child(3);
                                if(element!=null){
                                    String href = element.attr("href");
                                    String text = element.text();
                                    if(href!=null&&!href.isEmpty()){
                                        bookPOJO.setUrlArtist(Url.SERVER_KNIGOBLUD+href);
                                    }
                                    if(text!=null&&!text.isEmpty()){
                                        bookPOJO.setArtist(text);
                                    }
                                }
                            }else if (metaBlocktext.contains("✍")){
                                Element element = metaBlock.child(1);
                                if(element!=null){
                                    String href = element.attr("href");
                                    String text = element.text();
                                    if(href!=null&&!href.isEmpty()){
                                        bookPOJO.setUrlAutor(Url.SERVER_KNIGOBLUD+href);
                                    }
                                    if(text!=null&&!text.isEmpty()){
                                        bookPOJO.setAutor(text);
                                    }
                                }
                            }else if (metaBlocktext.contains("\uD83C\uDF99")){
                                Element element = metaBlock.child(1);
                                if(element!=null){
                                    String href = element.attr("href");
                                    String text = element.text();
                                    if(href!=null&&!href.isEmpty()){
                                        bookPOJO.setUrlArtist(Url.SERVER_KNIGOBLUD+href);
                                    }
                                    if(text!=null&&!text.isEmpty()){
                                        bookPOJO.setArtist(text);
                                    }
                                }
                            }
                        }
                    }

                    if (bookPOJO.isNull()) {
                        continue;
                    }
                    result.add(bookPOJO);
                }

            }
        }

        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Knigoblud: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksListOtrub(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        Document doc = connection.get();

        Element pnContainer = doc.getElementById("books_updates_list__pn");
        if (pnContainer == null) {
            pnContainer = doc.getElementById("books_list__pn");
        }
        if (pnContainer != null) {
            Element pnChild = pnContainer.child(0);
            if (pnChild != null) {
                Elements aPages = pnChild.select("a[data-page]");
                if (aPages.size() > 1) {
                    Element lastPage = aPages.last();
                    if (lastPage != null) {
                        try {
                            int last = Integer.parseInt(lastPage.attr("data-page"));
                            if (page > last) {
                                return result;
                            }
                        } catch (NumberFormatException e) {
                            return result;
                        }
                    }
                }
            }
        } else if (page > 1) {
            return result;
        }

        Element listParent = doc.getElementById("books_updates_list");
        if (listParent == null) {
            listParent = doc.getElementById("books_list");
        }
        if (listParent != null) {
            Elements books = listParent.select("._cb0a41 > ._dad4fa");
            if (books.isEmpty()) return result;
            for (Element book : books) {
                BookPOJO bookPOJO = new BookPOJO();

                Elements img = book.select("._bce453 img._76d12c");
                if (img != null && img.size() > 0) {
                    bookPOJO.setPhoto(img.first().attr("src"));
                }

                Elements genreLinks = book.select("._eeab32 b a");
                if (genreLinks != null && genreLinks.size() > 0) {
                    String href = genreLinks.first().attr("href");
                    if (href != null && !href.isEmpty()) {
                        bookPOJO.setUrlGenre(Url.SERVER_OTRUB + href + "?p=");
                    }
                    String name = genreLinks.first().text();
                    if (name != null && !name.isEmpty()) {
                        bookPOJO.setGenre(name);
                    }
                }

                Elements titleLinks = book.select("._3dc935 a");
                if (titleLinks != null && titleLinks.size() > 0) {
                    String href = titleLinks.first().attr("href");
                    if (href != null && !href.isEmpty()) {
                        bookPOJO.setUrl(Url.SERVER_OTRUB + href);
                    }
                    String name = titleLinks.first().text();
                    if (name != null && !name.isEmpty()) {
                        bookPOJO.setName(name);
                    }
                }

                Elements infoBlocks = book.select("._5d10b1 ._eeab32");
                if (infoBlocks != null) {
                    for (Element block : infoBlocks) {
                        Element label = block.select("._a6370b").first();
                        if (label != null) {
                            String labelText = label.text();
                            Element a = block.select("a").first();
                            if (a != null) {
                                String href = a.attr("href");
                                String name = a.text();
                                if (labelText.contains("Автор")) {
                                    bookPOJO.setAutor(name);
                                    if (href != null && !href.isEmpty()) {
                                        bookPOJO.setUrlAutor(Url.SERVER_OTRUB + href);
                                    }
                                } else if (labelText.contains("Читает")) {
                                    bookPOJO.setArtist(name);
                                    if (href != null && !href.isEmpty()) {
                                        bookPOJO.setUrlArtist(Url.SERVER_OTRUB + href);
                                    }
                                } else if (labelText.contains("Цикл") || labelText.contains("Серия")) {
                                    bookPOJO.setSeries(name);
                                    if (href != null && !href.isEmpty()) {
                                        if (href.startsWith("/")) href = Url.SERVER_OTRUB + href;
                                        bookPOJO.setUrlSeries(href);
                                    }
                                }
                            }
                        }
                    }
                }

                Elements desc = book.select("._b1f6b9");
                if (desc != null && desc.size() > 0) {
                    bookPOJO.setDesc(desc.first().text());
                }

                if (bookPOJO.isNull()) {
                    continue;
                }
                result.add(bookPOJO);
            }
        }
        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Otrub: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

    private void setSiries(String url) throws IOException {
        if (genreName == null || genreSrc == null || authorName == null || authorUrl == null) {
            Document doc = Jsoup.connect(url)
                    .userAgent(Consts.USER_AGENT)
                    .sslSocketFactory(Consts.socketFactory())
                    .referrer("http://www.google.com")
                    .maxBodySize(0)
                    .ignoreHttpErrors(true)
                    .get();

            Elements elements = doc.getElementsByClass("_7e215f");
            if (elements != null && elements.size() != 0) {
                Elements aTag = elements.first().getElementsByTag("a");
                if (aTag != null && aTag.size() != 0) {
                    Element a = aTag.first();
                    String urlGenre = a.attr("href");
                    if (urlGenre != null) {
                        genreSrc = Url.SERVER_IZIBUK + urlGenre + "?p=";
                    }
                    String genre = a.text();
                    if (genre != null) {
                        genreName = genre;
                    }

                }

                Elements autorConteiners = doc.getElementsByAttributeValue("itemprop", "author");
                if (autorConteiners != null && autorConteiners.size() != 0) {
                    Elements autor = autorConteiners.first().getElementsByTag("a");
                    if (autor != null && autor.size() != 0) {
                        Element a = autor.first();
                        String urlAutor = a.attr("href");
                        if (urlAutor != null) {
                            authorUrl = Url.SERVER_IZIBUK + urlAutor + "?p=";
                        }
                        String autorName = a.text();
                        if (autorName != null) {
                            authorName = autorName;
                        }

                    }
                }
            }
        }
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksListAudioboo(String url, int page, String nameModel, Context context) throws IOException {
        String cleanUrl = url;
        int idx = cleanUrl.indexOf("/page/");
        if (idx >= 0) {
            cleanUrl = cleanUrl.substring(0, idx) + "/";
        }

        String sortVal = "date";
        if (nameModel.contains(context.getString(R.string.order_new))){
            sortVal = "date";
        }else if (nameModel.contains(context.getString(R.string.order_reting))){
            sortVal = "rating";
        }else if (nameModel.contains(context.getString(R.string.order_popular))){
            sortVal = "views";
        }else if (nameModel.contains(context.getString(R.string.order_coments))) {
            sortVal = "comm";
        }else if (nameModel.contains(context.getString(R.string.order_title))) {
            sortVal = "title";
        }else if (nameModel.contains(context.getString(R.string.order_duration))) {
            sortVal = "time";
        }

        Connection connection = Jsoup.connect(cleanUrl)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .data("xsort", "1")
                .data("xs_field", "defaultsort")
                .data("xs_value", sortVal)
                .ignoreHttpErrors(true)
                .maxBodySize(0);

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Document doc = connection.post();
        return parceAudioboo(doc, page);
    }

    @UnstableApi
    private ArrayList<BookPOJO> loadBooksListAudiobooSearch(String url, int page) throws IOException {

        Log.d("AUDIOBOO_SEARCH", "url=" + url + " page=" + page);
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl != null) {
            String query = httpUrl.queryParameter("q");
            HttpUrl clean = httpUrl.newBuilder()
                    .removeAllQueryParameters("q")
                    .removeAllQueryParameters("page")
                    .build();

            Log.d("AUDIOBOO_SEARCH", "cleanUrl=" + clean.toString() + " query=" + query);
            Connection connection = Jsoup.connect(clean.toString())
                    .userAgent(Consts.USER_AGENT)
                    .referrer("http://www.google.com")
                    .sslSocketFactory(Consts.socketFactory())
                    .data("do", "search")
                    .data("subaction", "search")
                    .data("search_start", String.valueOf(page))
                    .data("full_search", "0")
                    .data("result_from", String.valueOf((page-1)*10+1))
                    .data("story", query)
                    .ignoreHttpErrors(true)
                    .maxBodySize(0);

            if(App.useProxy) {
                Proxy proxy = new Proxy(Type.SOCKS,
                        new InetSocketAddress(PROXY_HOST, PROXY_PORT));
                connection.proxy(proxy);
            }

            Document doc = connection.post();
            Log.d("AUDIOBOO_SEARCH", "response title=" + doc.title() + " status=200");
            return parceAudioboo(doc, page);
        }
        Log.d("AUDIOBOO_SEARCH", "httpUrl is null, returning empty");
        ArrayList<BookPOJO> errList = new ArrayList<>();
        BookPOJO eb = new BookPOJO();
        eb.setName("⚠ Audioboo: неверный URL");
        eb.setUrl("about:blank");
        errList.add(eb);
        return errList;
            }

            @UnstableApi
            private ArrayList<BookPOJO> loadBooksListAudiobooSearchMulti(String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .ignoreHttpErrors(true)
                .maxBodySize(0);

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Document doc = connection.get();
        return parceAudioboo(doc, 1);
            }

            private ArrayList<BookPOJO> parceAudioboo(Document doc, int page){
                    ArrayList<BookPOJO> result = new ArrayList<>();
                    Elements books = doc.getElementsByClass("card d-flex");
                    if (books.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Audioboo: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
            return result;
                    }
        for (Element book : books) {
            BookPOJO bookPOJO = new BookPOJO();
            
            Elements imgWrapper = book.getElementsByClass("card__img");
            if (!imgWrapper.isEmpty()) {
                bookPOJO.setUrl(imgWrapper.first().attr("href"));
                Elements img = imgWrapper.first().getElementsByTag("img");
                if (!img.isEmpty()) {
                    String imgUrl = img.first().attr("src");
                    if(imgUrl.startsWith("/")) imgUrl = Url.SERVER_AUDIOBOO + imgUrl;
                    bookPOJO.setPhoto(imgUrl);
                    bookPOJO.setName(img.first().attr("alt"));
                }
            }
            
            Elements title = book.getElementsByClass("card__title");
            if (!title.isEmpty()) {
                bookPOJO.setName(title.first().text());
            }

            Elements listItems = book.getElementsByTag("li");
            for (Element li : listItems) {
                String text = li.text();
                Elements a = li.getElementsByTag("a");
                if (!a.isEmpty()) {
                    String href = a.first().attr("href");
                    if (href != null && href.startsWith("/")) {
                        href = Url.SERVER_AUDIOBOO + href;
                    }
                    if (text.contains("Жанр:")) {
                        bookPOJO.setGenre(a.first().text());
                        bookPOJO.setUrlGenre(href);
                    } else if (text.contains("Автор:")) {
                        bookPOJO.setAutor(a.first().text());
                        bookPOJO.setUrlAutor(href);
                    } else if (text.contains("Читает:") || text.contains("Исполнитель:")) {
                        bookPOJO.setArtist(a.first().text());
                        bookPOJO.setUrlArtist(href);
                    } else if (text.contains("Цикл:")) {
                        bookPOJO.setSeries(a.first().text());
                        bookPOJO.setUrlSeries(href);
                    }
                }
                
                if (text.contains("Время:") && !text.contains("Время звучания:")) {
                    bookPOJO.setTime(text.replace("Время:", "").trim());
                }
            }

            if (bookPOJO.getSeries() == null || bookPOJO.getSeries().isEmpty()) {
                Elements seriesElements = book.select("a[href*=/xfsearch/cikl/]");
                if (seriesElements != null && !seriesElements.isEmpty()) {
                    Element aTag = seriesElements.first();
                    bookPOJO.setSeries(aTag.text());
                    String href = aTag.attr("href");
                    if (href != null && !href.isEmpty()) {
                        if (href.startsWith("/")) href = Url.SERVER_AUDIOBOO + href;
                        bookPOJO.setUrlSeries(href);
                    }
                }
            }
            
            Elements duration = book.getElementsByClass("card__ratings");
            if (!duration.isEmpty()) {
                Elements rub = duration.first().getElementsByClass("rubbery");
                if (!rub.isEmpty()) {
                    bookPOJO.setTime(rub.first().text().replace("Время звучания:", "").trim());
                }
            }
            
            Elements desc = book.getElementsByClass("card__text");
            if (!desc.isEmpty()) {
                bookPOJO.setDesc(desc.first().text());
            }

            Element cardMetas = book.selectFirst(".card__metas");
            if (cardMetas != null) {
                Element currentRating = cardMetas.selectFirst(".rating .unit-rating .current-rating");
                if (currentRating != null) {
                    String style = currentRating.attr("style");
                    if (style != null && style.contains("width:")) {
                        String widthStr = style.replaceAll(".*width:\\s*", "").replaceAll("[%\\s;].*", "");
                        try {
                            float width = Float.parseFloat(widthStr);
                            float stars = width / 20.0f;
                            Element voteSpan = cardMetas.selectFirst("span[id^=\"vote-num-id-\"]");
                            String votes = voteSpan != null ? voteSpan.text().trim() : "0";
                            if (stars > 0) {
                                bookPOJO.setReting(BookPOJO.formatRating(stars, votes));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            if (!bookPOJO.isNull()) {
                result.add(bookPOJO);
            }
                    }
                    if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Audioboo: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
                    }
                    return result;
                }

                private ArrayList<BookPOJO> loadBooksListPoleknig(String url, int page) throws IOException {
        ArrayList<BookPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        Document doc = connection.get();

        Elements books = doc.select("div.book-item");
        if (books.isEmpty()) {
            if (page > 1) return result;
            return result;
        }

        for (Element book : books) {
            BookPOJO bookPOJO = new BookPOJO();

            Element img = book.selectFirst("img");
            if (img != null) {
                String src = img.attr("data-original");
                if (src == null || src.isEmpty()) src = img.attr("data-originl");
                if (src == null || src.isEmpty()) src = img.attr("src");
                if (src != null && !src.isEmpty()) {
                    if (!src.startsWith("http")) {
                        src = Url.SERVER_POLEKNIG + src;
                    }
                    bookPOJO.setPhoto(src);
                }
            }

            Element titleEl = book.selectFirst("a.book-title");
            if (titleEl != null) {
                String href = titleEl.attr("href");
                if (href != null && !href.isEmpty()) {
                    bookPOJO.setUrl(href);
                }
                String name = titleEl.text();
                if (name != null && !name.isEmpty()) {
                    bookPOJO.setName(name);
                }
                            }

            Element authorEl = book.selectFirst(".book-author a");
            if (authorEl != null) {
                String href = authorEl.attr("href");
                if (href != null && !href.isEmpty()) {
                    bookPOJO.setUrlAutor(href);
                }
                String name = authorEl.text();
                if (name != null && !name.isEmpty()) {
                    bookPOJO.setAutor(name);
                }
            }

            Element readerEl = book.selectFirst(".book-reader a");
            if (readerEl != null) {
                String href = readerEl.attr("href");
                if (href != null && !href.isEmpty()) {
                    bookPOJO.setUrlArtist(href);
                }
                String name = readerEl.text();
                if (name != null && !name.isEmpty()) {
                    bookPOJO.setArtist(name);
                }
            }

            Element genreEl = book.selectFirst(".book-genres a");
            if (genreEl != null) {
                String href = genreEl.attr("href");
                if (href != null && !href.isEmpty()) {
                    bookPOJO.setUrlGenre(href);
                }
                String name = genreEl.text();
                if (name != null && !name.isEmpty()) {
                    bookPOJO.setGenre(name);
                }
            }

            Element seriesEl = book.selectFirst("a[href*=/series/]");
            if (seriesEl == null) {
                seriesEl = book.selectFirst(".book-series a");
            }
            if (seriesEl != null) {
                String href = seriesEl.attr("href");
                if (href != null && !href.isEmpty()) {
                    if (href.startsWith("/")) href = Url.SERVER_POLEKNIG + href;
                    bookPOJO.setUrlSeries(href);
                }
                String name = seriesEl.text();
                if (name != null && !name.isEmpty()) {
                    bookPOJO.setSeries(name);
                }
            }

            Element durationEl = book.selectFirst(".book-duration");
            if (durationEl != null) {
                String time = durationEl.text();
                if (time != null && !time.isEmpty()) {
                    bookPOJO.setTime(time);
                }
            }

            if (bookPOJO.isNull()) {
                continue;
            }
            result.add(bookPOJO);
        }

        if (result.isEmpty()) {
            BookPOJO eb = new BookPOJO();
            eb.setName("⚠ Poleknig: нет результатов / ошибка подключения");
            eb.setUrl("about:blank");
            result.add(eb);
        }
        return result;
    }

}
