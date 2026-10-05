package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.objects.Item;
import org.example.questiongenerator.QuestionGenerator;

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

    private static final Map<String, Item> items = SavedData.getItems();
    private static final Map<String, Set<String>> possibleAnswers = new HashMap<>();

    private static long lastRequest = 0;

    public static List<Item> getItems(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return List.of();

        List<String> missingIds = ids.stream()
                .filter(id -> !items.containsKey(id))
                .distinct()
                .toList();

        if (!missingIds.isEmpty()) {
            requestItems(missingIds);
        }

        return ids.stream()
                .map(items::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static void requestItems(List<String> ids) throws Exception {
        Map<String, Item> downloadedItems = new HashMap<>();

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

                if (label.equals(id)) continue;

                String description = entity.path("descriptions").path("en").path("value").asText("");
                Map<String, List<String>> statements = extractStatements(entity.path("claims"));

                long qrank = QuestionGenerator.idsToQRank.getOrDefault(id, 0);

                Item item = new Item(
                        id,
                        label,
                        description,
                        statements,
                        qrank
                );

                items.put(id, item);
                downloadedItems.put(id, item);
            }
        }

        if (!downloadedItems.isEmpty()) {
            SavedData.addItems(downloadedItems);
        }
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

                List<String> values = statements.computeIfAbsent(
                        propertyId,
                        k -> new ArrayList<>()
                );

                if (!values.contains(linkedItemId)) {
                    values.add(linkedItemId);
                }
            }
        });

        return statements;
    }

    public static Map<String, Item> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public static List<String> getSimilarAnswers(
            String property,
            List<String> actualAnswerIds
    ) {
        Set<String> actualAnswers = new HashSet<>(actualAnswerIds);

        List<String> candidates = possibleAnswers
                .getOrDefault(property, Collections.emptySet())
                .stream()
                .filter(answer -> !actualAnswers.contains(answer))
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(candidates);

        return candidates.stream()
                .limit(3)
                .map(answer -> {
                    Item cachedItem = items.get(answer);

                    if (cachedItem == null) return answer;

                    return cachedItem.getLabel();
                })
                .toList();
    }

    public static void addPossibleAnswers(Map<String, List<String>> answers) {
        answers.forEach((property, values) ->
                possibleAnswers
                        .computeIfAbsent(property, k -> new HashSet<>())
                        .addAll(values)
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
                            try {
                                return Long.parseLong(value) * 1000;
                            } catch (NumberFormatException e) {
                                return 5000L;
                            }
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