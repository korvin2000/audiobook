package com.fanok.audiobooks.model;




import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import androidx.media3.common.util.UnstableApi;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.AutorsSearchABMP3;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.R;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.ArrayList;
import java.util.Map;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class GenreModel implements com.fanok.audiobooks.interface_pacatge.books.GenreModel {


    @UnstableApi
    @Override
    public Observable<ArrayList<GenrePOJO>> getBooks(String url, int page) {
        int size = 4;
        return Observable.create(observableEmitter -> {
            ArrayList<GenrePOJO> articlesModels;
            try {
                if (url.contains(Url.SERVER_AKNIGA) && url.contains("ajax-search")) {
                    if (page > 1) {
                        throw new NullPointerException();
                    }
                    String qery = url.substring(url.indexOf("?q="));
                    qery = qery.replace("?q=", "");
                    String url1 = url.substring(0, url.indexOf("?q="));
                    articlesModels = searchAutorAbook(url1, qery);
                    observableEmitter.onNext(articlesModels);
                } else if (url.contains(String.valueOf(page))) {
                    for (int i = 1; i <= size; i++) {
                        int temp = (page - 1) * size + i;
                        if (url.contains(Url.SERVER)) {
                            articlesModels = loadBooksList(
                                    url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                        } else if (url.contains(Url.SERVER_IZIBUK)) {
                            articlesModels = loadBooksListIzibuk(
                                    url.replace(String.valueOf(page), String.valueOf(temp)), temp);
                        } else if (url.contains(Url.SERVER_ABMP3)) {
                            articlesModels = loadBooksListABMP3(
                                    url.replace("?page=" + page + "/", "?page=" + temp), temp);
                        } else if (url.contains(Url.SERVER_AKNIGA)) {
                            articlesModels = loadBooksListAbook(
                                    url.replace("page" + page + "/", "page" + temp), temp);
                        } else {
                            articlesModels = new ArrayList<>();
                        }
                        observableEmitter.onNext(articlesModels);
                    }
                } else {
                    if (url.contains(Url.SERVER)) {
                        articlesModels = loadBooksList(url, page);
                    } else if (url.contains(Url.SERVER_IZIBUK)) {
                        articlesModels = loadBooksListIzibuk(url, page);
                    } else if (url.contains(Url.SERVER_ABMP3)) {
                        if (url.contains("search")) {
                            articlesModels = new AutorsSearchABMP3().getAutors(url, page);
                        } else {
                            articlesModels = loadBooksListABMP3(url + "?page=", page);
                        }
                    } else if (url.contains(Url.SERVER_AKNIGA)) {
                        articlesModels = loadBooksListAbook(url, page);
                    } else if (url.contains("baza-knig")) {
                        articlesModels = loadBooksListBazaKnig(url, page);
                    } else if(url.contains(Url.SECTIONS_KNIGOBLUD)) {
                        articlesModels = loadBooksListKnigoblud();
                    }else if(url.contains(Url.INDEX_AUDIOBOO)) {
                        articlesModels = loadBooksListAudioboo(url, page);
                    } else if (url.contains(Url.SERVER_OTRUB)) {
                        articlesModels = loadBooksListOtrub(url, page);
                    } else if (url.contains(Url.SERVER_POLEKNIG)) {
                        articlesModels = loadBooksListPoleknig(url, page);
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

    protected ArrayList<GenrePOJO> loadBooksList(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements items = doc.getElementsByClass("genre2_item");
        for (Element item : items) {
            GenrePOJO genrePOJO = new GenrePOJO();
            Elements name = item.getElementsByClass("genre2_item_name");
            if (name.size() != 0) {
                genrePOJO.setUrl(Url.SERVER + name.first().attr("href") + "<page>/");
                genrePOJO.setName(name.first().text());
            }

            Elements rating = item.getElementsByClass("subscribe_btn_label_count");
            if (rating.size() != 0) {
                genrePOJO.setReting(Integer.parseInt(rating.first().text()));
            }

            Elements description = item.getElementsByClass("genre2_item_description");
            if (description.size() != 0) {
                genrePOJO.setDescription(description.first().text());
            }
            if (!genrePOJO.isNull()) {
                result.add(genrePOJO);
            }
        }
        return result;
    }

    @UnstableApi
    protected ArrayList<GenrePOJO> loadBooksListABMP3(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url + page)
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

        Elements items = doc.getElementsByClass("b-posts");
        if (items != null && items.size() != 0) {
            Elements list = items.first().children();
            if (list != null && list.size() > 0) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();
                    Elements titles = item.getElementsByClass("title");
                    if (titles != null && titles.size() != 0) {
                        Elements aGenre = titles.first().getElementsByTag("a");
                        if (aGenre != null && aGenre.size() != 0) {
                            genrePOJO.setUrl(Url.SERVER_ABMP3 + aGenre.first().attr("href") + "?page=");
                            genrePOJO.setName(aGenre.first().text());
                        }
                    }
                    Elements ratings = item.getElementsByClass("rating");
                    if (ratings != null && ratings.size() != 0) {
                        genrePOJO.setDescription(ratings.first().text().trim().replace("кни", " кни"));
                    }

                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }
        }
        return result;
    }

    protected ArrayList<GenrePOJO> loadBooksListAbook(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements items = doc.getElementsByClass("table-authors");
        if (items != null && items.size() != 0) {
            Elements tbody = items.first().getElementsByTag("tbody");
            if (tbody != null && tbody.size() != 0) {
                Elements list = tbody.first().children();
                if (list != null && list.size() > 0) {
                    for (Element item : list) {
                        GenrePOJO genrePOJO = new GenrePOJO();
                        Elements titles = item.getElementsByClass("name-obj");
                        if (titles != null && titles.size() != 0) {
                            Elements aGenre = titles.first().getElementsByTag("a");
                            if (aGenre != null && aGenre.size() != 0) {
                                genrePOJO.setUrl(aGenre.first().attr("href") + "page<page>/");
                                genrePOJO.setName(aGenre.first().text());
                            }
                        }
                        Elements description = item.getElementsByClass("description");
                        if (description != null && description.size() != 0) {
                            genrePOJO.setDescription(description.first().text().trim());
                        }

                        Elements reting = item.getElementsByClass("cell-rating");
                        if (reting != null && reting.size() != 0) {
                            genrePOJO.setReting(Integer.parseInt(reting.first().text().trim()));
                        }

                        if (!genrePOJO.isNull()) {
                            result.add(genrePOJO);
                        }
                    }
                }
            }
        }
        return result;
    }

    protected ArrayList<GenrePOJO> loadBooksListIzibuk(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
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

        Elements items = doc.getElementsByClass("_e181af");
        if (items != null && items.size() != 0) {
            Elements list = items.first().children();
            if (list != null) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();

                    String src = item.attr("href");
                    if (src != null) {
                        genrePOJO.setUrl(Url.SERVER_IZIBUK + src + "?p=");
                    }

                    Elements children = item.children();
                    if (children != null && children.size() == 2) {
                        String name = children.first().text();
                        if (name != null) {
                            genrePOJO.setName(name);
                        }

                        String desc = children.last().text();
                        if (desc != null) {
                            genrePOJO.setDescription(desc);
                        }
                    }

                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }
        }
        return result;
    }

    private ArrayList<GenrePOJO> loadBooksListBazaKnig(final String url, final int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .maxBodySize(0)
                .sslSocketFactory(Consts.socketFactory());

        if (!Consts.getBazaKnigCookies().isEmpty()) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Document doc = connection.get();

        if (doc.title().contains("Just a moment")) {
            throw new CookesExeption(url);
        }

        Elements items = doc.getElementsByClass("reset left-menu-items");
        if (items != null && items.size() != 0) {
            Elements list = items.first().getElementsByTag("li");
            if (list != null && list.size() > 0) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();
                    Elements aGenre = item.getElementsByTag("a");
                    if (aGenre != null && aGenre.size() != 0) {
                        genrePOJO.setUrl(Url.SERVER_BAZA_KNIG + aGenre.first().attr("href") + "page/");
                        genrePOJO.setName(item.text());
                    }
                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }
        }
        return result;
    }

    private ArrayList<GenrePOJO> loadBooksListKnigoblud() throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        result.add(new GenrePOJO("Фантастика, фэнтези", Url.SERVER_KNIGOBLUD+"/9d47c7aa-9e2e-4fe0-bcd5-14f7d4874198"));
        result.add(new GenrePOJO("Детективы, триллеры, боевики", Url.SERVER_KNIGOBLUD+"/4daab267-3592-4604-9e34-8f3b6a1db9fe"));
        result.add(new GenrePOJO("Аудиоспектакли, радиопостановки и литературные чтения", Url.SERVER_KNIGOBLUD+"/0efa0796-8734-4735-ac03-d4eca1ccb013"));
        result.add(new GenrePOJO("Бизнес, личностный рост", Url.SERVER_KNIGOBLUD+"/8ea14af7-f600-4e30-a0eb-67b615cdb701"));
        result.add(new GenrePOJO("Биографии, мемуары, ЖЗЛ", Url.SERVER_KNIGOBLUD+"/760dd554-482b-4817-8283-5b9ae9e76431"));
        result.add(new GenrePOJO("Для детей, аудиосказки, стишки", Url.SERVER_KNIGOBLUD+"/5982eae9-3868-4593-a9b0-b3378370345f"));
        result.add(new GenrePOJO("История, культурология", Url.SERVER_KNIGOBLUD+"/31d39d95-7bf5-420b-9db4-66c448785a07"));
        result.add(new GenrePOJO("Классика", Url.SERVER_KNIGOBLUD+"/0c6d4928-555a-4671-99e9-9bcf9a37d382"));
        result.add(new GenrePOJO("Медицина, здоровье", Url.SERVER_KNIGOBLUD+"/f00832de-609c-4cc8-9017-2571caa4bfb2"));
        result.add(new GenrePOJO("На иностранных языках", Url.SERVER_KNIGOBLUD+"/fb8d64ce-902d-44aa-9c21-77cb85a5b418"));
        result.add(new GenrePOJO("Научно-популярное", Url.SERVER_KNIGOBLUD+"/9da19d35-32ee-41e7-ab8c-5ec554fcdd8a"));
        result.add(new GenrePOJO("Обучение", Url.SERVER_KNIGOBLUD+"/7445b383-418a-475a-8374-ef2822d0552c"));
        result.add(new GenrePOJO("Поэзия", Url.SERVER_KNIGOBLUD+"/a1d4487a-12d6-4280-b1a2-4c204bd6908a"));
        result.add(new GenrePOJO("Приключения, военные приключения", Url.SERVER_KNIGOBLUD+"/e7d0d5bc-210a-4fc6-9598-19baf47c9b13"));
        result.add(new GenrePOJO("Психология, философия", Url.SERVER_KNIGOBLUD+"/09afc417-fa54-4b56-bb54-f950296a0b50"));
        result.add(new GenrePOJO("Разное", Url.SERVER_KNIGOBLUD+"/697856ad-1d48-48d5-ad20-5f00cc169f75"));
        result.add(new GenrePOJO("Ранобэ", Url.SERVER_KNIGOBLUD+"/07bc7998-8f32-49a1-8048-07a2c2294a11"));
        result.add(new GenrePOJO("Религия", Url.SERVER_KNIGOBLUD+"/4c37cad9-8ee6-4ce7-b62e-b52ef717952c"));
        result.add(new GenrePOJO("Роман, проза", Url.SERVER_KNIGOBLUD+"/61c37161-1899-4253-a4c3-9417f4b5c1c5"));
        result.add(new GenrePOJO("Ужасы, мистика, хоррор", Url.SERVER_KNIGOBLUD+"/120a1664-12e5-4ac6-853f-792f6a8007e9"));
        result.add(new GenrePOJO("Эзотерика, Нетрадиционные религиозно-философские учения", Url.SERVER_KNIGOBLUD+"/e4117eca-c027-4f80-8004-3851d9b562c7"));
        result.add(new GenrePOJO("Юмор, сатира", Url.SERVER_KNIGOBLUD+"/70bb89e8-65f7-414d-b98d-107878734c89"));
        return result;
    }

    private ArrayList<GenrePOJO> searchAutorAbook(String url, String qery) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Response response = Jsoup.connect(Url.SERVER_AKNIGA + "/authors/")
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER_AKNIGA + "/performers/")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .execute();
        Map<String, String> cookies = response.cookies();

        Document doc = response.parse();

        Elements sriptTag = doc.getElementsByTag("script");
        String key = "";
        if (sriptTag != null) {
            for (Element script : sriptTag) {
                String text = script.data();
                if (text != null && text.contains("LIVESTREET_SECURITY_KEY")) {
                    text = text.substring(text.indexOf("LIVESTREET_SECURITY_KEY"));
                    text = text.replace("LIVESTREET_SECURITY_KEY = '", "");
                    key = text.substring(0, text.indexOf("'"));
                    break;
                }
            }
        }

        String text = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .method(Method.POST)
                .referrer(Url.SERVER_AKNIGA + "/authors/")
                .sslSocketFactory(Consts.socketFactory())
                .data("security_ls_key", key)
                .data("sText", qery)
                .maxBodySize(0)
                .ignoreContentType(true)
                .cookies(cookies)
                .maxBodySize(0)
                .execute()
                .body();
        JsonElement json = JsonParser.parseString(text.replaceAll("\\n", ""));
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            String html = jsonObject.get("html").getAsString();
            doc = Jsoup.parse("<html><head></head><body><table>" + html + "</table></body></html>");
            Elements tr = doc.getElementsByTag("tr");
            if (tr != null && tr.size() != 0) {
                for (Element item : tr) {
                    GenrePOJO genrePOJO = new GenrePOJO();
                    Elements titles = item.getElementsByClass("name-obj");
                    if (titles != null && titles.size() != 0) {
                        Elements aGenre = titles.first().getElementsByTag("a");
                        if (aGenre != null && aGenre.size() != 0) {
                            genrePOJO.setUrl(aGenre.first().attr("href") + "/page<page>/");
                            genrePOJO.setName(aGenre.first().text());
                        }
                    }
                    Elements description = item.getElementsByClass("description");
                    if (description != null && description.size() != 0) {
                        genrePOJO.setDescription(description.first().text().trim());
                    }

                    Elements reting = item.getElementsByClass("cell-rating");
                    if (reting != null && reting.size() != 0) {
                        genrePOJO.setReting(Integer.parseInt(reting.first().text().trim()));
                    }

                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }

        }

        return result;
    }


    @UnstableApi
    protected ArrayList<GenrePOJO> loadBooksListAudioboo (String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url+page)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .data("dlenewssortby", "date")
                .data("dledirection", "desc")
                .data("set_new_sort", "dle_sort_main")
                .data("set_direction_sort", "dle_direction_main")
                .maxBodySize(0);

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Document doc = connection.post();

        Elements genreItems = doc.select("ul.xsort-ul[data-field=category] > li");
        if (genreItems != null && genreItems.size() > 0) {
            for (Element item : genreItems) {
                String val = item.attr("data-val");
                if (val == null || val.isEmpty()) continue;
                String name = item.text();
                if (name == null || name.isEmpty()) continue;
                String slug = genreNameToSlug(name);
                if (slug == null || slug.isEmpty()) continue;
                GenrePOJO genrePOJO = new GenrePOJO();
                genrePOJO.setUrl(Url.SERVER_AUDIOBOO + "/" + slug + "/page/");
                genrePOJO.setName(name);
                result.add(genrePOJO);
            }
        }

        return result;
    }

    private static String genreNameToSlug(String name) {
        switch (name) {
            case "Альтернативная история": return "altist";
            case "Аудиоспектакль": return "audiocpektakl";
            case "Бизнес": return "biz";
            case "Биография": return "biografiq";
            case "Боевик": return "boevik";
            case "Война": return "voina";
            case "Детектив": return "didiktiva";
            case "Детская литература": return "detskaya-literatura";
            case "Драма": return "drama";
            case "Интервью": return "intervy";
            case "История": return "istoria";
            case "Классика": return "klassika";
            case "Лекция": return "lekciq";
            case "Любовное Фэнтези": return "lffr";
            case "Медицина": return "medicina";
            case "Мемуары": return "memuari";
            case "Мистика": return "mictika";
            case "Новелла": return "novella";
            case "Повесть": return "povest";
            case "Познавательная литература": return "poznavatelnaya-literatura";
            case "Попаданцы": return "popadanci";
            case "Постапокалипсис": return "postapokalipsis";
            case "Поэзия": return "poeziq";
            case "Притча": return "pritcha";
            case "Приключения": return "priklyucheniq";
            case "Проза": return "proza";
            case "Психология": return "psihologia";
            case "Публицистика": return "publicictika";
            case "Ранобэ": return "ranobe";
            case "Религия": return "religiq";
            case "Роман": return "roman";
            case "Сказка": return "skazka";
            case "Стихи": return "stihi";
            case "Триллер": return "triller";
            case "Ужасы": return "ugasy";
            case "Учебник": return "uchebnik";
            case "Фантастика": return "fantastika";
            case "Философия": return "filosofiq";
            case "Фэнтези": return "fenezi";
            case "Хоррор": return "horror";
            case "Эзотерика": return "ezoterika";
            case "Эротика": return "erotika";
            case "Юмор": return "umor";
            default: return transliterateSimple(name);
        }
    }

    private static String transliterateSimple(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            switch (c) {
                case 'а': sb.append('a'); break;
                case 'б': sb.append('b'); break;
                case 'в': sb.append('v'); break;
                case 'г': sb.append('g'); break;
                case 'д': sb.append('d'); break;
                case 'е': sb.append('e'); break;
                case 'ё': sb.append('e'); break;
                case 'ж': sb.append('z'); break;
                case 'з': sb.append('z'); break;
                case 'и': sb.append('i'); break;
                case 'й': sb.append('y'); break;
                case 'к': sb.append('k'); break;
                case 'л': sb.append('l'); break;
                case 'м': sb.append('m'); break;
                case 'н': sb.append('n'); break;
                case 'о': sb.append('o'); break;
                case 'п': sb.append('p'); break;
                case 'р': sb.append('r'); break;
                case 'с': sb.append('s'); break;
                case 'т': sb.append('t'); break;
                case 'у': sb.append('u'); break;
                case 'ф': sb.append('f'); break;
                case 'х': sb.append('h'); break;
                case 'ц': sb.append('c'); break;
                case 'ч': sb.append("ch"); break;
                case 'ш': sb.append("sh"); break;
                case 'щ': sb.append("sch"); break;
                case 'ъ': break;
                case 'ы': sb.append('i'); break;
                case 'ь': break;
                case 'э': sb.append('e'); break;
                case 'ю': sb.append("yu"); break;
                case 'я': sb.append("ya"); break;
                case ' ': sb.append('-'); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private ArrayList<GenrePOJO> loadBooksListOtrub(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();

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

        Document doc = connection.get();

        Elements genreItems = doc.select("a._b8c4a1");
        if (genreItems != null && genreItems.size() > 0) {
            for (Element item : genreItems) {
                GenrePOJO genrePOJO = new GenrePOJO();
                String href = item.attr("href");
                if (href != null && !href.isEmpty()) {
                    genrePOJO.setUrl(Url.SERVER_OTRUB + href + "?p=");
                }
                Element nameEl = item.select("._28f7ab").first();
                if (nameEl != null) {
                    String name = nameEl.text();
                    if (name != null && !name.isEmpty()) {
                        genrePOJO.setName(name);
                    }
                }
                Element countEl = item.select("._fb36fb").first();
                if (countEl != null) {
                    genrePOJO.setDescription(countEl.text());
                }
                if (!genrePOJO.isNull()) {
                    result.add(genrePOJO);
                }
            }
        }

        return result;
    }

    private ArrayList<GenrePOJO> loadBooksListPoleknig(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        Document doc = connection.get();

        Elements genreItems = doc.select("div.genre > a.heading");
        if (genreItems != null && genreItems.size() > 0) {
            for (Element item : genreItems) {
                GenrePOJO genrePOJO = new GenrePOJO();
                String href = item.attr("href");
                if (href != null && !href.isEmpty()) {
                    genrePOJO.setUrl(href + "?p=");
                }
                String name = item.text();
                if (name != null && !name.isEmpty()) {
                    genrePOJO.setName(name);
                }
                if (!genrePOJO.isNull()) {
                    result.add(genrePOJO);
                }
            }
        }

        return result;
    }
}
