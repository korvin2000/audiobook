package com.fanok.audiobooks.model;




import static com.fanok.audiobooks.App.useProxy;
import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.App;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.EncodingExeption;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.AudioPOJO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.ArrayList;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

public class AudioModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.AudioModelInterfece {

    Context mContext;

    public AudioModel(@NotNull final Context context) {
        mContext = context;
    }

    private static final String TAG = "AudioModel";

    public static String runScript(Context context, String fileName, String key, String functionName) {
        String resultStr = null;
        // Get the JavaScript in previous section
        try {

            InputStream inputStream = context.getResources().openRawResource(
                    context.getResources().getIdentifier(fileName,
                            "raw", context.getPackageName()));
            String data = convertStreamToString(inputStream);

            Object[] functionParams = new Object[]{key};

            org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();
            rhino.setOptimizationLevel(-1);
            Scriptable scope = rhino.initStandardObjects();
            
            // Provide a mock window object for obfuscators
            rhino.evaluateString(scope, "var window = this; var document = {}; navigator = {userAgent: 'Mozilla/5.0'};", "MockEnv", 1, null);
            
            rhino.evaluateString(scope, data, "JavaScript", 1, null);
            Object obj = scope.get(functionName, scope);

            if (obj instanceof Function) {
                Function function = (Function) obj;
                Object result = function.call(rhino, scope, scope, functionParams);
                resultStr = org.mozilla.javascript.Context.toString(result);
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        } finally {
            // We must exit the Rhino VM
            org.mozilla.javascript.Context.exit();
        }

        return resultStr;
    }

    @Override
    public Observable<ArrayList<AudioPOJO>> getAudio(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            //waitVpnConetion();
            ArrayList<AudioPOJO> articlesModels;
            try {
                if (url.contains(Url.SERVER)) {
                    articlesModels = loadSeriesList(url);
                } else if (url.contains(Url.SERVER_IZIBUK)) {
                    articlesModels = loadSeriesListIzibuk(url);
                } else if (url.contains(Url.SERVER_ABMP3)) {
                    articlesModels = loadSeriesListADMP3(url);
                } else if (url.contains(Url.SERVER_AKNIGA)) {
                    articlesModels = loadSeriesListAbook(url);
                } else if (url.contains(Url.SERVER_BAZA_KNIG)) {
                    articlesModels = loadSeriesListBazaKnig(url);
                } else if (url.contains(Url.SERVER_KNIGOBLUD)) {
                    articlesModels = loadSeriesKnigoblud(url);
                } else if (url.contains(Url.SERVER_AUDIOBOO)) {
                    articlesModels = loadSeriesAudioboo(url);
                } else if (url.contains(Url.SERVER_OTRUB)) {
                    articlesModels = loadSeriesListOtrub(url);
                } else if (url.contains(Url.SERVER_POLEKNIG)) {
                    articlesModels = loadSeriesListPoleknig(url);
                } else {
                    articlesModels = new ArrayList<>();
                }
            } catch (Throwable e) {
                e.printStackTrace();
                articlesModels = null;
                observableEmitter.onError(e != null ? e : new java.io.IOException("Load audio failed"));
                return;
            }

            if (articlesModels == null || articlesModels.isEmpty()) {
                observableEmitter.onError(new java.io.IOException("No audio tracks found"));
                return;
            }

            observableEmitter.onNext(articlesModels);
            observableEmitter.onComplete();
        });
    }

    private String deleteComnets(@NonNull String str) {
        int firstIndex = str.indexOf("/*");
        if (firstIndex != -1) {
            int lastIndex = str.indexOf("*/");
            if (lastIndex == -1) {
                lastIndex = str.length() - 1;
            }
            String subString = str.substring(firstIndex, lastIndex + 2);
            str = str.replace(subString, "");
            return deleteComnets(str);
        }
        return str;
    }

    private ArrayList<AudioPOJO> loadSeriesList(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .maxBodySize(0)
                .sslSocketFactory(Consts.socketFactory());

        Document doc = connection.get();

        Elements titleElement = doc.getElementsByClass("book_title_elem book_title_name");
        String bookName = "";
        if (titleElement.size() != 0) {
            bookName = titleElement.first().text().trim();
        }
        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("domReady")) {
                value = deleteComnets(value);
                value = value.substring(value.indexOf("var player = new BookPlayer"));
                value = value.substring(0, value.indexOf("\n"));
                String json = value.substring(value.indexOf("["), value.indexOf("], ") + 1);
                JsonElement jsonTree = JsonParser.parseString(json);
                if (jsonTree.isJsonArray()) {
                    JsonArray jsonArray = jsonTree.getAsJsonArray();
                    for (JsonElement element : jsonArray) {
                        if (element.isJsonObject()) {
                            JsonObject jsonObject = element.getAsJsonObject();
                            AudioPOJO audioPOJO = new AudioPOJO();
                            audioPOJO.setName(jsonObject.get("title").getAsString());
                            audioPOJO.setUrl(jsonObject.get("url").getAsString());
                            audioPOJO.setTime(jsonObject.get("duration").getAsInt());
                            audioPOJO.setBookName(bookName);
                            result.add(audioPOJO);
                        }
                    }
                }

            }
        }

        return result;
    }

    @UnstableApi
    private ArrayList<AudioPOJO> loadSeriesListOtrub(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        Document doc = connection.get();

        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String data = script.html();
            if (data.contains("window.XSPlayer(")) {
                try {
                    String json = data.substring(data.indexOf("window.XSPlayer(") + 16);
                    json = json.substring(0, json.lastIndexOf(");"));
                    org.json.JSONObject jsonObject = new org.json.JSONObject(json);
                    org.json.JSONArray playlist = jsonObject.optJSONArray("playlist");
                    if (playlist != null) {
                        for (int i = 0; i < playlist.length(); i++) {
                            org.json.JSONObject track = playlist.getJSONObject(i);
                            AudioPOJO audioPOJO = new AudioPOJO();
                            
                            audioPOJO.setName(track.optString("title", ""));
                            audioPOJO.setUrl(track.optString("src", ""));
                            
                            int duration = track.optInt("duration", 0);
                            if (duration > 0) {
                                audioPOJO.setTime(duration);
                            }
                            
                            result.add(audioPOJO);
                        }
                    }
                    break;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else if (data.contains("Playerjs") && data.contains("file")) {
                int fileStart = data.indexOf("file:");
                if (fileStart < 0) fileStart = data.indexOf("\"file\":");
                if (fileStart < 0) fileStart = data.indexOf("\"file\"");
                int fileEnd = data.lastIndexOf(']');
                int fileStartBracket = data.indexOf('[', fileStart);
                if (fileStartBracket < 0 || fileEnd < 0 || fileEnd <= fileStartBracket) continue;

                String json = data.substring(fileStartBracket, fileEnd + 1);
                try {
                    org.json.JSONArray jsonArray = new org.json.JSONArray(json);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        org.json.JSONObject track = jsonArray.getJSONObject(i);
                        AudioPOJO audioPOJO = new AudioPOJO();

                        String title = track.optString("title", "");
                        audioPOJO.setName(title);

                        String fileUrl = track.optString("file", "");
                        audioPOJO.setUrl(fileUrl);

                        result.add(audioPOJO);
                    }
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
                break;
            }
        }

        if (result.isEmpty()) {
            throw new NullPointerException();
        }
        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesAudioboo(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        if(useProxy){
            Proxy proxy = new Proxy(Type.SOCKS, new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }
        Document doc = connection.get();

        String bookName = "";
        Element titleElement = doc.selectFirst("h1");
        if (titleElement != null) {
            bookName = titleElement.text().trim();
        }

        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String data = script.html();
            if (data.contains("Playerjs") && data.contains("file")) {
                int fileStart = data.indexOf("file:");
                if (fileStart < 0) fileStart = data.indexOf("\"file\":");
                if (fileStart < 0) fileStart = data.indexOf("\"file\"");
                int start = data.indexOf('[', fileStart);
                int end = data.lastIndexOf(']');
                if (start < 0 || end < 0 || end <= start) continue;

                String json = data.substring(start, end + 1);
                try {
                    org.json.JSONArray jsonArray = new org.json.JSONArray(json);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        org.json.JSONObject track = jsonArray.getJSONObject(i);
                        AudioPOJO audioPOJO = new AudioPOJO();

                        String title = track.optString("title", "");
                        audioPOJO.setName(title);

                        String fileUrl = track.optString("file", "");
                        
                        // Decode base64 if needed (Playerjs often base64 encodes with a prefix)
                        if (fileUrl.startsWith("//_//")) {
                            fileUrl = fileUrl.replace("//_//", "");
                            try {
                                byte[] decodedBytes = android.util.Base64.decode(fileUrl, android.util.Base64.DEFAULT);
                                fileUrl = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        } else if (fileUrl.contains("/engine/go.php?url=")) {
                            String base64 = fileUrl.substring(fileUrl.indexOf("/engine/go.php?url=") + 19);
                            try {
                                byte[] decodedBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                                fileUrl = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        } else if (fileUrl.startsWith("/")) {
                            fileUrl = Url.SERVER_AUDIOBOO + fileUrl;
                        }
                        
                        audioPOJO.setUrl(fileUrl);
                        audioPOJO.setBookName(bookName);
                        result.add(audioPOJO);
                    }
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
                break;
            }
        }

        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesListADMP3(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        String autor = "";
        String bookName = "";

        Connection connection = Jsoup.connect(url)
                .method(Connection.Method.GET)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER_ABMP3 + "/")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0);

        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Connection.Response res = connection.execute();

        Document doc = res.parse();

        Elements infos = doc.getElementsByClass("panel-item");
        if (infos != null) {
            for (Element info : infos) {
                if (info.text().contains("Автор")) {
                    Elements element = info.getElementsByTag("a");
                    if (element != null && element.size() != 0) {
                        autor = element.first().text();
                    }
                }
            }
        }

        Elements titleElement = doc.getElementsByTag("h1");
        if (titleElement.size() != 0) {
            String name = titleElement.first().text().trim();
            if (autor.isEmpty()) {
                bookName = name;
            } else {
                bookName = name.replace(autor + " - ", "");
            }
        }

        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("var player = new Playerjs")) {
                value = value.substring(value.indexOf("file:"));
                String urlJson = value.substring(value.indexOf("\"") + 1, value.lastIndexOf("\""));



                Connection connection1 = Jsoup.connect(urlJson)
                        .userAgent(Consts.USER_AGENT)
                        .referrer(Url.SERVER_ABMP3 + "/")
                        .sslSocketFactory(Consts.socketFactory())
                        //.cookies(res.cookies())
                        .maxBodySize(0);

                if(App.useProxy) {
                    Proxy proxy = new Proxy(Type.SOCKS,
                            new InetSocketAddress(PROXY_HOST, PROXY_PORT));
                    connection.proxy(proxy);
                }

                Document document = connection1.get();

                String json = document.body().text();

                JsonElement jsonTree = JsonParser.parseString(json);
                if (jsonTree.isJsonArray()) {
                    JsonArray jsonArray = jsonTree.getAsJsonArray();
                    for (JsonElement element : jsonArray) {
                        if (element.isJsonObject()) {
                            JsonObject jsonObject = element.getAsJsonObject();
                            AudioPOJO audioPOJO = new AudioPOJO();
                            audioPOJO.setName(jsonObject.get("title").getAsString());
                            audioPOJO.setUrl(jsonObject.get("file").getAsString());
                            audioPOJO.setBookName(bookName);
                            result.add(audioPOJO);
                        }
                    }
                }

                break;

            }
        }

        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesListAbook(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();

        Response response = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer(Url.SERVER_AKNIGA + "/performers/")
                .header("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
                .sslSocketFactory(Consts.socketFactory())
                .ignoreHttpErrors(true)
                .maxBodySize(0)
                .execute();

        Document doc = response.parse();
        String html = doc.html();

        String bookName = "";
        Elements elements = doc.getElementsByClass("caption__article-main");
        if (elements != null && elements.size() != 0) {
            bookName = elements.first().text();
        }

        String securityKey = "";
        java.util.regex.Matcher mKey = java.util.regex.Pattern.compile("LIVESTREET_SECURITY_KEY\\s*=\\s*'([^']+)'").matcher(html);
        if (mKey.find()) {
            securityKey = mKey.group(1);
        }

        String bid = "";
        java.util.regex.Matcher mBid = java.util.regex.Pattern.compile("data-bid=\"(\\d+)\"").matcher(html);
        if (mBid.find()) {
            bid = mBid.group(1);
        }

        if (!securityKey.isEmpty() && !bid.isEmpty()) {
            try {
                // Step 1: get player token
                String ts = String.valueOf(System.currentTimeMillis());
                Response tokenRes = Jsoup.connect("https://akniga.org/ajax/player/token")
                        .userAgent(Consts.USER_AGENT)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://akniga.org")
                        .header("Referer", url)
                        .header("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
                        .cookies(response.cookies())
                        .data("security_ls_key", securityKey)
                        .data("bid", bid)
                        .data("ts", ts)
                        .method(Connection.Method.POST)
                        .ignoreContentType(true)
                        .sslSocketFactory(Consts.socketFactory())
                        .execute();

                String playerToken = "";
                JsonElement tokenJson = JsonParser.parseString(tokenRes.body());
                if (tokenJson.isJsonObject() && tokenJson.getAsJsonObject().has("token")) {
                    playerToken = tokenJson.getAsJsonObject().get("token").getAsString();
                }

                // Step 2: get book audio data with token
                Response ajaxRes = Jsoup.connect("https://akniga.org/ajax/b/" + bid)
                        .userAgent(Consts.USER_AGENT)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://akniga.org")
                        .header("Referer", url)
                        .header("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
                        .cookies(response.cookies())
                        .cookies(tokenRes.cookies())
                        .data("security_ls_key", securityKey)
                        .data("bid", bid)
                        .data("token", playerToken)
                        .data("hls", "true")
                        .method(Connection.Method.POST)
                        .ignoreContentType(true)
                        .sslSocketFactory(Consts.socketFactory())
                        .execute();

                String json = ajaxRes.body();
                JsonElement jsonTree = JsonParser.parseString(json);
                if (jsonTree.isJsonObject()) {
                    JsonObject jsonObj = jsonTree.getAsJsonObject();

                    // Decrypt hres to get HLS playlist URL
                    String hlsUrl = "";
                    if (jsonObj.has("hres")) {
                        String hresStr = jsonObj.get("hres").getAsString();
                        hlsUrl = decryptAknigaHres(hresStr);
                    }

                    // New format: items array + HLS url from hres
                    if (!hlsUrl.isEmpty()) {
                        if (jsonObj.has("items")) {
                            try {
                                JsonArray items;
                                if (jsonObj.get("items").isJsonArray()) {
                                    items = jsonObj.getAsJsonArray("items");
                                } else {
                                    items = JsonParser.parseString(jsonObj.get("items").getAsString()).getAsJsonArray();
                                }
                                
                                int timeAccumulated = 0;
                                for (JsonElement item : items) {
                                    if (item.isJsonObject()) {
                                        JsonObject itemObj = item.getAsJsonObject();
                                        AudioPOJO audioPOJO = new AudioPOJO();
                                        audioPOJO.setBookName(bookName);
                                        try {
                                            audioPOJO.setUrl(hlsUrl);
                                        } catch (Exception ignored) {}
                                        if (itemObj.has("title")) {
                                            audioPOJO.setName(itemObj.get("title").getAsString());
                                        }
                                        if (itemObj.has("time_from_start")) {
                                            audioPOJO.setTimeStart(itemObj.get("time_from_start").getAsInt());
                                        } else {
                                            audioPOJO.setTimeStart(timeAccumulated);
                                        }
                                        if (itemObj.has("duration")) {
                                            int duration = itemObj.get("duration").getAsInt();
                                            audioPOJO.setTime(duration);
                                            if (itemObj.has("time_finish")) {
                                                audioPOJO.setTimeFinish(itemObj.get("time_finish").getAsInt());
                                            } else {
                                                timeAccumulated = audioPOJO.getTimeStart() + duration;
                                                audioPOJO.setTimeFinish(timeAccumulated);
                                            }
                                            timeAccumulated = audioPOJO.getTimeFinish();
                                        }
                                        result.add(audioPOJO);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if (result.isEmpty()) {
                            AudioPOJO audioPOJO = new AudioPOJO();
                            audioPOJO.setBookName(bookName);
                            try {
                                audioPOJO.setUrl(hlsUrl);
                            } catch (Exception ignored) {}
                            audioPOJO.setName("Аудиокнига (полная)");
                            audioPOJO.setTimeStart(0);
                            result.add(audioPOJO);
                        }
                    }

                    // Legacy fallback: aItems format
                    if (result.isEmpty() && jsonObj.has("aItems")) {
                        JsonArray items = jsonObj.getAsJsonArray("aItems");
                        int timeAccumulated = 0;
                        for (JsonElement item : items) {
                            if (item.isJsonObject()) {
                                JsonObject itemObj = item.getAsJsonObject();
                                AudioPOJO audioPOJO = new AudioPOJO();
                                audioPOJO.setBookName(bookName);
                                if (itemObj.has("file")) {
                                    audioPOJO.setUrl(itemObj.get("file").getAsString());
                                } else if (itemObj.has("file_url")) {
                                    audioPOJO.setUrl(itemObj.get("file_url").getAsString());
                                }
                                if (itemObj.has("title")) {
                                    audioPOJO.setName(itemObj.get("title").getAsString());
                                }
                                if (itemObj.has("duration")) {
                                    int duration = itemObj.get("duration").getAsInt();
                                    audioPOJO.setTime(duration);
                                    audioPOJO.setTimeStart(timeAccumulated);
                                    timeAccumulated += duration;
                                    audioPOJO.setTimeFinish(timeAccumulated);
                                }
                                result.add(audioPOJO);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                AudioPOJO errorPOJO = new AudioPOJO();
                errorPOJO.setName("Ошибка AJAX: " + e.toString());
                result.add(errorPOJO);
            }
                    } else {
            AudioPOJO err = new AudioPOJO();
            err.setName("Ошибка: не найден securityKey или bid на странице.");
            
            result.add(err);
                    }

                    if (result.isEmpty()) {
            String audioUrl = "";
            Elements audioTags = doc.getElementsByTag("audio");
            if (audioTags != null) {
                for (Element audio : audioTags) {
                    if (audio.hasAttr("src") && !audio.attr("src").isEmpty()) {
                        audioUrl = audio.attr("src");
                        break;
                    }
                }
            }

            if (!audioUrl.isEmpty()) {
                AudioPOJO audioPOJO = new AudioPOJO();
                audioPOJO.setUrl(audioUrl);
                audioPOJO.setBookName(bookName);
                audioPOJO.setName(bookName);
                result.add(audioPOJO);
            } else {
                AudioPOJO err = new AudioPOJO();
                err.setName("Список файлов пуст. HLS ссылка не найдена.");
                
                result.add(err);
            }
                    }

                    return result;
                }

    /**
     * Decrypts the Akniga "hres" field which contains the HLS playlist URL.
     * Uses AES-256-CBC with EVP_BytesToKey (MD5) key derivation - replicates CryptoJS behavior.
     * Passphrase "EKxtcg46V" reverse-engineered from Akniga's obfuscated JavaScript.
     */
    private String decryptAknigaHres(String hresJson) {
        try {
            org.json.JSONObject hres = new org.json.JSONObject(hresJson);
            String ctB64 = hres.getString("ct");
            String ivHex = hres.optString("iv", "");
            String saltHex = hres.optString("s", "");

            byte[] ct = android.util.Base64.decode(ctB64, android.util.Base64.DEFAULT);
            byte[] iv = ivHex.isEmpty() ? new byte[16] : hexToBytes(ivHex);
            byte[] salt = saltHex.isEmpty() ? new byte[8] : hexToBytes(saltHex);

            // EVP_BytesToKey: MD5-based KDF replicating CryptoJS string passphrase behavior
            byte[] passphrase = "EKxtcg46V".getBytes("UTF-8");
            byte[] keyIv = evpBytesToKey(passphrase, salt, 48);
            byte[] key = java.util.Arrays.copyOfRange(keyIv, 0, 32);
            byte[] derivedIv = java.util.Arrays.copyOfRange(keyIv, 32, 48);

            // Use explicit IV from hres if available, otherwise use derived IV
            byte[] useIv = iv.length == 16 && !ivHex.isEmpty() ? iv : derivedIv;

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.IvParameterSpec(useIv));
            byte[] decrypted = cipher.doFinal(ct);
            String result = new String(decrypted, "UTF-8").trim();
            result = result.replace("\\/", "/");
            // result is a JSON string like "\"https://r1.akniga.club/b/111286/pl.m3u8?...\""
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result = result.substring(1, result.length() - 1);
            }
            if (result.startsWith("[")) {
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(result);
                    if (arr.length() > 0) result = arr.getString(0);
                } catch(Exception e) {}
            } else if (result.startsWith("{")) {
                try {
                    org.json.JSONObject obj = new org.json.JSONObject(result);
                    if(obj.has("url")) result = obj.getString("url");
                } catch(Exception e) {}
            }
            if (result.startsWith("//")) result = "https:" + result; 
            else if (result.startsWith("/")) result = "https://akniga.org" + result;
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private byte[] evpBytesToKey(byte[] passphrase, byte[] salt, int needed) throws Exception {
        byte[] result = new byte[0];
        byte[] prev = new byte[0];
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        while (result.length < needed) {
            md.reset();
            md.update(prev);
            md.update(passphrase);
            md.update(salt);
            prev = md.digest();
            byte[] tmp = new byte[result.length + prev.length];
            System.arraycopy(result, 0, tmp, 0, result.length);
            System.arraycopy(prev, 0, tmp, result.length, prev.length);
            result = tmp;
        }
        return result;
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private ArrayList<AudioPOJO> getAudioSorceBazaKnig(Document doc, String id, String bookName) {
        ArrayList<AudioPOJO> result = new ArrayList<>();
        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains(id)) {
                int arrStart = value.indexOf("[");
                int arrEnd = value.indexOf("]");
                if (arrStart < 0 || arrEnd < 0 || arrEnd <= arrStart) continue;
                String key = value.substring(arrStart, arrEnd) + "]";
                if (value.contains("strDecode")) {
                    try {
                        String decoded = decodeBazaKnigStr(key);
                        JsonElement jsonTree = JsonParser.parseString(decoded);
                        if (jsonTree.isJsonArray()) {
                            JsonArray jsonArray = jsonTree.getAsJsonArray();
                            for (JsonElement element : jsonArray) {
                                if (element.isJsonObject()) {
                                    JsonObject jsonObject = element.getAsJsonObject();
                                    AudioPOJO audioPOJO = new AudioPOJO();
                                    audioPOJO.setName(jsonObject.get("title").getAsString());
                                    audioPOJO.setUrl(jsonObject.get("file").getAsString());
                                    audioPOJO.setBookName(bookName);
                                    result.add(audioPOJO);
                                }
                            }
                        }
                        return result;
                    } catch (Exception ignored) {
                    }
                }
                try {
                    JsonElement jsonTree = JsonParser.parseString(key);
                    if (jsonTree.isJsonArray()) {
                        JsonArray jsonArray = jsonTree.getAsJsonArray();
                        for (JsonElement element : jsonArray) {
                            if (element.isJsonObject()) {
                                JsonObject jsonObject = element.getAsJsonObject();
                                AudioPOJO audioPOJO = new AudioPOJO();
                                audioPOJO.setName(jsonObject.get("title").getAsString());
                                audioPOJO.setUrl(jsonObject.get("file").getAsString());
                                audioPOJO.setBookName(bookName);
                                result.add(audioPOJO);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private String decodeBazaKnigStr(String key) {
        String base64 = key.replaceAll("[^a-zA-Z0-9+/=]", "");
        String alphabet = "Z83UhncLHApBrM7GvdqT4tNWRjemgak9oVzwK1PXDfY5bQOSlsF26yi0JCIuxE+/=";
        byte[] bytes = new byte[base64.length() / 4 * 3];
        int pos = 0;
        for (int i = 0; i < base64.length(); i += 4) {
            int c1 = alphabet.indexOf(base64.charAt(i));
            int c2 = i + 1 < base64.length() ? alphabet.indexOf(base64.charAt(i + 1)) : 64;
            int c3 = i + 2 < base64.length() ? alphabet.indexOf(base64.charAt(i + 2)) : 64;
            int c4 = i + 3 < base64.length() ? alphabet.indexOf(base64.charAt(i + 3)) : 64;
            bytes[pos++] = (byte) ((c1 << 2) | (c2 >> 4));
            if (c3 < 64) bytes[pos++] = (byte) (((c2 & 0xf) << 4) | (c3 >> 2));
            if (c4 < 64) bytes[pos++] = (byte) (((c3 & 0x3) << 6) | c4);
        }
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < pos; i++) {
            int val = bytes[i] & 0xFF;
            char c = (char) val;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '@' || c == '*' ||
                c == '_' || c == '+' || c == '-' || c == '.' || c == '/') {
                escaped.append(c);
            } else {
                escaped.append(String.format("%%%02X", val));
            }
        }
        try {
            String pct = escaped.toString().replace("+", "%2B");
            java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream(pos);
            int ci = 0;
            while (ci < pct.length()) {
                if (pct.charAt(ci) == '%' && ci + 2 < pct.length()) {
                    bout.write(Integer.parseInt(pct.substring(ci + 1, ci + 3), 16));
                    ci += 3;
                } else {
                    bout.write((byte) pct.charAt(ci));
                    ci++;
                }
            }
            java.nio.charset.CharsetDecoder dec = java.nio.charset.Charset.forName("UTF-8").newDecoder();
            dec.onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE);
            dec.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE);
            String result = dec.decode(java.nio.ByteBuffer.wrap(bout.toByteArray())).toString();
            int jsonStart = result.indexOf('[');
            if (jsonStart >= 0) return result.substring(jsonStart);
            return result;
        } catch (Exception e) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < pos; i++) {
                result.append((char) (bytes[i] & 0xFF));
            }
            return result.toString();
        }
    }


    private ArrayList<AudioPOJO> loadSeriesListBazaKnig(String url) throws IOException {

        int indexSorce = url.indexOf("?sorce");
        int sorce = 1;
        if (indexSorce != -1) {
            String substring = url.substring(indexSorce).replace("?sorce=", "");
            sorce = Integer.parseInt(substring);
        }

        ArrayList<AudioPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .header("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        if (!Consts.getBazaKnigCookies().isEmpty()) {
            connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
        }
        if(App.useProxy) {
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }


        Document doc = connection.get();

        String bookName = "";
        Elements title = doc.getElementsByTag("h1");
        if (title != null && title.size() > 0) {
            String text = title.first().ownText();
            int idx = text.indexOf(" - ");
            if (idx > 0) {
                bookName = text.substring(0, idx).trim();
            } else {
                bookName = text.trim();
            }
        }

        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("strDecode(\"")) {
                value = deleteComnets(value);
                value = value.substring(value.indexOf("strDecode(\"") + 11);
                String key = value.substring(0, value.indexOf("\")"));
                try {
                    String hash = decodeBazaKnigStr(key);
                    JsonElement element = JsonParser.parseString(hash);
                    if (element.isJsonArray()) {
                        JsonArray array = element.getAsJsonArray();
                        for (JsonElement item : array) {
                            if (item.isJsonObject()) {
                                AudioPOJO audioPOJO = new AudioPOJO();
                                JsonObject obj = item.getAsJsonObject();
                                audioPOJO.setName(obj.get("title").getAsString());
                                audioPOJO.setUrl(obj.get("file").getAsString());
                                audioPOJO.setBookName(bookName);
                                result.add(audioPOJO);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        Element div = doc.getElementById("fr");
        if (div != null) {
            String data = div.attr("data");
            if (data != null) {
                try {
                    int srcIdx = data.indexOf("src=");
                    data = data.substring(srcIdx);
                    data = data.replace("src=\"", "");
                    int quoteEnd = data.indexOf("\"");
                    data = data.substring(0, quoteEnd);
                    data = data.replace("&amp;", "&");
                    if (data.contains("archive.org")) {
                        String metaUrl = data.replace("/embed/", "/metadata/");
                        int qIdx = metaUrl.indexOf("&");
                        if (qIdx > 0) metaUrl = metaUrl.substring(0, qIdx);
                        Connection metaConn = Jsoup.connect(metaUrl)
                                .userAgent(Consts.USER_AGENT)
                                .maxBodySize(0)
                                .ignoreContentType(true);
                        String metaJson = metaConn.execute().body();
                        JsonObject metaRoot = JsonParser.parseString(metaJson).getAsJsonObject();
                        JsonObject meta = metaRoot.getAsJsonObject("metadata");
                        String archiveId = meta.get("identifier").getAsString();
                        JsonArray files = metaRoot.getAsJsonArray("files");
                        if (files != null) {
                            for (int i = 0; i < files.size(); i++) {
                                JsonObject file = files.get(i).getAsJsonObject();
                                JsonElement titleEl = file.get("title");
                                JsonElement formatEl = file.get("format");
                                if (titleEl != null && formatEl != null &&
                                        formatEl.getAsString().contains("MP3")) {
                                    AudioPOJO audioPOJO = new AudioPOJO();
                                    audioPOJO.setName(titleEl.getAsString());
                                    audioPOJO.setBookName(bookName);
                                    String name = file.get("name").getAsString();
                                    audioPOJO.setUrl("https://archive.org/download/" + archiveId + "/" + name);
                                    result.add(audioPOJO);
                                }
                            }
                        }
                    } else {
                        Connection conPlaylist = Jsoup.connect(data)
                                .userAgent(Consts.USER_AGENT)
                                .referrer("http://www.google.com")
                                .maxBodySize(0)
                                .sslSocketFactory(Consts.socketFactory());
                        Document playlist = conPlaylist.get();
                        Elements player = playlist.getElementsByClass("js-play8-playlist");
                        if (player != null && player.size() > 0) {
                            String value = player.first().attr("value");
                            JsonElement jsonArray = JsonParser.parseString(value);
                            if (jsonArray.isJsonArray()) {
                                for (int i = 0; i < jsonArray.getAsJsonArray().size(); i++) {
                                    JsonElement element = jsonArray.getAsJsonArray().get(i);
                                    if (element.isJsonObject()) {
                                        JsonObject jsonObject = element.getAsJsonObject();
                                        AudioPOJO audioPOJO = new AudioPOJO();
                                        audioPOJO.setName(bookName + "_" + (i + 1));
                                        audioPOJO.setTime((int) jsonObject.get("duration").getAsDouble());
                                        audioPOJO.setBookName(bookName);
                                        JsonArray array = jsonObject.getAsJsonArray("sources");
                                        if (array != null) {
                                            JsonElement element1 = array.get(0);
                                            if (element1 != null && element1.isJsonObject()) {
                                                JsonObject object = element1.getAsJsonObject();
                                                audioPOJO.setUrl("https://archive.org" + object.get("file").getAsString());
                                            }
                                        }
                                        result.add(audioPOJO);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (result.isEmpty()) {
                if (sorce == 1) {
                    result = getAudioSorceBazaKnig(doc, "id:\"player\"", bookName);
                } else {
                    result = getAudioSorceBazaKnig(doc, "id:\"player" + sorce + "\"", bookName);
                }
            }
        }

        if (result.isEmpty()) {
            AudioPOJO err = new AudioPOJO();
            err.setName("Список файлов пуст. Не найдено ни одного источника.");
            
            result.add(err);
        }

        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesListIzibuk(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("https://izib.uk")
                .maxBodySize(0)
                .sslSocketFactory(Consts.socketFactory());

        if(useProxy){
            Proxy proxy = new Proxy(Type.SOCKS,
                    new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            connection.proxy(proxy);
        }

        Document doc = connection.get();

        Elements titleElement = doc.getElementsByAttributeValue("itemprop", "name");
        String bookName = "";
        if (titleElement.size() != 0) {
            bookName = titleElement.first().text().trim();
        }
        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("domReady")) {
                value = deleteComnets(value);
                value = value.substring(value.indexOf("var player = new XSPlayer("));
                // value = value.substring(0, value.indexOf("\n"));
                String json = value.substring(value.indexOf("(") + 1, value.indexOf(");"));
                JsonElement jsonTree = JsonParser.parseString(json);
                if (jsonTree.isJsonObject()) {
                    JsonObject jsonObject = jsonTree.getAsJsonObject();
                    JsonElement url_pref_element = jsonObject.get("mp3_url_prefix");
                    if (url_pref_element != null && url_pref_element.isJsonPrimitive()) {
                        String url_pref = "https://" + url_pref_element.getAsString();
                        
                        String sign = "";
                        if (jsonObject.has("sign")) {
                            sign = jsonObject.get("sign").getAsString();
                        }
                        
                        JsonArray array = jsonObject.getAsJsonArray("tracks");
                        if (array != null) {
                            for (int i = 0; i < array.size(); i++) {
                                AudioPOJO audioPOJO = new AudioPOJO();
                                JsonArray elements = array.get(i).getAsJsonArray();
                                audioPOJO.setName(elements.get(1).getAsString());
                                audioPOJO.setTime(elements.get(2).getAsInt());
                                audioPOJO.setUrl(url_pref + "/" + elements.get(4).getAsString() + sign);
                                audioPOJO.setBookName(bookName);
                                result.add(audioPOJO);
                            }
                        }
                    }
                }

            }
        }

        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesKnigoblud(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .maxBodySize(0)
                .ignoreHttpErrors(true)
                .sslSocketFactory(Consts.socketFactory());


        Document doc = connection.get();

        String bookName = "";
        Elements titleElement = doc.getElementsByTag("h1");
        if (titleElement!=null&&titleElement.size() != 0) {
            String name = titleElement.first().text().trim();
            bookName = name;
        }

        Elements sriptElements = doc.getElementsByTag("script");
        for (Element script : sriptElements) {
            String value = script.toString();
            if (value.contains("KB.playerInit")) {
                value = deleteComnets(value);
                value = value.substring(value.indexOf("KB.playerInit("));
                String json = value.substring(value.indexOf("(") + 1, value.indexOf(");"));
                JsonElement jsonTree = JsonParser.parseString(json);
                if (jsonTree.isJsonObject()) {
                    JsonObject jsonObject = jsonTree.getAsJsonObject();
                    JsonArray array = jsonObject.getAsJsonArray("playlist");
                    for (int i = 0; i < array.size(); i++) {
                        AudioPOJO audioPOJO = new AudioPOJO();
                        JsonObject object = array.get(i).getAsJsonObject();
                        if(object!=null){
                            JsonElement element = object.get("title");
                            if(element!=null){
                                audioPOJO.setName(element.getAsString());
                            }

                            element = object.get("duration");
                            if(element!=null){
                                audioPOJO.setTime(element.getAsInt());
                            }

                            element = object.get("src");
                            if(element!=null){
                                audioPOJO.setUrl(element.getAsString());
                            }

                        }
                        audioPOJO.setBookName(bookName);
                        result.add(audioPOJO);
                    }
                }

            }
        }

        return result;
    }

    private ArrayList<AudioPOJO> loadSeriesListPoleknig(String url) throws IOException {
        ArrayList<AudioPOJO> result = new ArrayList<>();

        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        Document doc = connection.get();

        String playlistUrl = null;
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String data = script.html();
                if (data.contains("Playerjs")) {
                int fileKey = data.indexOf("\"file\"");
                if (fileKey < 0) {
                    fileKey = data.indexOf("'file'");
                }
                if (fileKey < 0) {
                    fileKey = data.indexOf("file:");
                }
                if (fileKey >= 0) {
                    boolean unquoted = data.charAt(fileKey) == 'f';
                    int keyEnd = fileKey + (unquoted ? 5 : 6);
                    int start = data.indexOf('"', keyEnd) + 1;
                    if (start <= 0) {
                        start = data.indexOf('\'', keyEnd) + 1;
                    }
                    if (start > 0) {
                        int end = data.indexOf('"', start);
                        if (end < 0) {
                            end = data.indexOf('\'', start);
                        }
                        if (end > start) {
                            playlistUrl = data.substring(start, end);
                        }
                    }
                }
                break;
            }
        }

        if (playlistUrl == null || playlistUrl.isEmpty()) {
            throw new NullPointerException();
        }

        if (!playlistUrl.startsWith("http")) {
            playlistUrl = Url.SERVER_POLEKNIG + playlistUrl;
        }

        Connection playlistConn = Jsoup.connect(playlistUrl)
                .userAgent(Consts.USER_AGENT)
                .referrer(url)
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .ignoreHttpErrors(true);

        String jsonBody = playlistConn.execute().body();

        try {
            JSONArray jsonArray = new JSONArray(jsonBody);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject track = jsonArray.getJSONObject(i);
                AudioPOJO audioPOJO = new AudioPOJO();

                String title = track.optString("title", "");
                audioPOJO.setName(title);

                String fileUrl = track.optString("file", "");
                audioPOJO.setUrl(fileUrl);

                result.add(audioPOJO);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (result.isEmpty()) {
            throw new NullPointerException();
        }
        return result;
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
