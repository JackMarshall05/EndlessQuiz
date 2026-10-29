package org.example.questiongenerator;

import org.example.Logger;
import org.example.WikidataClient;
import org.example.objects.Item;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class QuestionGenerator {
    public static Difficulty difficulty = Difficulty.EASY;

    public static HashMap<String, Integer> idsToQRank = getIdsQRank();

    private static final CopyOnWriteArrayList<Question> bufferedQuestions = new CopyOnWriteArrayList<>();
    private static final List<Question> allQuestions = new ArrayList<>(bufferedQuestions);
    private static final int TARGET_BUFFER_SIZE = 20000;
    private static final Map<Difficulty, List<String>> questionIdsByDifficulty = new EnumMap<>(Difficulty.class);

    public enum Difficulty {
        EASY(10000000),
        MEDIUM(1000000),
        HARD(400000),
        VERY_HARD(100000);

        public final int PAGEVIEWS;

        Difficulty(int pageviews) {
            PAGEVIEWS = pageviews;
        }
    }

    public static final List<QuestionType> queries = List.of(
            // People
            new QuestionType("Which country was %s born in?", "Q5", List.of(new QuestionCondition(null, "P19", "P17")), false),
            new QuestionType("Which sports team has %s played for?", "Q5", List.of(new QuestionCondition(null, "P54", null)), false),
            new QuestionType("Which sports team has %s NOT played for?", "Q5", List.of(new QuestionCondition(null, "P54", null)), true),
            new QuestionType("Which song was NOT performed by %s?", "Q5", List.of(new QuestionCondition(null, "P175", null, true, "Q7366")), true),
            new QuestionType("Which song's lyrics were NOT written by %s?", "Q5", List.of(new QuestionCondition(null, "P676", null, true, "Q7366")), true),
            new QuestionType("Which song was NOT composed by %s?", "Q5", List.of(new QuestionCondition(null, "P86", null, true, "Q7366")), true),
            new QuestionType("Which album was NOT performed by %s?", "Q5", List.of(new QuestionCondition(null, "P175", null, true, "Q482994")), true),
            new QuestionType("Which university did %s attend?", "Q5", List.of(new QuestionCondition(null, "P69", null)), false),
            new QuestionType("Which field is %s known for?", "Q5", List.of(new QuestionCondition(null, "P101", null)), false),

            // Countries
            new QuestionType("What is the capital of %s?", "Q6256", List.of(new QuestionCondition(null, "P36", null)), false),
            new QuestionType("What currency is used in %s?", "Q6256", List.of(new QuestionCondition(null, "P38", null)), false),
            new QuestionType("Which continent is %s in?", "Q6256", List.of(new QuestionCondition(null, "P30", null)), false),
            new QuestionType("What is an official language of %s?", "Q6256", List.of(new QuestionCondition(null, "P37", null)), false),
            new QuestionType("Which country shares a border with %s?", "Q6256", List.of(new QuestionCondition(null, "P47", null)), false),
            new QuestionType("Which country does NOT share a border with %s?", "Q6256", List.of(new QuestionCondition(null, "P47", null)), true),
            new QuestionType("Which is NOT an official language of %s?", "Q6256", List.of(new QuestionCondition(null, "P37", null)), true),
            new QuestionType("Which city is NOT in %s?", "Q6256", List.of(new QuestionCondition(null, "P17", null, true, "Q515")), true),

            // Continents
            new QuestionType("Which country is NOT completely or partially in %s?", "Q5107", List.of(new QuestionCondition(null, "P30", null, true, "Q6256")), true),

            // Cities
            new QuestionType("Which country is %s in?", "Q515", List.of(new QuestionCondition(null, "P17", null)), false),
            new QuestionType("Which body of water is %s located beside?", "Q515", List.of(new QuestionCondition(null, "P206", null)), false),

            // Books
            new QuestionType("Who wrote %s?", "Q571", List.of(new QuestionCondition(null, "P50", null)), false),
            new QuestionType("What genre is %s?", "Q571", List.of(new QuestionCondition(null, "P136", null)), false),

            // Films
            new QuestionType("Who directed %s?", "Q11424", List.of(new QuestionCondition(null, "P57", null)), false),
            new QuestionType("Who was a cast member of %s?", "Q11424", List.of(new QuestionCondition(null, "P161", null)), false),
            new QuestionType("Who composed the music for %s?", "Q11424", List.of(new QuestionCondition(null, "P86", null)), false),
            new QuestionType("What genre is %s?", "Q11424", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who was NOT a cast member of %s?", "Q11424", List.of(new QuestionCondition(null, "P161", null)), true),

            // Television series
            new QuestionType("Who was a cast member of %s?", "Q5398426", List.of(new QuestionCondition(null, "P161", null)), false),
            new QuestionType("What genre is %s?", "Q5398426", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who was NOT a cast member of %s?", "Q5398426", List.of(new QuestionCondition(null, "P161", null)), true),
            new QuestionType("Which network originally broadcast %s?", "Q5398426", List.of(new QuestionCondition(null, "P449", null)), false),

            // Songs
            new QuestionType("Who performed %s?", "Q7366", List.of(new QuestionCondition(null, "P175", null)), false),
            new QuestionType("What genre is %s?", "Q7366", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who composed %s?", "Q7366", List.of(new QuestionCondition(null, "P86", null)), false),
            new QuestionType("Who wrote the lyrics for %s?", "Q7366", List.of(new QuestionCondition(null, "P676", null)), false),
            new QuestionType("Who produced %s?", "Q7366", List.of(new QuestionCondition(null, "P162", null)), false),

            // Albums
            new QuestionType("Who performed the album %s?", "Q482994", List.of(new QuestionCondition(null, "P175", null)), false),
            new QuestionType("What genre is the album %s?", "Q482994", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who produced the album %s?", "Q482994", List.of(new QuestionCondition(null, "P162", null)), false),
            new QuestionType("Which genre is NOT associated with the album %s?", "Q482994", List.of(new QuestionCondition(null, "P136", null)), true),
            new QuestionType("Which record label released the album %s?", "Q482994", List.of(new QuestionCondition(null, "P264", null)), false),

            // Companies
            new QuestionType("Who founded %s?", "Q4830453", List.of(new QuestionCondition(null, "P112", null)), false),

            // Universities
            new QuestionType("Which country is %s in?", "Q3918", List.of(new QuestionCondition(null, "P17", null)), false),

            // Famous buildings
            new QuestionType("Which country is %s in?", "Q41176", List.of(new QuestionCondition(null, "P17", null)), false),

            // Mountains
            new QuestionType("Which country is %s in?", "Q8502", List.of(new QuestionCondition(null, "P17", null)), false),
            new QuestionType("Which mountain range is %s part of?", "Q8502", List.of(new QuestionCondition(null, "P4552", null)), false),
            new QuestionType("Which continent is %s in?", "Q8502", List.of(new QuestionCondition(null, "P30", null)), false),

            // Rivers
            new QuestionType("Which country does %s flow through?", "Q4022", List.of(new QuestionCondition(null, "P17", null)), false),
            new QuestionType("Which country does %s NOT flow through?", "Q4022", List.of(new QuestionCondition(null, "P17", null)), true),
            new QuestionType("Where does %s flow into?", "Q4022", List.of(new QuestionCondition(null, "P403", null)), false),

            // Lakes
            new QuestionType("Which country is %s in?", "Q23397", List.of(new QuestionCondition(null, "P17", null)), false),
            new QuestionType("Which river flows into %s?", "Q23397", List.of(new QuestionCondition(null, "P200", null)), false),

            // Airports
            new QuestionType("Which country is %s in?", "Q1248784", List.of(new QuestionCondition(null, "P17", null)), false),

            // Museums
            new QuestionType("Which country is %s in?", "Q33506", List.of(new QuestionCondition(null, "P17", null)), false),

            // Video games
            new QuestionType("Who developed %s?", "Q7889", List.of(new QuestionCondition(null, "P178", null)), false),
            new QuestionType("What genre is %s?", "Q7889", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who published %s?", "Q7889", List.of(new QuestionCondition(null, "P123", null)), false),

            // Planets
            new QuestionType("What is %s named after?", "Q634", List.of(new QuestionCondition(null, "P138", null)), false),
            new QuestionType("Who discovered %s?", "Q634", List.of(new QuestionCondition(null, "P61", null)), false),
            new QuestionType("What astronomical system is %s part of?", "Q634", List.of(new QuestionCondition(null, "P361", null)), false),

            // Chemical elements
            new QuestionType("Who discovered %s?", "Q11344", List.of(new QuestionCondition(null, "P61", null)), false),
            new QuestionType("What is %s named after?", "Q11344", List.of(new QuestionCondition(null, "P138", null)), false),
            new QuestionType("Where was %s discovered?", "Q11344", List.of(new QuestionCondition(null, "P189", null)), false),

            // Natural satellites
            new QuestionType("What astronomical body does %s orbit?", "Q2537", List.of(new QuestionCondition(null, "P397", null)), false),
            new QuestionType("Who discovered %s?", "Q2537", List.of(new QuestionCondition(null, "P61", null)), false),
            new QuestionType("What is %s named after?", "Q2537", List.of(new QuestionCondition(null, "P138", null)), false),

            // Stars
            new QuestionType("Which constellation is %s in?", "Q523", List.of(new QuestionCondition(null, "P59", null)), false),

            // Galaxies
            new QuestionType("Which constellation is %s in?", "Q318", List.of(new QuestionCondition(null, "P59", null)), false)
    );

    private static CompletableFuture<Void> future;

    static {
        future = CompletableFuture.runAsync(QuestionGenerator::addQuestions);
    }

    private record ConditionSelection(QuestionCondition condition, List<String> answerIds, String answerId) {}
    private record QuestionSelection(Item item, QuestionType type, List<ConditionSelection> conditions) {}

    // Numeric questions use the same downloaded Item cache as relationship questions.
    private record NumericRule(String itemType, String property, String most, String least, boolean dated) {}
    private static final List<NumericRule> RULES = List.of(
            new NumericRule("Q6256", "P1082", "Which of these countries has the largest population?", "Which of these countries has the smallest population?", true),
            new NumericRule("Q6256", "P2046", "Which of these countries has the largest area?", "Which of these countries has the smallest area?", false),
            new NumericRule("Q515", "P1082", "Which of these cities has the largest population?", "Which of these cities has the smallest population?", true),
            new NumericRule("Q515", "P2046", "Which of these cities covers the largest area?", "Which of these cities covers the smallest area?", false),
            new NumericRule("Q8502", "P2044", "Which of these mountains has the highest elevation?", "Which of these mountains has the lowest elevation?", false),
            new NumericRule("Q4022", "P2043", "Which of these rivers is the longest?", "Which of these rivers is the shortest?", false),
            new NumericRule("Q4022", "P2053", "Which of these rivers has the largest drainage basin?", "Which of these rivers has the smallest drainage basin?", false),
            new NumericRule("Q23397", "P2046", "Which of these lakes has the largest area?", "Which of these lakes has the smallest area?", false),
            new NumericRule("Q23397", "P2234", "Which of these lakes has the greatest volume?", "Which of these lakes has the smallest volume?", false),
            new NumericRule("Q41176", "P2048", "Which of these buildings is the tallest?", "Which of these buildings is the shortest?", false),
            new NumericRule("Q5", "P2048", "Which of these people is the tallest?", "Which of these people is the shortest?", false),
            new NumericRule("Q1248784", "P2044", "Which of these airports is at the highest elevation?", "Which of these airports is at the lowest elevation?", false),
            new NumericRule("Q11436", "P2050", "Which of these aircraft has the largest wingspan?", "Which of these aircraft has the smallest wingspan?", false)
    );


    private static List<Question> generateNumericQuestions(int count) {
        List<Question> result = new ArrayList<>();
        Map<String, Item> cached = WikidataClient.getItems();
        List<NumericRule> rules = new ArrayList<>(RULES);
        Collections.shuffle(rules);
        Map<NumericRule, List<Item>> pools = new HashMap<>();
        for (NumericRule rule : rules) {
            List<Item> candidates = cached.values().stream()
                    .filter(i -> i.getLabel() != null && !i.getLabel().isBlank() && !i.getLabel().matches("Q\\d+"))
                    .filter(i -> i.getStatements().getOrDefault("P31", List.of()).contains(rule.itemType()))
                    .filter(i -> i.getNumericProperties().containsKey(rule.property()))
                    .filter(i -> Double.isFinite(i.getNumericProperties().get(rule.property()).value()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (rule.dated()) {
                int latest = candidates.stream().map(i -> i.getNumericProperties().get(rule.property()).year())
                        .filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1);
                if (latest < 0) continue;
                candidates.removeIf(i -> { Integer y = i.getNumericProperties().get(rule.property()).year();
                    return y == null || y < latest - 5; });
            }
            if (candidates.size() >= 4) pools.put(rule, candidates);
        }
        if (pools.isEmpty()) return List.of();
        List<NumericRule> available = new ArrayList<>(pools.keySet());
        Set<String> generated = new HashSet<>();
        int attempts = 0;
        while (result.size() < count && attempts++ < Math.max(40, count * 12)) {
            NumericRule rule = available.get(ThreadLocalRandom.current().nextInt(available.size()));
            List<Item> pool = pools.get(rule);
            Collections.shuffle(pool);
            List<Item> four = new ArrayList<>(pool.subList(0, 4));
            if (four.stream().map(Item::getLabel).distinct().count() != 4) continue;
            boolean most = ThreadLocalRandom.current().nextBoolean();
            Comparator<Item> comparator = Comparator.comparingDouble(i -> i.getNumericProperties().get(rule.property()).value());
            Item correct = most ? Collections.max(four, comparator) : Collections.min(four, comparator);
            double answerValue = correct.getNumericProperties().get(rule.property()).value();
            if (four.stream().filter(i -> Double.compare(i.getNumericProperties().get(rule.property()).value(), answerValue) == 0).count() != 1) continue;
            // Population measurements from different years can make near-equal answers ambiguous.
            if (rule.dated()) {
                double nearest = most ? four.stream().filter(i -> i != correct).mapToDouble(i -> i.getNumericProperties().get(rule.property()).value()).max().orElse(answerValue)
                        : four.stream().filter(i -> i != correct).mapToDouble(i -> i.getNumericProperties().get(rule.property()).value()).min().orElse(answerValue);
                if (answerValue <= 0 || nearest <= 0 || Math.abs(answerValue - nearest) / Math.max(answerValue, nearest) < 0.10) continue;
            }
            String combination = rule.itemType() + ":" + rule.property() + ":" + most + ":" +
                    four.stream().map(Item::getId).sorted().collect(Collectors.joining(","));
            if (!generated.add(combination)) continue;
            List<String> options = four.stream().map(Item::getLabel).collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(options);
            result.add(new Question(most ? rule.most() : rule.least(), correct, correct.getLabel(), options,
                    (int) Math.min(Integer.MAX_VALUE, correct.getQrank())));
        }
        return result;
    }


    public static Question pollQuestion() {
        while (bufferedQuestions.isEmpty()) {
            if (future.isDone()) startFilling();
            try { Thread.sleep(100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }
        Question question = bufferedQuestions.remove(ThreadLocalRandom.current().nextInt(bufferedQuestions.size()));
        if (bufferedQuestions.size() < TARGET_BUFFER_SIZE / 2) startFilling();
        return question;
    }

    private static synchronized void startFilling() {
        if (future == null || future.isDone()) future = CompletableFuture.runAsync(QuestionGenerator::addQuestions);
    }

    public static void addQuestions() {
        Logger.addEvent("Trying to find new Questions");
        int failures = 0;
        int batches = 0;
        while (bufferedQuestions.size() < TARGET_BUFFER_SIZE && !Thread.currentThread().isInterrupted()) {
            try {
                // Backfill existing saved items gradually. New items already get numeric data.
                if (batches++ % 10 == 0) WikidataClient.enrichCachedItems(50);
                List<Question> newQuestions = new ArrayList<>(generateNumericQuestions(20));
                int numNumericQuestionsGenerated = newQuestions.size();
                newQuestions.addAll(generateRelationshipQuestions(50));
                int numRelationshipQuestionsGenerated = newQuestions.size() - numNumericQuestionsGenerated;
                if (newQuestions.isEmpty()) {
                    if (++failures >= 25) return;
                    Logger.addEvent("Failed to find any new questions. Failed : " + failures + " times");
                    continue;
                }
                int remaining = TARGET_BUFFER_SIZE - bufferedQuestions.size();
                List<Question> accepted = newQuestions.subList(0, Math.min(remaining, newQuestions.size()));
                bufferedQuestions.addAll(accepted);
                allQuestions.addAll(accepted);
                failures = 0;
                Logger.addEvent("Created " + numNumericQuestionsGenerated + " Numeric Questions");
                Logger.addEvent("Created " + numRelationshipQuestionsGenerated + " Relationship Questions");
                Logger.addEvent("Had " + failures + " failures");
                Logger.addEvent("Created Questions with new size " + bufferedQuestions.size());
            } catch (Exception e) {
                System.err.println("Question generation: " + e.getMessage());
                if (++failures >= 25) return;
            }
        }
    }

    private static List<Question> generateRelationshipQuestions(int batchSize) throws Exception {
        List<String> ids = getRandomQuestionIds(batchSize);
        if (ids.isEmpty()) return List.of();
        List<Item> items = WikidataClient.getItems(ids);
        List<QuestionSelection> selections = new ArrayList<>();
        Map<String, List<String>> possibleAnswers = new HashMap<>();
        for (Item item : items) {
            QuestionSelection selection = selectQuestion(item);
            if (selection == null) continue;
            selections.add(selection);
            for (ConditionSelection condition : selection.conditions()) {
                if (!condition.condition().reverse()) possibleAnswers
                        .computeIfAbsent(condition.condition().property(), k -> new ArrayList<>())
                        .addAll(condition.answerIds());
            }
        }
        List<String> requiredIds = selections.stream().flatMap(s -> s.conditions().stream())
                .flatMap(c -> c.answerIds().stream()).distinct().toList();
        WikidataClient.getItems(requiredIds);
        WikidataClient.addPossibleAnswers(possibleAnswers);
        List<Question> newQuestions = new ArrayList<>();
        for (QuestionSelection selection : selections) {
            Question question = createQuestion(selection);
            if (question != null) newQuestions.add(question);
        }
        return newQuestions;
    }

    private static List<String> getRandomQuestionIds(int batchSize) {
        List<String> eligibleIds = questionIdsByDifficulty.computeIfAbsent(
                difficulty,
                d -> idsToQRank.entrySet().stream()
                        .filter(e -> e.getValue() > d.PAGEVIEWS)
                        .map(Map.Entry::getKey)
                        .toList()
        );

        if (eligibleIds.size() <= batchSize) return new ArrayList<>(eligibleIds);

        Set<String> selectedIds = new LinkedHashSet<>();

        while (selectedIds.size() < batchSize) {
            selectedIds.add(
                    eligibleIds.get(
                            ThreadLocalRandom.current().nextInt(eligibleIds.size())
                    )
            );
        }

        return new ArrayList<>(selectedIds);
    }

    private static QuestionSelection selectQuestion(Item item) throws Exception {
        List<QuestionType> possibleQuestions = new ArrayList<>(
                queries.stream()
                        .filter(q -> worksWith(item, q))
                        .toList()
        );

        Collections.shuffle(possibleQuestions);

        for (QuestionType type : possibleQuestions) {
            List<ConditionSelection> conditions = new ArrayList<>();
            boolean valid = true;

            for (QuestionCondition condition : type.conditions()) {
                List<String> answerIds = getAnswerIds(item, condition);

                if (answerIds.isEmpty()) {
                    valid = false;
                    break;
                }

                String answerId = answerIds.get(
                        ThreadLocalRandom.current().nextInt(answerIds.size())
                );

                conditions.add(
                        new ConditionSelection(
                                condition,
                                answerIds,
                                answerId
                        )
                );
            }

            if (!valid) continue;

            if (type.notTrue() &&
                    conditions.size() == 1 &&
                    conditions.get(0).answerIds().size() < 3) continue;

            if (type.notTrue() &&
                    conditions.size() > 1 &&
                    conditions.size() < 4) continue;

            if (type.notTrue() && conditions.size() > 4) {
                Collections.shuffle(conditions);
                conditions = new ArrayList<>(conditions.subList(0, 4));
            }

            return new QuestionSelection(
                    item,
                    type,
                    conditions
            );
        }

        return null;
    }

    private static List<String> getAnswerIds(Item item, QuestionCondition condition) throws Exception {
        if (condition.reverse()) {
            return WikidataClient.getItems().values().stream()
                    .filter(answer ->
                            condition.answerItemType() == null ||
                                    answer.getStatements()
                                            .getOrDefault("P31", List.of())
                                            .contains(condition.answerItemType()))
                    .filter(answer ->
                            answer.getStatements()
                                    .getOrDefault(condition.property(), List.of())
                                    .contains(item.getId()))
                    .map(Item::getId)
                    .distinct()
                    .toList();
        }

        List<String> answerIds = new ArrayList<>(
                item.getStatements()
                        .getOrDefault(condition.property(), List.of())
        );

        if (answerIds.isEmpty()) return List.of();

        if (condition.answerType() == null) {
            return answerIds;
        }

        List<Item> answerItems = WikidataClient.getItems(answerIds);

        List<String> finalAnswerIds = answerItems.stream()
                .flatMap(answer ->
                        answer.getStatements()
                                .getOrDefault(condition.answerType(), List.of())
                                .stream())
                .distinct()
                .toList();

        return finalAnswerIds;
    }

    private static Question createQuestion(QuestionSelection selection) {
        if (!selection.type().notTrue()) return createNormalQuestion(selection);
        if (selection.conditions().size() == 1) return createSingleConditionNotTrueQuestion(selection);
        return createNotTrueQuestion(selection);
    }

    private static Question createNormalQuestion(QuestionSelection selection) {
        ConditionSelection condition = selection.conditions().get(0);
        Item answerItem = WikidataClient.getItems().get(condition.answerId());

        if (answerItem == null) return null;

        String answer = answerItem.getLabel();

        List<String> options = new ArrayList<>(
                WikidataClient.getSimilarAnswers(
                        condition.condition().property(),
                        condition.answerIds()
                )
        );

        if (options.size() < 3) return null;

        options.add(answer);
        if (new HashSet<>(options).size() != 4) return null;
        Collections.shuffle(options);

        return new Question(
                selection.type().question().formatted(selection.item().getLabel()),
                selection.item(),
                answer,
                options,
                idsToQRank.getOrDefault(selection.item().getId(), 0)
        );
    }

    private static Question createSingleConditionNotTrueQuestion(QuestionSelection selection) {
        ConditionSelection condition = selection.conditions().get(0);
        List<String> actualIds = new ArrayList<>(condition.answerIds());

        if (actualIds.size() < 3) return null;

        Collections.shuffle(actualIds);

        List<String> options = actualIds.stream()
                .limit(3)
                .map(id -> WikidataClient.getItems().get(id))
                .filter(Objects::nonNull)
                .map(Item::getLabel)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        if (options.size() < 3) return null;

        String answer = getFalseAnswer(selection.item(), condition);

        if (answer == null || options.contains(answer)) return null;

        options.add(answer);
        if (new HashSet<>(options).size() != 4) return null;
        Collections.shuffle(options);

        return new Question(
                selection.type().question().formatted(selection.item().getLabel()),
                selection.item(),
                answer,
                options,
                idsToQRank.getOrDefault(selection.item().getId(), 0)
        );
    }

    private static Question createNotTrueQuestion(QuestionSelection selection) {
        List<ConditionSelection> conditions = new ArrayList<>(selection.conditions());
        Collections.shuffle(conditions);

        int falseIndex = -1;
        String falseValue = null;

        for (int i = 0; i < conditions.size(); i++) {
            falseValue = getFalseAnswer(
                    selection.item(),
                    conditions.get(i)
            );

            if (falseValue != null) {
                falseIndex = i;
                break;
            }
        }

        if (falseIndex == -1) return null;

        List<String> options = new ArrayList<>();
        String answer = null;

        for (int i = 0; i < conditions.size(); i++) {
            ConditionSelection condition = conditions.get(i);

            if (condition.condition().statement() == null) return null;

            String value;

            if (i == falseIndex) {
                value = falseValue;
            } else {
                Item answerItem = WikidataClient.getItems().get(condition.answerId());

                if (answerItem == null) return null;

                value = answerItem.getLabel();
            }

            String option = condition.condition()
                    .statement()
                    .formatted(value);

            options.add(option);

            if (i == falseIndex) answer = option;
        }

        if (new HashSet<>(options).size() != 4) return null;
        Collections.shuffle(options);

        return new Question(
                selection.type().question().formatted(selection.item().getLabel()),
                selection.item(),
                answer,
                options,
                idsToQRank.getOrDefault(selection.item().getId(), 0)
        );
    }

    private static String getFalseAnswer(Item item, ConditionSelection condition) {
        if (!condition.condition().reverse()) {
            List<String> answers = WikidataClient.getSimilarAnswers(
                    condition.condition().property(),
                    condition.answerIds()
            );

            return answers.isEmpty() ? null : answers.get(0);
        }

        List<Item> candidates = new ArrayList<>(
                WikidataClient.getItems().values().stream()
                        .filter(answer ->
                                !condition.answerIds().contains(answer.getId()))
                        .filter(answer ->
                                condition.condition().answerItemType() == null ||
                                        answer.getStatements()
                                                .getOrDefault("P31", List.of())
                                                .contains(condition.condition().answerItemType()))
                        .filter(answer ->
                                !answer.getStatements()
                                        .getOrDefault(condition.condition().property(), List.of())
                                        .isEmpty())
                        .filter(answer ->
                                !answer.getStatements()
                                        .getOrDefault(condition.condition().property(), List.of())
                                        .contains(item.getId()))
                        .toList()
        );

        if (candidates.isEmpty()) return null;

        return candidates.get(
                ThreadLocalRandom.current().nextInt(candidates.size())
        ).getLabel();
    }

    private static boolean worksWith(Item item, QuestionType type) {
        if (!item.getStatements()
                .getOrDefault("P31", List.of())
                .contains(type.itemType())) return false;

        return type.conditions().stream().allMatch(condition ->
                condition.reverse() ||
                        !item.getStatements()
                                .getOrDefault(condition.property(), List.of())
                                .isEmpty()
        );
    }

    private static HashMap<String, Integer> getIdsQRank() {
        HashMap<String, Integer> ids = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                QuestionGenerator.class.getResourceAsStream("/qrank.csv")
                        )
                )
        )) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                if (parts.length < 2) continue;

                try {
                    String id = parts[0].trim();
                    int pageViews = Integer.parseInt(parts[1].trim());

                    ids.put(id, pageViews);
                } catch (NumberFormatException ignored) {}
            }

        } catch (IOException e) {
            throw new RuntimeException("Could not read QRank CSV", e);
        }

        return ids;
    }
}