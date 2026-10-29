package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.objects.Item;
import org.example.questiongenerator.QuestionGenerator;
import org.example.saveddata.SavedDataManager;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class WikidataClient {
    private static final long REQUEST_DELAY = 1000;
    private static final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, Item> items = new HashMap<>(SavedDataManager.SAVED_ITEM_DATA.getData());
    private static final Map<String, Set<String>> possibleAnswers = new HashMap<>();
    public static final HashMap<String, Integer> idRequestFrequency = new HashMap<>();
    private static final Set<String> NUMERIC_PROPERTIES = Set.of(
            "P1082", // population
            "P2046", // area
            "P2044", // elevation above sea level
            "P2043", // length
            "P2048", // height
            "P2049", // width
            "P2050", // wingspan
            "P2053", // watershed area
            "P2067", // mass
            "P2234"  // volume
    );
    private static long lastRequest = 0;

    public static synchronized List<Item> getItems(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> missing = ids.stream().filter(Objects::nonNull).filter(id -> !items.containsKey(id)).distinct().toList();
        if (!missing.isEmpty()) requestItems(missing);
        return ids.stream().map(items::get).filter(Objects::nonNull).toList();
    }

    public static synchronized Map<String, Item> getItems() { return Map.copyOf(items); }

    // Gradually enrich old items.json records; numericLoaded prevents repeatedly fetching items with no numeric properties.
    public static synchronized void enrichCachedItems(int limit) throws Exception {
        if (limit <= 0) return;
        List<String> ids = items.values().stream()
                .filter(i -> !i.isNumericLoaded())
                .filter(i -> i.getStatements().getOrDefault("P31", List.of()).stream().anyMatch(type -> Set.of(
                        "Q6256", "Q515", "Q5", "Q8502", "Q4022", "Q23397", "Q41176", "Q1248784", "Q11436").contains(type)))
                .sorted(Comparator.comparingLong(Item::getQrank).reversed())
                .map(Item::getId).limit(limit).toList();
        if (!ids.isEmpty()) {
            requestItems(ids);
            SavedDataManager.SAVED_ITEM_DATA.saveData();
        }
    }

    private static void requestItems(List<String> ids) throws Exception {
        Map<String, Item> downloaded = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 50) {
            List<String> batch = ids.subList(start, Math.min(start + 50, ids.size()));
            String url = "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=" + String.join("%7C", batch)
                    + "&props=labels%7Cdescriptions%7Cclaims&languages=en&format=json&formatversion=2";
            JsonNode entities = requestJson(url).path("entities");
            for (String id : batch) {
                JsonNode entity = entities.path(id);
                if (entity.isMissingNode() || entity.isNull() || entity.has("missing")) continue;
                String label = entity.path("labels").path("en").path("value").asText(id);
                Item old = items.get(id);
                if (label.equals(id) && old == null) continue;
                String description = entity.path("descriptions").path("en").path("value").asText("");
                idRequestFrequency.merge(id, 1, Integer::sum);
                Item item = new Item(id, label.equals(id) ? old.getLabel() : label,
                        description.isBlank() && old != null ? old.getDescription() : description,
                        extractStatements(entity.path("claims")),
                        old == null ? QuestionGenerator.idsToQRank.getOrDefault(id, 0) : old.getQrank(),
                        extractNumericProperties(entity.path("claims")), true);
                items.put(id, item);
                downloaded.put(id, item);
            }
        }
        if (!downloaded.isEmpty()) SavedDataManager.SAVED_ITEM_DATA.recordData(downloaded);
    }

    private static Map<String, List<String>> extractStatements(JsonNode claims) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        claims.fields().forEachRemaining(field -> {
            for (JsonNode claim : field.getValue()) {
                if ("deprecated".equals(claim.path("rank").asText())) continue;
                JsonNode value = claim.path("mainsnak").path("datavalue");
                if (!"wikibase-entityid".equals(value.path("type").asText())) continue;
                String id = value.path("value").path("id").asText("");
                if (!id.matches("Q\\d+")) continue;
                List<String> ids = result.computeIfAbsent(field.getKey(), k -> new ArrayList<>());
                if (!ids.contains(id)) ids.add(id);
            }
        });
        return result;
    }

    private static Map<String, Item.Measurement> extractNumericProperties(JsonNode claims) {
        Map<String, Item.Measurement> result = new HashMap<>();
        for (String property : NUMERIC_PROPERTIES) {
            int selectedYear = Integer.MIN_VALUE;
            int selectedRank = -1;
            for (JsonNode claim : claims.path(property)) {
                if ("deprecated".equals(claim.path("rank").asText())) continue;
                // Exclude figures that only describe a part of the subject.
                if (claim.path("qualifiers").has("P518") || claim.path("qualifiers").has("P642")) continue;
                JsonNode datavalue = claim.path("mainsnak").path("datavalue");
                if (!"quantity".equals(datavalue.path("type").asText())) continue;
                JsonNode value = datavalue.path("value");
                Double factor = unitFactor(property, value.path("unit").asText("1"));
                if (factor == null) continue;
                double number;
                try { number = Double.parseDouble(value.path("amount").asText()) * factor; }
                catch (NumberFormatException e) { continue; }
                if (!Double.isFinite(number)) continue;
                Integer year = yearOf(claim);
                int yearScore = year == null ? Integer.MIN_VALUE : year;
                int rankScore = "preferred".equals(claim.path("rank").asText()) ? 1 : 0;
                boolean population = property.equals("P1082");
                boolean better = !result.containsKey(property) || (population
                        ? yearScore > selectedYear || (yearScore == selectedYear && rankScore > selectedRank)
                        : rankScore > selectedRank || (rankScore == selectedRank && yearScore > selectedYear));
                if (!better) continue;
                result.put(property, new Item.Measurement(number, canonicalUnit(property), year));
                selectedYear = yearScore;
                selectedRank = rankScore;
            }
        }
        return result;
    }

    private static Integer yearOf(JsonNode claim) {
        for (JsonNode qualifier : claim.path("qualifiers").path("P585")) {
            String date = qualifier.path("datavalue").path("value").path("time").asText("");
            Matcher matcher = Pattern.compile("^([+-])(\\d{4,})-").matcher(date);
            if (matcher.find()) {
                try { return Integer.parseInt(matcher.group(2)) * (matcher.group(1).equals("-") ? -1 : 1); }
                catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static String canonicalUnit(String property) {
        return switch (property) {
            case "P1082" -> "people";
            case "P2046", "P2053" -> "m²";
            case "P2234" -> "m³";
            case "P2067" -> "kg";
            default -> "m";
        };
    }

    private static Double unitFactor(String property, String rawUnit) {
        String unit = rawUnit.substring(rawUnit.lastIndexOf('/') + 1);
        if (property.equals("P1082")) return rawUnit.equals("1") ? 1.0 : null;
        if (property.equals("P2046") || property.equals("P2053")) return switch (unit) {
            case "Q25343" -> 1.0;                 // square metre
            case "Q712226" -> 1_000_000.0;       // square kilometre
            case "Q232291" -> 2_589_988.110336; // square mile
            case "Q35852" -> 10_000.0;           // hectare
            default -> null;
        };
        if (property.equals("P2234")) return switch (unit) {
            case "Q25517" -> 1.0;                 // cubic metre
            case "Q4243638" -> 1_000_000_000.0; // cubic kilometre
            case "Q11582" -> 0.001;              // litre
            default -> null;
        };
        if (property.equals("P2067")) return switch (unit) {
            case "Q11570" -> 1.0;  // kilogram
            case "Q41803" -> 0.001; // gram
            default -> null;
        };
        return switch (unit) {
            case "Q11573" -> 1.0;       // metre
            case "Q828224" -> 1000.0;   // kilometre
            case "Q174728" -> 0.01;     // centimetre
            case "Q174789" -> 0.001;    // millimetre
            case "Q3710" -> 0.3048;     // foot
            case "Q218593" -> 0.0254;  // inch
            case "Q253276" -> 1609.344; // mile
            default -> null;
        };
    }

    public static synchronized List<String> getSimilarAnswers(String property, List<String> actualIds) {
        Set<String> actual = new HashSet<>(actualIds);
        List<String> candidates = possibleAnswers.getOrDefault(property, Set.of()).stream()
                .filter(id -> !actual.contains(id)).map(items::get).filter(Objects::nonNull)
                .map(Item::getLabel).filter(s -> s != null && !s.isBlank() && !s.matches("Q\\d+"))
                .distinct().collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);
        return candidates.stream().limit(3).toList();
    }

    public static synchronized void addPossibleAnswers(Map<String, List<String>> answers) {
        answers.forEach((property, ids) -> possibleAnswers.computeIfAbsent(property, k -> new HashSet<>()).addAll(ids));
    }

    private static JsonNode requestJson(String url) throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            waitForRequest();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "EndlessQuiz/1.0 (educational project; add your contact address)")
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                long delay = response.headers().firstValue("Retry-After")
                        .map(s -> { try { return Long.parseLong(s) * 1000L; } catch (NumberFormatException e) { return 2000L; } })
                        .orElse(2000L);
                Thread.sleep(delay);
                continue;
            }
            if (response.statusCode() != 200) throw new IllegalStateException("Wikidata HTTP " + response.statusCode());
            return mapper.readTree(response.body());
        }
        throw new IllegalStateException("Wikidata request failed after retries");
    }

    private static synchronized void waitForRequest() throws InterruptedException {
        long wait = REQUEST_DELAY - (System.currentTimeMillis() - lastRequest);
        if (wait > 0) Thread.sleep(wait);
        lastRequest = System.currentTimeMillis();
    }
}
