package com.netscope.analysis;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NetScopeServer {
    private static final int PORT = 8765;
    private static final String[] URL_SHORTENERS = {
            "bit.ly", "t.co", "tinyurl.com", "ow.ly", "is.gd", "buff.ly", "shorturl.at"
    };
    private static final Pattern SUSPICIOUS_TERMS =
            Pattern.compile("(?i)(login|verify|account|password|secure|bank|wallet|payment|signin|"
                    + "confirm|update|gift|claim|crypto|unlock|recover)");
    private static final Pattern IPV4_ADDRESS =
            Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern ENCODED_CHARACTER =
            Pattern.compile("%[0-9a-f]{2}", Pattern.CASE_INSENSITIVE);
    private static final String[] REDIRECT_PARAMETERS = {
            "url", "u", "target", "redirect", "redirect_url", "destination", "dest", "next", "continue", "return"
    };
    private static List<String> knownMaliciousDomains = Collections.emptyList();

    private NetScopeServer() {
    }

    public static void main(String[] args) throws IOException {
        Path domainListPath = Paths.get(System.getProperty("netscope.badDomains", "known-malicious-domains.txt"));
        knownMaliciousDomains = loadKnownMaliciousDomains(domainListPath);
        System.out.println("Loaded " + knownMaliciousDomains.size() + " known malicious domain(s)");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/health", NetScopeServer::handleHealth);
        server.createContext("/analyze", NetScopeServer::handleAnalyze);
        server.setExecutor(null);
        server.start();
        System.out.println("NetScope analyzer listening at http://127.0.0.1:" + PORT);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"ok\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        send(exchange, 200, "{\"ok\":true,\"service\":\"NetScope analyzer\"}");
    }

    private static void handleAnalyze(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"ok\":false,\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            List<String> urls = parseUrls(body);
            NetScopeAnalyzer analyzer = new NetScopeAnalyzer();
            analyzer.enqueueTopResults(urls);

            StringBuilder results = new StringBuilder();
            results.append("[");
            boolean first = true;
            java.util.Optional<UrlAssessmentJob> next;
            while ((next = analyzer.dequeueNext()).isPresent()) {
                UrlAssessmentJob job = next.get();
                RiskAssessment assessment = analyzer.assess(job, collectConcerns(job.getUrl()));
                if (!first) {
                    results.append(",");
                }
                first = false;
                results.append("{")
                        .append("\"url\":\"").append(escape(assessment.getUrl())).append("\",")
                        .append("\"score\":").append(assessment.getScore()).append(",")
                        .append("\"label\":\"").append(escape(assessment.getLevel().getLabel())).append("\",")
                        .append("\"color\":\"").append(escape(assessment.getLevel().getColor())).append("\",")
                        .append("\"flags\":").append(flagsJson(assessment.getConcerns()))
                        .append("}");
            }
            results.append("]");
            send(exchange, 200, "{\"ok\":true,\"results\":" + results + "}");
        } catch (IllegalArgumentException | NullPointerException exception) {
            send(exchange, 400, "{\"ok\":false,\"error\":\"" + escape(exception.getMessage()) + "\"}");
        }
    }

    static List<RiskConcern> collectConcerns(String url) {
        return collectConcerns(url, knownMaliciousDomains);
    }

    static List<RiskConcern> collectConcerns(String url, List<String> maliciousDomains) {
        List<RiskConcern> concerns = new ArrayList<>();
        URI uri = URI.create(url);

        if ("http".equalsIgnoreCase(uri.getScheme())) {
            concerns.add(new RiskConcern("insecure-connection", RiskArea.CONNECTION,
                    "The result uses an unencrypted HTTP connection", "URL scheme check", 20));
        }

        String host = uri.getHost();
        if (host != null) {
            String lowerHost = host.toLowerCase(Locale.ROOT);
            if (isIpAddress(host)) {
                concerns.add(new RiskConcern("ip-address-host", RiskArea.DOMAIN,
                        "The result uses an IP address instead of a domain name", "Domain check", 20));
            }
            if (matchesKnownMaliciousDomain(lowerHost, maliciousDomains)) {
                concerns.add(new RiskConcern("known-malicious-domain", RiskArea.REPUTATION,
                        "The domain appears in the local known-malicious-domain list",
                        "Local domain list", 50));
            }
            if (lowerHost.contains("xn--")) {
                concerns.add(new RiskConcern("encoded-domain", RiskArea.DOMAIN,
                        "The domain uses encoded characters and may imitate another name",
                        "Domain encoding check", 20));
            }
            if (countOccurrences(lowerHost, '.') >= 4) {
                concerns.add(new RiskConcern("many-subdomains", RiskArea.DOMAIN,
                        "The domain has several subdomain levels", "Domain structure check", 10));
            }
            if (countOccurrences(lowerHost, '-') >= 2) {
                concerns.add(new RiskConcern("many-hyphens", RiskArea.DOMAIN,
                        "The domain contains multiple hyphens", "Domain structure check", 8));
            }
            for (String shortener : URL_SHORTENERS) {
                if (lowerHost.equals(shortener) || lowerHost.endsWith("." + shortener)) {
                    concerns.add(new RiskConcern("shortened-url", RiskArea.REDIRECTS,
                            "The result uses a URL-shortening service that hides its destination",
                            "Known shortener host check", 15));
                    break;
                }
            }
        }

        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            concerns.add(new RiskConcern("unusual-port", RiskArea.CONNECTION,
                    "The URL uses a non-standard web port", "URL port check", 10));
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (SUSPICIOUS_TERMS.matcher(lowerUrl).find()) {
            concerns.add(new RiskConcern("sensitive-url-term", RiskArea.PAGE_INDICATORS,
                    "The URL contains terms commonly used in deceptive pages", "URL indicator check", 20));
        }
        if (url.length() > 150) {
            concerns.add(new RiskConcern("long-url", RiskArea.PAGE_INDICATORS,
                    "The URL is unusually long", "URL length check", 8));
        }
        if (ENCODED_CHARACTER.matcher(url).find()) {
            concerns.add(new RiskConcern("encoded-url-content", RiskArea.PAGE_INDICATORS,
                    "The URL contains encoded characters that can obscure its destination",
                    "URL encoding check", 8));
        }
        String query = uri.getRawQuery();
        if (query != null) {
            if (countOccurrences(query, '&') >= 4) {
                concerns.add(new RiskConcern("many-query-parameters", RiskArea.PAGE_INDICATORS,
                        "The URL has many query parameters", "URL query check", 8));
            }
            if (hasExternalRedirectHint(query)) {
                concerns.add(new RiskConcern("redirect-parameter", RiskArea.REDIRECTS,
                        "The URL contains a parameter that may redirect to another site",
                        "URL redirect-hint check", 10));
            }
        }
        return concerns;
    }

    static List<String> loadKnownMaliciousDomains(Path path) throws IOException {
        List<String> domains = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                int commentStart = line.indexOf('#');
                String domain = (commentStart < 0 ? line : line.substring(0, commentStart)).trim();
                if (domain.isEmpty()) {
                    continue;
                }
                domain = domain.toLowerCase(Locale.ROOT);
                while (domain.endsWith(".")) {
                    domain = domain.substring(0, domain.length() - 1);
                }
                if (!isValidDomainEntry(domain)) {
                    throw new IOException("Invalid domain at " + path + ":" + lineNumber);
                }
                if (!domains.contains(domain)) {
                    domains.add(domain);
                }
            }
        }
        return domains;
    }

    private static boolean isValidDomainEntry(String domain) {
        if (domain.length() > 253 || domain.indexOf('.') < 0 || domain.contains("..")) {
            return false;
        }
        for (String label : domain.split("\\.")) {
            if (label.isEmpty() || label.length() > 63
                    || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesKnownMaliciousDomain(String host, List<String> domains) {
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIpAddress(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        if (!IPV4_ADDRESS.matcher(host).matches()) {
            return false;
        }
        for (String octet : host.split("\\.")) {
            int value = Integer.parseInt(octet);
            if (value > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasExternalRedirectHint(String query) {
        for (String parameter : query.split("&")) {
            int equals = parameter.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String name = parameter.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = parameter.substring(equals + 1).toLowerCase(Locale.ROOT);
            if (isRedirectParameter(name)
                    && (value.contains("://") || value.contains("%3a%2f%2f")
                    || value.startsWith("//") || value.startsWith("%2f%2f"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRedirectParameter(String name) {
        for (String redirectParameter : REDIRECT_PARAMETERS) {
            if (name.equals(redirectParameter)) {
                return true;
            }
        }
        return false;
    }

    private static int countOccurrences(String value, char character) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == character) {
                count++;
            }
        }
        return count;
    }

    private static String flagsJson(List<RiskConcern> concerns) {
        StringBuilder flags = new StringBuilder("[");
        for (int index = 0; index < concerns.size(); index++) {
            if (index > 0) {
                flags.append(",");
            }
            flags.append("\"").append(escape(concerns.get(index).getDescription())).append("\"");
        }
        return flags.append("]").toString();
    }

    private static List<String> parseUrls(String body) {
        int marker = body.indexOf("\"urls\"");
        if (marker < 0) {
            throw new IllegalArgumentException("Request must include urls");
        }
        int start = body.indexOf('[', marker);
        int end = body.indexOf(']', start);
        if (start < 0 || end < 0) {
            throw new IllegalArgumentException("urls must be an array");
        }
        List<String> urls = new ArrayList<>();
        String array = body.substring(start + 1, end);
        int position = 0;
        while (position < array.length()) {
            int quote = array.indexOf('"', position);
            if (quote < 0) {
                break;
            }
            int closing = array.indexOf('"', quote + 1);
            if (closing < 0) {
                throw new IllegalArgumentException("Invalid JSON string");
            }
            String value = array.substring(quote + 1, closing)
                    .replace("\\/", "/")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            if (!value.isBlank()) {
                urls.add(value);
            }
            position = closing + 1;
        }
        return urls;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
