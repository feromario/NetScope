import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class UrlQueueServer {
    private static final int MAX_URLS = 10;
    private static final StringQueue URLS = new StringQueue(MAX_URLS);

    // Starts a local server that receives and displays search-result URLs.
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8766), 0);
        server.createContext("/urls", UrlQueueServer::receiveUrls);
        server.createContext("/queue", UrlQueueServer::showQueue);
        server.createContext("/next", UrlQueueServer::takeNext);
        server.start();
        System.out.println("URL queue listening at http://127.0.0.1:8766");
    }

    // Replaces the queue with up to ten URLs sent by the browser extension.
    private static void receiveUrls(HttpExchange exchange) throws IOException {
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

    // Returns the current queue as one URL per line.
    private static void showQueue(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Use GET to read the queue.");
            return;
        }
        send(exchange, 200, URLS.toText());
    }

    // Removes and returns the oldest URL in the queue.
    private static void takeNext(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Use GET to take the next URL.");
            return;
        }
        String url = URLS.dequeue();
        send(exchange, url == null ? 404 : 200, url == null ? "Queue is empty." : url);
    }

    // Sends a plain-text response to the browser or a local test.
    private static void send(HttpExchange exchange, int status, String message) throws IOException {
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
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

        // Shows the URLs in queue order without removing them.
        private synchronized String toText() {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < size; index++) {
                if (index > 0) {
                    result.append('\n');
                }
                result.append(items[(first + index) % items.length]);
            }
            return result.toString();
        }
    }
}
