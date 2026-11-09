
package org.example.questiongenerator;

import org.example.objects.Item;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public record ImageQuery(String question, String itemType) implements Query {
    @Override
    public List<Question> generate(Context context, int count) {
        if (count <= 0) return List.of();
        List<Item> possibleAnswers = context.cachedItems().stream()
                .filter(Query::validLabel)
                .filter(i -> i.getStatements().getOrDefault("P31", List.of()).contains(itemType))
                .collect(Collectors.toCollection(ArrayList::new));
        if (possibleAnswers.stream().map(i -> i.getLabel().toLowerCase(Locale.ROOT)).distinct().count() < 4)
            return List.of();

        List<Item> candidates = context.subjects().stream()
                .filter(Query::validLabel)
                .filter(i -> i.getStatements().getOrDefault("P31", List.of()).contains(itemType))
                .filter(i -> !i.getStatements().getOrDefault("P18", List.of()).isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);

        List<Question> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Item item : candidates) {
            if (result.size() >= count) break;
            if (!used.add(item.getId())) continue;

            List<Item> distractors = new ArrayList<>(possibleAnswers);
            Collections.shuffle(distractors);
            Set<String> labels = new HashSet<>();
            labels.add(item.getLabel().toLowerCase(Locale.ROOT));
            List<String> options = new ArrayList<>(List.of(item.getLabel()));
            for (Item other : distractors) {
                if (labels.add(other.getLabel().toLowerCase(Locale.ROOT))) options.add(other.getLabel());
                if (options.size() == 4) break;
            }
            if (options.size() != 4) continue;
            Collections.shuffle(options);
            String url = imageUrl(item);
            if (url == null) continue;
            result.add(new Question(url, item, item.getLabel(), options));
        }
        return result;
    }

    // The question's Item stores the Commons filename under P18.
    public static String imageUrl(Item item) {
        if (item == null) return null;
        List<String> images = item.getStatements().getOrDefault("P18", List.of());
        if (images.isEmpty() || images.get(0).isBlank()) return null;
        String filename = images.get(0);
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif"))) return null;
        return "https://commons.wikimedia.org/wiki/Special:FilePath/"
                + URLEncoder.encode(images.get(0), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
