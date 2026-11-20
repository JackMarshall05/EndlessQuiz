package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.questiongenerator.Question;
import org.example.questiongenerator.QuestionGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Browser-based replacement for GuiClient.
 * Keeps the existing QuestionGenerator and Question classes unchanged.
 * Requires Java 21 and no additional dependencies.
 */
public final class WebClient {
    private static final Map<String, ActiveQuestion> QUESTIONS = new ConcurrentHashMap<>();
    private static final long QUESTION_TTL_MS = Duration.ofMinutes(30).toMillis();
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;
    private static final HttpClient IMAGE_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private record ActiveQuestion(Question question, long createdAt, AtomicBoolean answered) {
        ActiveQuestion(Question question) {
            this(question, System.currentTimeMillis(), new AtomicBoolean(false));
        }
    }

    private WebClient() {}

    public static void main(String[] args) throws IOException {
        String host = System.getenv().getOrDefault("HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", WebClient::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Wiki Quiz web app running at http://localhost:" + port);
        System.out.println("Press Ctrl+C in this terminal to stop it.");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            switch (path) {
                case "/", "/index.html" -> serveResource(exchange, "/web/index.html", "text/html; charset=utf-8");
                case "/styles.css" -> serveResource(exchange, "/web/styles.css", "text/css; charset=utf-8");
                case "/app.js" -> serveResource(exchange, "/web/app.js", "text/javascript; charset=utf-8");
                case "/api/question" -> handleQuestion(exchange);
                case "/api/answer" -> handleAnswer(exchange);
                case "/api/image" -> handleImage(exchange);
                default -> sendJson(exchange, 404, "{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            System.err.println("Web request failed: " + exchange.getRequestURI());
            e.printStackTrace();
            sendJson(exchange, 500, "{\"error\":\"An unexpected error occurred\"}");
        } finally {
            exchange.close();
        }
    }

    private static void serveResource(HttpExchange exchange, String resource, String contentType) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        try (InputStream input = WebClient.class.getResourceAsStream(resource)) {
            if (input == null) {
                sendJson(exchange, 404, "{\"error\":\"Web resource missing from classpath\"}");
                return;
            }
            send(exchange, 200, contentType, input.readAllBytes());
        }
    }

    private static void handleQuestion(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        Map<String, String> params = parseForm(exchange.getRequestURI().getRawQuery());
        String previous = params.get("previous");
        if (previous != null) QUESTIONS.remove(previous);
        long now = System.currentTimeMillis();
        QUESTIONS.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > QUESTION_TTL_MS);

        Question question;
        try {
            // The existing question generator uses shared saved data, so serialize its calls.
            synchronized (QuestionGenerator.class) {
                question = QuestionGenerator.pollQuestion();
            }
        } catch (Exception e) {
            System.err.println("Question generation failed:");
            e.printStackTrace();
            sendJson(exchange, 503, "{\"error\":\"Could not create a question. Please try again.\"}");
            return;
        }
        if (question == null || question.options() == null || question.options().size() != 4) {
            sendJson(exchange, 503, "{\"error\":\"No four-option question available.\"}");
            return;
        }

        String id = UUID.randomUUID().toString();
        QUESTIONS.put(id, new ActiveQuestion(question));
        boolean hasImage = question.imageUrl() != null && !question.imageUrl().isBlank();
        boolean imageRequired = isImageQuestion(question);
        String rawText = question.questionText() == null ? "" : question.questionText();
        String text = imageRequired ? "What is shown in this image?" : rawText;
        if (!imageRequired && text.contains("%s") && question.item() != null) {
            text = text.replace("%s", question.item().getLabel());
        }

        StringBuilder json = new StringBuilder("{\"id\":").append(jsonString(id))
                .append(",\"text\":").append(jsonString(text))
                .append(",\"hasImage\":").append(hasImage)
                .append(",\"imageRequired\":").append(imageRequired)
                .append(",\"options\":[");
        List<String> options = question.options();
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) json.append(',');
            json.append(jsonString(options.get(i)));
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private static void handleAnswer(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) return;
        byte[] body = exchange.getRequestBody().readNBytes(8193);
        if (body.length > 8192) {
            sendJson(exchange, 413, "{\"error\":\"Request too large\"}");
            return;
        }
        Map<String, String> data = parseForm(new String(body, StandardCharsets.UTF_8));
        ActiveQuestion active = getActive(data.get("id"));
        if (active == null) {
            sendJson(exchange, 404, "{\"error\":\"Question has expired\"}");
            return;
        }
        String selected = data.get("selected");
        if (selected == null || !active.question().options().contains(selected)) {
            sendJson(exchange, 400, "{\"error\":\"Invalid answer option\"}");
            return;
        }
        if (!active.answered().compareAndSet(false, true)) {
            sendJson(exchange, 409, "{\"error\":\"Question already answered\"}");
            return;
        }
        String answer = active.question().answer();
        boolean correct = selected.equals(answer);
        sendJson(exchange, 200, "{\"correct\":" + correct + ",\"correctAnswer\":" + jsonString(answer) + "}");
    }

    private static void handleImage(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) return;
        String id = parseForm(exchange.getRequestURI().getRawQuery()).get("id");
        ActiveQuestion active = getActive(id);
        if (active == null || active.question().imageUrl() == null) {
            sendJson(exchange, 404, "{\"error\":\"Image not found\"}");
            return;
        }
        try {
            URI uri = URI.create(active.question().imageUrl());
            for (int redirects = 0; redirects <= 5; redirects++) {
                // Only Wikimedia-family HTTPS hosts can be fetched by this proxy.
                // Checking on *every* redirect avoids redirects to local/private services.
                if (!isTrustedImageUri(uri)) throw new IOException("Untrusted image URL: " + uri);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "EndlessQuiz/1.0 (educational Java web quiz)")
                        .header("Accept", "image/*")
                        .GET().build();
                HttpResponse<InputStream> response = IMAGE_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream stream = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400) {
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> new IOException("Image redirect without Location"));
                        uri = uri.resolve(location);
                        continue;
                    }
                    if (status != 200) throw new IOException("Image HTTP " + status);
                    String contentType = response.headers().firstValue("Content-Type")
                            .orElse("application/octet-stream").split(";")[0].trim().toLowerCase();
                    if (!List.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif").contains(contentType)) {
                        throw new IOException("Unsupported image content type: " + contentType);
                    }
                    byte[] bytes = stream.readNBytes(MAX_IMAGE_BYTES + 1);
                    if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                        throw new IOException("Empty or oversized image");
                    }
                    send(exchange, 200, contentType, bytes);
                    return;
                }
            }
            throw new IOException("Too many image redirects");
        } catch (Exception e) {
            System.err.println("Image unavailable for: " + active.question().imageUrl());
            System.err.println("Reason: " + e.getMessage());
            sendJson(exchange, 502, "{\"error\":\"Image unavailable\"}");
        }
    }

    private static boolean isImageQuestion(Question question) {
        return question.imageUrl() != null && question.imageUrl().equals(question.questionText());
    }

    private static boolean isTrustedImageUri(URI uri) {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return false;
        host = host.toLowerCase();
        return host.equals("wikimedia.org") || host.endsWith(".wikimedia.org")
                || host.equals("wikipedia.org") || host.endsWith(".wikipedia.org")
                || host.equals("wikidata.org") || host.endsWith(".wikidata.org");
    }

    private static ActiveQuestion getActive(String id) {
        ActiveQuestion active = id == null ? null : QUESTIONS.get(id);
        if (active == null) return null;
        if (System.currentTimeMillis() - active.createdAt() > QUESTION_TTL_MS) {
            QUESTIONS.remove(id);
            return null;
        }
        return active;
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isBlank()) return values;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            String key = URLDecoder.decode(i < 0 ? part : part.substring(0, i), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(i < 0 ? "" : part.substring(i + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    private static String jsonString(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (exchange.getRequestMethod().equals(method)) return true;
        exchange.getResponseHeaders().set("Allow", method);
        sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        return false;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; img-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
