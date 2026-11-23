import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UrlQueueServer {
    private static final int MAX_URLS = 10;
    private static final StringQueue URLS = new StringQueue(MAX_URLS);
    private static volatile List<HeapSort.ScoredUrl> TOP_RESULTS = Collections.emptyList();

    // Keeps every score at or below twenty points.
    private static final int MAX_POINTS = 20;

    // Each keyword counts once; its points are at the same position in KEYWORD_POINTS.
    private static final String[] KEYWORDS = { 
        "login", "signin", "verify", "secure", "update",
        "account", "password", "recover", "locked", "urgent",
        "alert", "expired", "overdue", "limited", "billing",
        "invoice", "payment", "refund", "delivery", "tracking",
        "support", "free", "bonus", "claim", "download",
        "install", "update", "upgrade", "activate", "register" 
    };
    private static final int[] KEYWORD_POINTS = {  
        2, 2, 4, 3, 2,
        2, 3, 4, 5, 5,
        4, 4, 5, 4, 3,
        3, 3, 4, 3, 3,
        2, 3, 4, 5, 2,
        3, 2, 2, 2, 1
    };

    // Each domain ending uses the points at the same position in TLD_POINTS.
    private static final String[] TLDS = { 
        ".com", ".org", ".net", ".edu", ".gov",
        ".io", ".co", ".ai", ".app", ".dev",
        ".info", ".biz", ".online", ".shop", ".xyz",
        ".top", ".icu", ".zip", ".click", ".bond"
    };
    private static final int[] TLD_POINTS = { 
        2, 1, 2, 1, 1,
        2, 2, 2, 1, 1,
        3, 3, 3, 2, 4,
        4, 5, 5, 4, 5
    };

    // Gives unlisted domain endings two points.
    private static final int OTHER_TLD_POINTS = 2;

    // Starts a local server that receives and displays search-result URLs.
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8766), 0);
        server.createContext("/urls", UrlQueueServer::receiveUrls);
        server.createContext("/analyze", UrlQueueServer::analyzeQueue);
        server.createContext("/top", UrlQueueServer::getTopResults);
        server.start();
        System.out.println("URL queue listening at http://127.0.0.1:8766");
    }

    // Replaces the queue with up to ten URLs sent by the browser extension.
    private static void receiveUrls(HttpExchange exchange) throws IOException {
        if (!isAllowedOrigin(exchange)) {
            send(exchange, 403, "Only the browser extension can send URLs.");
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Use POST to add URLs.");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String[] incoming = body.split("\\R");
        String[] accepted = new String[MAX_URLS];
        int count = 0;

        // Keeps valid web addresses and ignores anything after the first ten.
        for (String item : incoming) {
            if (count == MAX_URLS) {
                break;
            }
            String url = item.trim();
            if (url.isEmpty()) {
                continue;
            }
            if (!isWebUrl(url)) {
                send(exchange, 400, "A URL must start with http:// or https://.");
                return;
            }
            accepted[count++] = url;
        }

        URLS.replace(accepted, count);
        send(exchange, 200, "Saved " + count + " URL(s).");
    }

    // Checks that a string is an HTTP or HTTPS address with a host name.
    private static boolean isWebUrl(String value) {
        try {
            URI url = URI.create(value);
            String scheme = url.getScheme();
            return url.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    // Scores each queued URL, keeps all scores for Google, and saves the three lowest for the popup.
    private static void analyzeQueue(HttpExchange exchange) throws IOException {
        if (!isAllowedOrigin(exchange)) {
            send(exchange, 403, "Only the browser extension can request scores.");
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Use GET to analyze the queue.");
            return;
        }

        List<HeapSort.ScoredUrl> scoredResults = new ArrayList<>();
        String url;
        while ((url = URLS.dequeue()) != null) {
            scoredResults.add(analyzeUrl(url));
        }

        if (scoredResults.isEmpty()) {
            TOP_RESULTS = Collections.emptyList();
            send(exchange, 404, "Queue is empty.");
            return;
        }

        // Keep the full set for the existing Google-page display.
        StringBuilder results = new StringBuilder();
        for (HeapSort.ScoredUrl result : scoredResults) {
            if (results.length() > 0) {
                results.append('\n');
            }
            results.append(formatResult(result));
        }

        // Save the min-heap's three lowest scores for the extension popup.
        TOP_RESULTS = Collections.unmodifiableList(
                HeapSort.getLowestScoringUrls(scoredResults, 3));
        send(exchange, 200, results.toString());
    }

    // Returns the most recently analyzed three lowest-scoring URLs to the popup.
    private static void getTopResults(HttpExchange exchange) throws IOException {
        if (!isAllowedOrigin(exchange)) {
            send(exchange, 403, "Only the browser extension can request scores.");
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Use GET to request the lowest-scoring URLs.");
            return;
        }

        List<HeapSort.ScoredUrl> topResults = TOP_RESULTS;
        if (topResults.isEmpty()) {
            send(exchange, 404, "No scored URLs are available.");
            return;
        }

        StringBuilder results = new StringBuilder();
        for (HeapSort.ScoredUrl result : topResults) {
            if (results.length() > 0) {
                results.append('\n');
            }
            results.append(formatResult(result));
        }
        send(exchange, 200, results.toString());
    }

    // Adds points for length, matching keywords, and the URL's domain ending.
    private static HeapSort.ScoredUrl analyzeUrl(String url) {
        int points = 0;
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        StringBuilder flags = new StringBuilder();

        int lengthPoints = getUrlLengthPoints(url.length());
        points += lengthPoints;
        addFlag(flags, "URL length +" + lengthPoints);
        for (int index = 0; index < KEYWORDS.length; index++) {
            if (lowerUrl.contains(KEYWORDS[index])) {
                points += KEYWORD_POINTS[index];
                addFlag(flags, KEYWORDS[index] + " +" + KEYWORD_POINTS[index]);
            }
        }

        String tld = getTopLevelDomain(url);
        int tldPoints = getTldPoints(tld);
        points += tldPoints;
        addFlag(flags, "TLD " + tld + " +" + tldPoints);

        return new HeapSort.ScoredUrl(url, Math.min(points, MAX_POINTS), flags.toString());
    }

    // Keeps the existing plain-text score format used by the Google-page content script.
    private static String formatResult(HeapSort.ScoredUrl result) {
        return result.getUrl() + " | " + result.getScore() + "/" + MAX_POINTS
                + " | " + result.getFlags();
    }

    // Scores URL length in bands; the 55-character boundary belongs to the first band.
    private static int getUrlLengthPoints(int length) {
        if (length <= 55) {
            return 1;
        }
        if (length <= 75) {
            return 2;
        }
        if (length <= 100) {
            return 3;
        }
        if (length <= 150) {
            return 4;
        }
        return 5;
    }

    // Finds the final domain part, such as ".com", in a URL.
    private static String getTopLevelDomain(String url) {
        String host = URI.create(url).getHost();
        if (host == null || !host.contains(".")) {
            return "other";
        }
        int lastDot = host.lastIndexOf('.');
        return host.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    // Uses the listed TLD points or the default points for other endings.
    private static int getTldPoints(String tld) {
        for (int index = 0; index < TLDS.length; index++) {
            if (TLDS[index].equals(tld)) {
                return TLD_POINTS[index];
            }
        }
        return OTHER_TLD_POINTS;
    }

    // Adds another explanation to the comma-separated flag list.
    private static void addFlag(StringBuilder flags, String flag) {
        if (flags.length() > 0) {
            flags.append(", ");
        }
        flags.append(flag);
    }

    // Allows local tools and requests from Chrome extensions.
    private static boolean isAllowedOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || origin.startsWith("chrome-extension://");
    }

    // Adds the response headers needed by the browser extension.
    private static void addCorsHeaders(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && origin.startsWith("chrome-extension://")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        if ("true".equalsIgnoreCase(
                exchange.getRequestHeaders().getFirst("Access-Control-Request-Private-Network"))) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
        }
    }

    // Sends a plain-text response to the browser or a local test.
    private static void send(HttpExchange exchange, int status, String message) throws IOException {
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        addCorsHeaders(exchange);
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    // Stores URLs in order using a fixed-size circular array.
    private static final class StringQueue {
        private final String[] items;
        private int first;
        private int size;

        // Creates a queue with room for the first ten URLs.
        private StringQueue(int capacity) {
            items = new String[capacity];
        }

        // Clears old search results and adds the new results in order.
        private synchronized void replace(String[] urls, int count) {
            for (int index = 0; index < items.length; index++) {
                items[index] = null;
            }
            first = 0;
            size = 0;
            for (int index = 0; index < count; index++) {
                enqueue(urls[index]);
            }
        }

        // Adds one URL to the back of the queue.
        private void enqueue(String url) {
            int last = (first + size) % items.length;
            items[last] = url;
            size++;
        }

        // Removes and returns the oldest URL, or null when the queue is empty.
        private synchronized String dequeue() {
            if (size == 0) {
                return null;
            }
            String url = items[first];
            items[first] = null;
            first = (first + 1) % items.length;
            size--;
            return url;
        }
    }
}
