package com.fanok.audiobooks.model;




import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import androidx.annotation.NonNull;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ComentsModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.ComentsModel {


    @Override
    public Observable<ArrayList<ComentsPOJO>> getComents(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            //waitVpnConetion();
            ArrayList<ComentsPOJO> articlesModels;
            try {
                if (url.contains(Url.SERVER)) {
                    articlesModels = loadComentsList(url);
                } else if (url.contains(Url.SERVER_ABMP3)) {
                    articlesModels = loadComentsListABMP3(url);
                } else if (url.contains(Url.SERVER_AKNIGA)) {
                    articlesModels = loadComentsListAbook(url);
                } else if (url.contains(Url.SERVER_BAZA_KNIG)) {
                    articlesModels = loadComentsListBazaKnig(url);
                } else if (url.contains(Url.SERVER_AUDIOBOO)) {
                    articlesModels = loadComentsListAudioboo(url);
                }else {
                    articlesModels = new ArrayList<>();
                }
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    private ArrayList<SubComentsPOJO> getChildCOments(Element child, String parent) {
        ArrayList<SubComentsPOJO> result = new ArrayList<>();
        SubComentsPOJO subComentsPOJO = new SubComentsPOJO();
        Elements imageConteiner = child.getElementsByTag("img");
        if (imageConteiner.size() != 0) {
            subComentsPOJO.setImage(imageConteiner.first().attr("src"));
        }
        subComentsPOJO.setParentName(parent);

        Elements nameConteiner = child.getElementsByClass("comment_head_user");
        if (nameConteiner.size() != 0) {
            subComentsPOJO.setName(nameConteiner.first().text());
        }

        Elements reting = child.getElementsByClass("comment_head_votes_count");
        if (reting.size() != 0) {
            subComentsPOJO.setReting(reting.first().text());
        }

        Elements time = child.getElementsByClass("comment_head_time");
        if (time.size() != 0) {
            subComentsPOJO.setDate(time.first().text());
        }

        Elements text = child.getElementsByClass("comment_body");
        if (text.size() != 0) {
            subComentsPOJO.setText(text.first().text());
        }

        if (!subComentsPOJO.isEmty()) {
            result.add(subComentsPOJO);
        }

        Elements elements = child.children();

        for (int i = 1; i < elements.size(); i++) {
            if (elements.get(i).attr("class").contains("comments_list")) {
                result.addAll(getChildCOments(elements.get(i), subComentsPOJO.getName()));
            }
        }

        return result;
    }

    private ArrayList<ComentsPOJO> loadComentsList(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Element comentsElement = doc.getElementById("comments_list");

        if (comentsElement == null) return result;

        for (Element comentConteiner : comentsElement.children()) {
            ComentsPOJO comentsPOJO = new ComentsPOJO();
            Elements imageConteiner = comentConteiner.getElementsByTag("img");
            if (imageConteiner.size() != 0) {
                comentsPOJO.setImage(imageConteiner.first().attr("src"));
            }
            Elements nameConteiner = comentConteiner.getElementsByClass("comment_head_user");
            if (nameConteiner.size() != 0) {
                comentsPOJO.setName(nameConteiner.first().text());
            }

            Elements time = comentConteiner.getElementsByClass("comment_head_time");
            if (time.size() != 0) {
                comentsPOJO.setDate(time.first().text());
            }

            Elements reting = comentConteiner.getElementsByClass("comment_head_votes_count");
            if (reting.size() != 0) {
                comentsPOJO.setReting(reting.first().text());
            }

            Elements text = comentConteiner.getElementsByClass("comment_body");
            if (text.size() != 0) {
                comentsPOJO.setText(text.first().text());
            }


            Elements children = comentConteiner.children();

            for (int i = 1; i < children.size(); i++) {
                if (children.get(i).attr("class").contains("comments_list")) {
                    comentsPOJO.setChildComents(
                            getChildCOments(children.get(i), comentsPOJO.getName()));
                }
            }
            if (!comentsPOJO.isEmty()) {
                result.add(comentsPOJO);
            }

        }
        return result;
    }

    private SubComentsPOJO getChildCOmentsABMP3(Element comentConteiner, String parent) {

        SubComentsPOJO comentsPOJO = new SubComentsPOJO();
        Elements imageConteiner = comentConteiner.getElementsByTag("img");
        if (imageConteiner != null && imageConteiner.size() != 0) {
            comentsPOJO.setImage(imageConteiner.first().attr("src"));
        }
        Elements nameConteiner = comentConteiner.getElementsByClass("comment-author-name");
        if (nameConteiner != null && nameConteiner.size() != 0) {
            Element child = nameConteiner.first().child(0);
            if (child != null) {
                comentsPOJO.setName(child.text());
            }

            Element time = nameConteiner.first().child(1);
            if (time != null) {
                comentsPOJO.setDate(time.text());
            }
        }

        Elements text = comentConteiner.getElementsByClass("comment-body");
        if (text.size() != 0) {
            comentsPOJO.setText(text.first().text());
        }

        comentsPOJO.setParentName(parent);

        return comentsPOJO;


    }

    private ArrayList<ComentsPOJO> loadComentsListABMP3(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
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

        Element comentsElement = doc.getElementById("w0");
        if (comentsElement == null) {
            return result;
        }

        Elements empty = comentsElement.getElementsByClass("empty");
        if (empty != null && empty.size() != 0) {
            return result;
        }

        for (Element comentConteiner : comentsElement.children()) {
            ComentsPOJO comentsPOJO = new ComentsPOJO();
            Elements imageConteiner = comentConteiner.getElementsByTag("img");
            if (imageConteiner != null && imageConteiner.size() != 0) {
                comentsPOJO.setImage(imageConteiner.first().attr("src"));
            }
            Elements nameConteiner = comentConteiner.getElementsByClass("comment-author-name");
            if (nameConteiner != null && nameConteiner.size() != 0) {
                Element child = nameConteiner.first().child(0);
                if (child != null) {
                    comentsPOJO.setName(child.text());
                }

                Element time = nameConteiner.first().child(1);
                if (time != null) {
                    comentsPOJO.setDate(time.text());
                }
            }

            Elements text = comentConteiner.getElementsByClass("comment-body");
            if (text.size() != 0) {
                comentsPOJO.setText(text.first().text());
            }

            Elements childrenConteiner = comentConteiner.getElementsByClass("children");
            if (childrenConteiner != null && childrenConteiner.size() != 0) {
                Elements children = childrenConteiner.first().getElementsByClass("item");
                if (children != null) {
                    ArrayList<SubComentsPOJO> subComentsPOJOS = new ArrayList<>();
                    for (int i = 0; i < children.size(); i++) {
                        SubComentsPOJO subComentsPOJO = getChildCOmentsABMP3(children.get(i), comentsPOJO.getName());
                        if (!subComentsPOJO.isEmty()) {
                            subComentsPOJOS.add(subComentsPOJO);
                        }
                    }
                    if (subComentsPOJOS.size() != 0) {
                        comentsPOJO.setChildComents(subComentsPOJOS);
                    }
                }
            }

            if (!comentsPOJO.isEmty()) {
                result.add(comentsPOJO);
            }

        }
        return result;
    }

    private ArrayList<ComentsPOJO> loadComentsListAbook(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Element comentsElement = doc.getElementById("comments");
        if (comentsElement == null) return result;

        Elements titles = comentsElement.getElementsByClass("caption--inline");
        if (titles.isEmpty()) return result;
        Element titleEl = titles.first();
        if (!titleEl.hasClass("js-comments-title")) return result;
        String titleText = titleEl.text();
        if (titleText == null || titleText.contains("Нет комментариев")) return result;

        Elements wrappers = comentsElement.getElementsByClass("js-comment-wrapper");
        if (wrappers.isEmpty()) return result;

        HashMap<String, ComentsPOJO> parentMap = new HashMap<>();
        HashSet<String> existingIds = new HashSet<>();

        for (Element wrapper : wrappers) {
            String id = wrapper.attr("data-id");
            boolean isReply = wrapper.hasClass("has-parent");

            ComentsPOJO pojo = new ComentsPOJO();

            Elements imgs = wrapper.getElementsByTag("img");
            boolean hasAvatar = false;
            for (Element img : imgs) {
                if (img.hasClass("comments__block--item--avatar")) {
                    String src = img.attr("src");
                    if (src.isEmpty()) src = img.attr("data-src");
                    pojo.setImage(src);
                    hasAvatar = true;
                    break;
                }
            }
            if (!hasAvatar) {
                pojo.setImage("");
            }

            Elements nameEls = wrapper.getElementsByClass("comments__block--item--name");
            if (!nameEls.isEmpty()) {
                pojo.setName(nameEls.first().ownText());
            }

            Elements timeEls = wrapper.getElementsByTag("time");
            if (!timeEls.isEmpty()) {
                pojo.setDate(timeEls.first().text());
            }

            Elements commentTexts = wrapper.getElementsByClass("comments__block--item--comment");
            if (!commentTexts.isEmpty()) {
                pojo.setText(commentTexts.first().text());
            }

            String ratingStr = wrapper.attr("data-rating");
            if (ratingStr != null && !ratingStr.isEmpty() && !ratingStr.equals("0")) {
                pojo.setReting(ratingStr);
            }

            if (isReply) {
                String parentId = wrapper.getElementsByAttribute("data-parent-id").attr("data-parent-id");
                if (parentId.isEmpty()) {
                    Elements sections = wrapper.getElementsByTag("section");
                    if (!sections.isEmpty()) {
                        parentId = sections.first().attr("data-parent-id");
                    }
                }
                ComentsPOJO parent = parentMap.get(parentId);
                if (parent != null) {
                    SubComentsPOJO sub = new SubComentsPOJO();
                    sub.setImage(pojo.getImage());
                    sub.setName(pojo.getName());
                    sub.setDate(pojo.getDate());
                    sub.setText(pojo.getText());
                    sub.setReting(pojo.getReting());
                    Elements replytos = wrapper.getElementsByClass("replyto");
                    if (!replytos.isEmpty()) {
                        sub.setParentName(replytos.first().text().trim());
                    }
                    if (!sub.getText().isEmpty()) {
                        parent.setChildComents(sub);
                    }
                }
            } else {
                if (!id.isEmpty()) {
                    parentMap.put(id, pojo);
                    existingIds.add(id);
                }
                if (!pojo.getText().isEmpty()) result.add(pojo);
            }
        }

        Element loadMore = doc.select(".js-comments-load-more").first();
        if (loadMore != null) {
            try {
                int total = Integer.parseInt(loadMore.attr("data-total"));
                int loaded = Integer.parseInt(loadMore.attr("data-loaded"));
                if (total > loaded) {
                    String targetId = comentsElement.attr("data-target-id");
                    Document rssDoc = Jsoup.connect(
                                    "https://akniga.org/rss/comments/" + targetId + "/")
                            .userAgent(Consts.USER_AGENT)
                            .maxBodySize(0)
                            .ignoreContentType(true)
                            .get();
                    Elements rssItems = rssDoc.select("item");
                    for (Element item : rssItems) {
                        String guid = item.select("guid").text();
                        String commentId = guid.replaceAll(".*#comment", "");
                        if (commentId.isEmpty() || existingIds.contains(commentId)) continue;
                        ComentsPOJO pojo = new ComentsPOJO();
                        Elements creators = item.getElementsByTag("dc:creator");
                        if (!creators.isEmpty()) pojo.setName(creators.first().text());
                        else pojo.setName(item.select("author").text());
                        String desc = item.select("description").text();
                        pojo.setText(desc.replaceAll("<.*?>", ""));
                        String pubDate = item.select("pubDate").text();
                        if (!pubDate.isEmpty()) {
                            pojo.setDate(pubDate.replaceAll(" \\+\\d{4}", ""));
                        }
                        if (!pojo.getText().isEmpty()) result.add(pojo);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private ArrayList<ComentsPOJO> loadComentsListBazaKnig(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        int page = 0;
        url = url.substring(url.lastIndexOf("/") + 1);
        url = url.substring(0, url.indexOf("-"));
        String id = url;
        String baseUrl
                = Url.SERVER_BAZA_KNIG
                + "/engine/ajax/controller.php?mod=comments&cstart=<page>&news_id=<bookId>&skin=knigi-pk&massact=disable";
        while (true) {
            page++;
            Connection connection = Jsoup
                    .connect(baseUrl.replace("<page>", String.valueOf(page)).replace("<bookId>", id))
                    .userAgent(Consts.USER_AGENT)
                    .referrer("http://www.google.com")
                    .sslSocketFactory(Consts.socketFactory())
                    .maxBodySize(0)
                    .ignoreContentType(true);

            if(App.useProxy) {
                Proxy proxy = new Proxy(Type.SOCKS,
                        new InetSocketAddress(PROXY_HOST, PROXY_PORT));
                connection.proxy(proxy);
            }

            if (!Consts.getBazaKnigCookies().isEmpty()) {
                connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
            }

            String text = connection.execute().body();
            JsonElement json = JsonParser.parseString(text.replaceAll("\\n", "").replaceAll("\\t", ""));
            if (json.isJsonObject()) {
                String comentsList = json.getAsJsonObject().get("comments").getAsString();
                if (comentsList.isEmpty()) {
                    break;
                } else {
                    Document doc = Jsoup.parse(comentsList);
                    Elements elements = doc.body().children();
                    for (Element element : elements) {
                        ComentsPOJO comentsPOJO = new ComentsPOJO();
                        Elements imgConteiner = element.getElementsByClass("comm-ava");
                        if (imgConteiner != null && imgConteiner.size() > 0) {
                            Elements img = imgConteiner.first().getElementsByTag("img");
                            if (img != null && img.size() > 0) {
                                String src = img.first().attr("src");
                                if (src != null && !src.isEmpty()) {
                                    comentsPOJO.setImage(src);
                                }
                            }

                            Elements commInfo = element.getElementsByClass("comm-info");
                            if (commInfo != null && commInfo.size() > 0) {
                                Elements b = commInfo.first().getElementsByTag("b");
                                if (b != null && b.size() > 0) {
                                    String name = b.first().ownText();
                                    if (name != null && !name.isEmpty()) {
                                        comentsPOJO.setName(name);
                                    }
                                }
                                String date = commInfo.first().ownText();
                                if (date != null && !date.isEmpty()) {
                                    comentsPOJO.setDate(date);
                                }
                            }
                        }
                        Elements comText = element.getElementsByClass("comm-text");
                        if (comText != null && comText.size() != 0) {
                            Element parent = comText.first().child(0);
                            if (parent != null) {
                                String textComent = parent.ownText();
                                if (textComent != null && !textComent.isEmpty()) {
                                    comentsPOJO.setText(textComent);
                                }
                                Elements quoteTitle = parent.getElementsByClass("title_quote");
                                if (quoteTitle != null && quoteTitle.size() > 0) {
                                    String name = quoteTitle.first().text();
                                    if (name != null && !name.isEmpty()) {
                                        comentsPOJO.setQuoteName(name.replace("Цитата: ", ""));
                                    }
                                }
                                Elements quote = parent.getElementsByClass("quote");
                                if (quote != null && quote.size() > 0) {
                                    String quoteText = quote.first().text();
                                    if (quoteText != null && !quoteText.isEmpty()) {
                                        comentsPOJO.setQuoteText(quoteText);
                                    }
                                }
                            }
                        }

                        if (!comentsPOJO.isEmty()) {
                            result.add(comentsPOJO);
                        }
                    }
                }
            } else {
                break;
            }
        }
        Collections.reverse(result);
        return result;

    }


    private ArrayList<ComentsPOJO> loadComentsListAudioboo(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Element comentsElement = doc.getElementById("dle-comments-list");

        if (comentsElement == null) return result;

        for (Element comentConteiner : comentsElement.children()) {
            ComentsPOJO comentsPOJO = new ComentsPOJO();
            Element conteiner = comentConteiner.getElementsByClass("comm-one nowrap").first();
            if (conteiner!=null) {
                Element name = conteiner.child(0);
                comentsPOJO.setName(name.text());
                Element time = conteiner.child(1);
                comentsPOJO.setDate(time.text());
            }

            Element text = comentConteiner.getElementsByClass("comm-two clearfix full-text").first();
            if (text!=null) {
                comentsPOJO.setText(text.text());
            }

            comentsPOJO.setImage("https://placehold.net/default.png");

            if (!comentsPOJO.isEmty()) {
                result.add(comentsPOJO);
            }

        }
        return result;
    }
}
