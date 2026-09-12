package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.objects.Item;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class WikidataClient {

    private static final long REQUEST_DELAY = 500;
    private static final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> labels = SavedData.getLabels();
    private static final Map<String, List<String>> possibleAnswers = new HashMap<>();
    private static final Map<String, Long> pageViews = new HashMap<>();

    private static long lastRequest = 0;

    public static List<Item> getItems(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return List.of();

        List<Item> items = new ArrayList<>();

        for (int start = 0; start < ids.size(); start += 50) {
            List<String> batch = ids.subList(start, Math.min(start + 50, ids.size()));

            String url = "https://www.wikidata.org/w/api.php"
                    + "?action=wbgetentities"
                    + "&ids=" + String.join("%7C", batch)
                    + "&props=labels%7Cdescriptions%7Cclaims"
                    + "&languages=en"
                    + "&format=json"
                    + "&formatversion=2";

            JsonNode entities = requestJson(url).path("entities");

            for (String id : batch) {
                JsonNode entity = entities.path(id);
                if (entity.isMissingNode() || entity.isNull() || entity.has("missing")) continue;

                String label = entity.path("labels").path("en").path("value").asText(id);
                String description = entity.path("descriptions").path("en").path("value").asText("");
                Map<String, List<String>> statements = extractStatements(entity.path("claims"));

                items.add(new Item(id, label, description, statements));
            }
        }

        return items;
    }

    private static Map<String, List<String>> extractStatements(JsonNode claims) {
        Map<String, List<String>> statements = new LinkedHashMap<>();

        claims.fields().forEachRemaining(property -> {
            String propertyId = property.getKey();

            for (JsonNode claim : property.getValue()) {
                JsonNode dataValue = claim.path("mainsnak").path("datavalue");

                if (!"wikibase-entityid".equals(dataValue.path("type").asText())) continue;

                String linkedItemId = dataValue.path("value").path("id").asText("");
                if (!linkedItemId.matches("Q\\d+")) continue;

                List<String> values = statements.computeIfAbsent(propertyId, k -> new ArrayList<>());
                if (!values.contains(linkedItemId)) values.add(linkedItemId);
            }
        });

        return statements;
    }

    public static Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    public static void requestLabels(Set<String> ids) throws Exception {
        List<String> missingIds = ids.stream().filter(id -> !labels.containsKey(id)).toList();
        if (missingIds.isEmpty()) return;

        for (int start = 0; start < missingIds.size(); start += 50) {
            List<String> batch = missingIds.subList(start, Math.min(start + 50, missingIds.size()));

            String url = "https://www.wikidata.org/w/api.php"
                    + "?action=wbgetentities"
                    + "&ids=" + String.join("%7C", batch)
                    + "&props=labels"
                    + "&languages=en"
                    + "&format=json";

            JsonNode entities = requestJson(url).path("entities");

            for (String id : batch) {
                String label = entities.path(id).path("labels").path("en").path("value").asText(id);
                labels.put(id, label);
            }
        }

        SavedData.addLabels(labels);
    }

    public static List<String> getSimilarAnswers(String property, Item item) {
        Set<String> actualAnswers = new HashSet<>(
                item.getStatements().getOrDefault(property, Collections.emptyList())
        );

        List<String> candidates = possibleAnswers
                .getOrDefault(property, Collections.emptyList())
                .stream()
                .filter(answer -> !actualAnswers.contains(answer))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidates.size() < 3) {
            candidates = possibleAnswers.values().stream()
                    .flatMap(List::stream)
                    .filter(answer -> !actualAnswers.contains(answer))
                    .distinct()
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        Collections.shuffle(candidates);

        return candidates.stream()
                .limit(3)
                .map(answer -> labels.getOrDefault(answer, answer))
                .toList();
    }

    public static void addPossibleAnswers(Map<String, List<String>> answers) {
        answers.forEach((property, values) ->
                possibleAnswers.merge(property, new ArrayList<>(values), (existing, incoming) -> {
                    existing.addAll(incoming);
                    return existing;
                })
        );
    }

    private static JsonNode requestJson(String url) throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            waitForRequest();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "WikiQuiz/1.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() == 429) {
                long wait = response.headers().firstValue("Retry-After")
                        .map(value -> {
                            try { return Long.parseLong(value) * 1000; }
                            catch (NumberFormatException e) { return 5000L; }
                        })
                        .orElse(5000L);

                Thread.sleep(wait);
                continue;
            }

            if (response.statusCode() >= 500) {
                Thread.sleep(1000L * (attempt + 1));
                continue;
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Request failed: HTTP " + response.statusCode() + "\n" + response.body()
                );
            }

            return mapper.readTree(response.body());
        }

        throw new RuntimeException("Request failed after retries.");
    }

    private static synchronized void waitForRequest() throws InterruptedException {
        long wait = REQUEST_DELAY - (System.currentTimeMillis() - lastRequest);
        if (wait > 0) Thread.sleep(wait);
        lastRequest = System.currentTimeMillis();
    }
}