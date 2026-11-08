package org.example.questiongenerator;

import org.example.WikidataClient;
import org.example.objects.Item;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public record RelationshipQuery(
        String question,
        String itemType,
        List<QuestionCondition> conditions,
        boolean notTrue
) implements Query {

    private record Choice(QuestionCondition condition, List<String> ids, String selectedId) {}
    private record Selection(Item item, List<Choice> choices) {}

    @Override
    public List<Question> generate(Context context, int count) throws Exception {
        List<Item> candidates = context.subjects().stream().filter(this::supports)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);
        List<Selection> selections = new ArrayList<>();
        Map<String, List<String>> similarAnswers = new HashMap<>();

        // Resolve the statements for a group first, then fetch answer labels together.
        for (Item item : candidates) {
            if (selections.size() >= Math.max(3, count * 3)) break;
            List<Choice> choices = new ArrayList<>();
            boolean valid = true;
            for (QuestionCondition condition : conditions) {
                List<String> ids = getAnswerIds(item, condition, context);
                if (ids.isEmpty()) { valid = false; break; }
                choices.add(new Choice(condition, ids, ids.get(ThreadLocalRandom.current().nextInt(ids.size()))));
            }
            if (!valid || choices.isEmpty()) continue;
            if (notTrue && choices.size() == 1 && choices.get(0).ids().size() < 3) continue;
            if (notTrue && choices.size() > 1 && choices.size() < 4) continue;
            if (notTrue && choices.size() > 4) {
                Collections.shuffle(choices);
                choices = new ArrayList<>(choices.subList(0, 4));
            }
            selections.add(new Selection(item, choices));
            for (Choice choice : choices) {
                if (!choice.condition().reverse()) similarAnswers
                        .computeIfAbsent(choice.condition().property(), k -> new ArrayList<>()).addAll(choice.ids());
            }
        }
        if (selections.isEmpty()) return List.of();

        List<String> required = selections.stream().flatMap(s -> s.choices().stream())
                .flatMap(c -> c.ids().stream()).distinct().toList();
        WikidataClient.getItems(required);
        WikidataClient.addPossibleAnswers(similarAnswers);

        List<Question> result = new ArrayList<>();
        Map<String, Item> lookup = WikidataClient.getItems();
        for (Selection selection : selections) {
            Question made = createQuestion(selection, context, lookup);
            if (made != null) result.add(made);
            if (result.size() >= count) break;
        }
        return result;
    }

    private boolean supports(Item item) {
        if (!Query.validLabel(item)) return false;
        if (!item.getStatements().getOrDefault("P31", List.of()).contains(itemType)) return false;
        return conditions.stream().allMatch(c -> c.reverse() ||
                !item.getStatements().getOrDefault(c.property(), List.of()).isEmpty());
    }

    private List<String> getAnswerIds(Item item, QuestionCondition condition, Context context) throws Exception {
        if (condition.reverse()) {
            return context.cachedItems().stream()
                    .filter(i -> condition.answerItemType() == null ||
                            i.getStatements().getOrDefault("P31", List.of()).contains(condition.answerItemType()))
                    .filter(i -> i.getStatements().getOrDefault(condition.property(), List.of()).contains(item.getId()))
                    .map(Item::getId).distinct().toList();
        }
        List<String> ids = item.getStatements().getOrDefault(condition.property(), List.of());
        if (ids.isEmpty() || condition.answerType() == null) return ids;
        return WikidataClient.getItems(ids).stream()
                .flatMap(i -> i.getStatements().getOrDefault(condition.answerType(), List.of()).stream())
                .distinct().toList();
    }

    private Question createQuestion(Selection selection, Context context, Map<String, Item> lookup) {
        if (!notTrue) return normalQuestion(selection, lookup);
        if (selection.choices().size() == 1) return singleNotTrueQuestion(selection, context, lookup);
        return multiNotTrueQuestion(selection, context, lookup);
    }

    private Question normalQuestion(Selection selection, Map<String, Item> lookup) {
        Choice choice = selection.choices().get(0);
        Item correct = lookup.get(choice.selectedId());
        if (!Query.validLabel(correct)) return null;
        List<String> options = new ArrayList<>(WikidataClient.getSimilarAnswers(choice.condition().property(), choice.ids()));
        options.removeIf(correct.getLabel()::equals);
        if (new HashSet<>(options).size() < 3) return null;
        options = new ArrayList<>(new LinkedHashSet<>(options));
        options = new ArrayList<>(options.subList(0, 3));
        options.add(correct.getLabel());
        Collections.shuffle(options);
        return build(selection.item(), correct.getLabel(), options);
    }

    private Question singleNotTrueQuestion(Selection selection, Context context, Map<String, Item> lookup) {
        Choice choice = selection.choices().get(0);
        List<String> actualIds = new ArrayList<>(choice.ids());
        Collections.shuffle(actualIds);
        List<String> trueOptions = actualIds.stream()
                .map(lookup::get)
                .filter(Query::validLabel).map(Item::getLabel).distinct().limit(3).toList();
        if (trueOptions.size() != 3) return null;
        String falseOption = getFalseAnswer(selection.item(), choice, context);
        if (falseOption == null || trueOptions.contains(falseOption)) return null;
        List<String> options = new ArrayList<>(trueOptions);
        options.add(falseOption);
        Collections.shuffle(options);
        return build(selection.item(), falseOption, options);
    }

    private Question multiNotTrueQuestion(Selection selection, Context context, Map<String, Item> lookup) {
        List<Choice> choices = new ArrayList<>(selection.choices());
        if (choices.size() != 4) return null;
        Collections.shuffle(choices);
        String falseAnswer = null;
        int falseIndex = -1;
        for (int i = 0; i < choices.size(); i++) {
            falseAnswer = getFalseAnswer(selection.item(), choices.get(i), context);
            if (falseAnswer != null) { falseIndex = i; break; }
        }
        if (falseIndex < 0) return null;
        List<String> options = new ArrayList<>();
        String answer = null;
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i);
            if (c.condition().statement() == null) return null;
            String value = i == falseIndex ? falseAnswer : Optional.ofNullable(lookup.get(c.selectedId()))
                    .filter(Query::validLabel).map(Item::getLabel).orElse(null);
            if (value == null) return null;
            String option = c.condition().statement().formatted(value);
            options.add(option);
            if (i == falseIndex) answer = option;
        }
        if (new HashSet<>(options).size() != 4) return null;
        Collections.shuffle(options);
        return build(selection.item(), answer, options);
    }

    private String getFalseAnswer(Item item, Choice choice, Context context) {
        if (!choice.condition().reverse()) {
            List<String> options = WikidataClient.getSimilarAnswers(choice.condition().property(), choice.ids());
            return options.isEmpty() ? null : options.get(0);
        }
        List<Item> candidates = context.cachedItems().stream()
                .filter(Query::validLabel)
                .filter(i -> !choice.ids().contains(i.getId()))
                .filter(i -> choice.condition().answerItemType() == null ||
                        i.getStatements().getOrDefault("P31", List.of()).contains(choice.condition().answerItemType()))
                .filter(i -> !i.getStatements().getOrDefault(choice.condition().property(), List.of()).isEmpty())
                .filter(i -> !i.getStatements().getOrDefault(choice.condition().property(), List.of()).contains(item.getId()))
                .toList();
        return candidates.isEmpty() ? null : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).getLabel();
    }

    private Question build(Item item, String answer, List<String> options) {
        if (new HashSet<>(options).size() != 4) return null;
        return new Question(question.formatted(item.getLabel()), item, answer, options,
                (int) Math.min(Integer.MAX_VALUE, item.getQrank()));
    }
}
