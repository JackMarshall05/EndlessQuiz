package org.example.questiongenerator;

import org.example.objects.Item;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public record NumericQuery(
        String itemType,
        String property,
        String most,
        String least,
        boolean dated
) implements Query {

    @Override
    public List<Question> generate(Context context, int count) {
        List<Item> candidates = context.cachedItems().stream()
                .filter(Query::validLabel)
                .filter(i -> i.getStatements().getOrDefault("P31", List.of()).contains(itemType))
                .filter(i -> i.getNumericProperties().containsKey(property))
                .filter(i -> Double.isFinite(i.getNumericProperties().get(property).value()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (dated) {
            int latest = candidates.stream().map(i -> i.getNumericProperties().get(property).year())
                    .filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1);
            if (latest < 0) return List.of();
            candidates.removeIf(i -> {
                Integer year = i.getNumericProperties().get(property).year();
                return year == null || year < latest - 5;
            });
        }
        if (candidates.size() < 4) return List.of();

        List<Question> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int attempts = 0;
        while (result.size() < count && attempts++ < Math.max(40, count * 15)) {
            Collections.shuffle(candidates);
            List<Item> four = new ArrayList<>(candidates.subList(0, 4));
            if (four.stream().map(Item::getLabel).distinct().count() != 4) continue;
            boolean highest = ThreadLocalRandom.current().nextBoolean();
            Comparator<Item> byValue = Comparator.comparingDouble(i -> i.getNumericProperties().get(property).value());
            Item answer = highest ? Collections.max(four, byValue) : Collections.min(four, byValue);
            double value = answer.getNumericProperties().get(property).value();
            if (four.stream().filter(i -> Double.compare(i.getNumericProperties().get(property).value(), value) == 0).count() != 1) continue;

            if (dated) {
                double next = highest
                        ? four.stream().filter(i -> i != answer).mapToDouble(i -> i.getNumericProperties().get(property).value()).max().orElse(value)
                        : four.stream().filter(i -> i != answer).mapToDouble(i -> i.getNumericProperties().get(property).value()).min().orElse(value);
                if (value <= 0 || next <= 0 || Math.abs(value - next) / Math.max(value, next) < 0.10) continue;
            }

            String key = property + ":" + highest + ":" + four.stream().map(Item::getId).sorted().collect(Collectors.joining(","));
            if (!seen.add(key)) continue;
            List<String> options = four.stream().map(Item::getLabel).collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(options);
            result.add(new Question(highest ? most : least, answer, answer.getLabel(), options, null));
        }
        return result;
    }
}
