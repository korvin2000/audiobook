package com.fanok.audiobooks.net;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.CookesExeption;
import com.fanok.audiobooks.Url;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one way to fetch a provider page: shared defaults (user agent, referrer, timeout, bounded body),
 * a per-host cookie jar, platform TLS validation, and classification of challenge/error pages.
 * Challenge pages (Cloudflare, DDoS-Guard) raise {@link CookesExeption}; other HTTP errors raise
 * {@link HttpStatusException}; parsers therefore never see an error page as content.
 */
public final class SiteHttp {

    public static final int TIMEOUT_MS = 10_000;
    public static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    private static final String REFERRER = "https://www.google.com/";
    private static final Map<String, Map<String, String>> JAR = new ConcurrentHashMap<>();

    private SiteHttp() {
    }

    /** A pre-configured connection for callers that need extra cookies, headers or the raw response. */
    public static Connection connect(String url) {
        Connection connection = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer(REFERRER)
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_BODY_BYTES)
                .ignoreHttpErrors(true);
        Map<String, String> cookies = JAR.get(Url.hostOf(url));
        if (cookies != null) {
            connection.cookies(cookies);
        }
        if (Url.isHost(url, Url.SERVER_BAZA_KNIG) && (cookies == null || !cookies.containsKey("PHPSESSID"))) {
            // The session the user pasted in settings; a newer one issued by the site (in the jar) wins.
            String session = Consts.getBazaKnigCookies();
            if (session != null && !session.isEmpty()) {
                connection.cookie("PHPSESSID", session);
            }
        }
        return connection;
    }

    /** GET {@code url} and parse it; the document's base URI is the final URL, so {@code absUrl} works. */
    public static Document get(String url) throws IOException {
        return parse(execute(connect(url)));
    }

    /**
     * Executes a connection built by {@link #connect} and remembers its cookies for the requested host. Jsoup
     * merges cookies across redirects, so after a cross-host redirect nothing is recorded (they cannot be attributed).
     */
    public static Connection.Response execute(Connection connection) throws IOException {
        String requestedHost = Url.hostOf(connection.request().url().toString());
        Connection.Response response = connection.execute();
        Map<String, String> cookies = response.cookies();
        if (!cookies.isEmpty() && requestedHost.equals(Url.hostOf(response.url().toString()))) {
            JAR.computeIfAbsent(requestedHost, k -> new ConcurrentHashMap<>()).putAll(cookies);
        }
        return response;
    }

    /** Parses a response or throws: a challenge page → {@link CookesExeption}, HTTP ≥ 400 → {@link HttpStatusException}. */
    public static Document parse(Connection.Response response) throws IOException {
        String url = response.url().toString();
        if (response.bodyAsBytes().length >= MAX_BODY_BYTES) {
            // Jsoup silently truncates at maxBodySize; a cut page would parse as "no more items", so refuse it.
            throw new IOException("Response larger than " + MAX_BODY_BYTES + " bytes: " + url);
        }
        Document document = response.parse();
        if (isChallenge(response.header("cf-mitigated"), document.title())) {
            throw new CookesExeption(url);
        }
        if (response.statusCode() >= 400) {
            throw new HttpStatusException("HTTP error fetching URL", response.statusCode(), url);
        }
        return document;
    }

    /** Pure: Cloudflare marks every challenge with {@code cf-mitigated}; older walls are known by title. */
    static boolean isChallenge(String cfMitigated, String title) {
        if (cfMitigated != null) {
            return true;
        }
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return t.contains("just a moment") || t.contains("attention required")
                || t.contains("ddos-guard") || t.contains("checking your browser")
                || t.contains("security verification");
    }
}
