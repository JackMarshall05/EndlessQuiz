package org.example.questiongenerator;

import org.example.Event;
import org.example.Logger;
import org.example.WikidataClient;
import org.example.objects.Item;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class QuestionGenerator {
    public static Difficulty difficulty = Difficulty.EASY;

    public static HashMap<String, Integer> idsToQRank = getIdsQRank();

    private static final CopyOnWriteArrayList<Question> bufferedQuestions = new CopyOnWriteArrayList<>();
    private static final List<Question> allQuestions = new ArrayList<>();
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
            new QuestionType("Which award has %s received?", "Q5", List.of(new QuestionCondition(null, "P166", null)), false),
            new QuestionType("Which award has %s NOT received?", "Q5", List.of(new QuestionCondition(null, "P166", null)), true),
            new QuestionType("Which field is %s known for?", "Q5", List.of(new QuestionCondition(null, "P101", null)), false),
            new QuestionType("Which organisation has %s worked for?", "Q5", List.of(new QuestionCondition(null, "P108", null)), false),

            // Countries
            new QuestionType("What is the capital of %s?", "Q6256", List.of(new QuestionCondition(null, "P36", null)), false),
            new QuestionType("What currency is used in %s?", "Q6256", List.of(new QuestionCondition(null, "P38", null)), false),
            new QuestionType("Which continent is %s in?", "Q6256", List.of(new QuestionCondition(null, "P30", null)), false),
            new QuestionType("What is an official language of %s?", "Q6256", List.of(new QuestionCondition(null, "P37", null)), false),
            new QuestionType("Which country shares a border with %s?", "Q6256", List.of(new QuestionCondition(null, "P47", null)), false),
            new QuestionType("Which country does NOT share a border with %s?", "Q6256", List.of(new QuestionCondition(null, "P47", null)), true),
            new QuestionType("Which is NOT an official language of %s?", "Q6256", List.of(new QuestionCondition(null, "P37", null)), true),
            new QuestionType("Which city is NOT in %s?", "Q6256", List.of(new QuestionCondition(null, "P17", null, true, "Q515")), true),
            new QuestionType("Which of these is NOT true about %s?", "Q6256", List.of(
                    new QuestionCondition("Its capital is %s", "P36", null),
                    new QuestionCondition("Its currency is %s", "P38", null),
                    new QuestionCondition("It is in %s", "P30", null),
                    new QuestionCondition("An official language is %s", "P37", null)
            ), true),

            // Continents
            new QuestionType("Which country is NOT completely or partially on %s?", "Q5107", List.of(new QuestionCondition(null, "P30", null, true, "Q6256")), true),

            // Cities
            new QuestionType("Which country is %s in?", "Q515", List.of(new QuestionCondition(null, "P17", null)), false),
            new QuestionType("Which body of water is %s located beside?", "Q515", List.of(new QuestionCondition(null, "P206", null)), false),

            // Books
            new QuestionType("Who wrote %s?", "Q571", List.of(new QuestionCondition(null, "P50", null)), false),
            new QuestionType("What genre is %s?", "Q571", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Which genre is NOT associated with %s?", "Q571", List.of(new QuestionCondition(null, "P136", null)), true),
            new QuestionType("Who published %s?", "Q571", List.of(new QuestionCondition(null, "P123", null)), false),
            new QuestionType("What language was %s written in?", "Q571", List.of(new QuestionCondition(null, "P407", null)), false),

            // Films
            new QuestionType("Who directed %s?", "Q11424", List.of(new QuestionCondition(null, "P57", null)), false),
            new QuestionType("Who was a cast member of %s?", "Q11424", List.of(new QuestionCondition(null, "P161", null)), false),
            new QuestionType("Who composed the music for %s?", "Q11424", List.of(new QuestionCondition(null, "P86", null)), false),
            new QuestionType("What genre is %s?", "Q11424", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who was NOT a cast member of %s?", "Q11424", List.of(new QuestionCondition(null, "P161", null)), true),
            new QuestionType("Which genre is NOT associated with %s?", "Q11424", List.of(new QuestionCondition(null, "P136", null)), true),
            new QuestionType("Which company produced %s?", "Q11424", List.of(new QuestionCondition(null, "P272", null)), false),
            new QuestionType("Which country is %s from?", "Q11424", List.of(new QuestionCondition(null, "P495", null)), false),
            new QuestionType("What work is %s based on?", "Q11424", List.of(new QuestionCondition(null, "P144", null)), false),
            new QuestionType("What language was %s originally made in?", "Q11424", List.of(new QuestionCondition(null, "P364", null)), false),

            // Television series
            new QuestionType("Who created %s?", "Q5398426", List.of(new QuestionCondition(null, "P170", null)), false),
            new QuestionType("Who was a cast member of %s?", "Q5398426", List.of(new QuestionCondition(null, "P161", null)), false),
            new QuestionType("What genre is %s?", "Q5398426", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who was NOT a cast member of %s?", "Q5398426", List.of(new QuestionCondition(null, "P161", null)), true),
            new QuestionType("Which genre is NOT associated with %s?", "Q5398426", List.of(new QuestionCondition(null, "P136", null)), true),
            new QuestionType("Which company produced %s?", "Q5398426", List.of(new QuestionCondition(null, "P272", null)), false),
            new QuestionType("Which network originally broadcast %s?", "Q5398426", List.of(new QuestionCondition(null, "P449", null)), false),

            // Songs
            new QuestionType("Who performed %s?", "Q7366", List.of(new QuestionCondition(null, "P175", null)), false),
            new QuestionType("What genre is %s?", "Q7366", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who composed %s?", "Q7366", List.of(new QuestionCondition(null, "P86", null)), false),
            new QuestionType("Who wrote the lyrics for %s?", "Q7366", List.of(new QuestionCondition(null, "P676", null)), false),
            new QuestionType("Who produced %s?", "Q7366", List.of(new QuestionCondition(null, "P162", null)), false),
            new QuestionType("Which genre is NOT associated with %s?", "Q7366", List.of(new QuestionCondition(null, "P136", null)), true),
            new QuestionType("Which record label released %s?", "Q7366", List.of(new QuestionCondition(null, "P264", null)), false),

            // Albums
            new QuestionType("Who performed the album %s?", "Q482994", List.of(new QuestionCondition(null, "P175", null)), false),
            new QuestionType("What genre is the album %s?", "Q482994", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Who produced the album %s?", "Q482994", List.of(new QuestionCondition(null, "P162", null)), false),
            new QuestionType("Which genre is NOT associated with the album %s?", "Q482994", List.of(new QuestionCondition(null, "P136", null)), true),
            new QuestionType("Which record label released the album %s?", "Q482994", List.of(new QuestionCondition(null, "P264", null)), false),

            // Companies
            new QuestionType("Who founded %s?", "Q4830453", List.of(new QuestionCondition(null, "P112", null)), false),
            new QuestionType("What industry does %s operate in?", "Q4830453", List.of(new QuestionCondition(null, "P452", null)), false),
            new QuestionType("Which organisation is the parent company of %s?", "Q4830453", List.of(new QuestionCondition(null, "P749", null)), false),

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
            new QuestionType("Which city does %s serve?", "Q1248784", List.of(new QuestionCondition(null, "P931", null)), false),
            new QuestionType("What is %s named after?", "Q1248784", List.of(new QuestionCondition(null, "P138", null)), false),

            // Museums
            new QuestionType("Which country is %s in?", "Q33506", List.of(new QuestionCondition(null, "P17", null)), false),

            // Video games
            new QuestionType("Who developed %s?", "Q7889", List.of(new QuestionCondition(null, "P178", null)), false),
            new QuestionType("What genre is %s?", "Q7889", List.of(new QuestionCondition(null, "P136", null)), false),
            new QuestionType("Which genre is NOT associated with %s?", "Q7889", List.of(new QuestionCondition(null, "P136", null)), true),
            new QuestionType("Who published %s?", "Q7889", List.of(new QuestionCondition(null, "P123", null)), false),
            new QuestionType("Which platform can %s be played on?", "Q7889", List.of(new QuestionCondition(null, "P400", null)), false),

            // Fictional characters
            new QuestionType("Who created %s?", "Q95074", List.of(new QuestionCondition(null, "P170", null)), false),
            new QuestionType("What work does %s appear in?", "Q95074", List.of(new QuestionCondition(null, "P1441", null)), false),
            new QuestionType("Which work does %s NOT appear in?", "Q95074", List.of(new QuestionCondition(null, "P1441", null)), true),

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
            new QuestionType("Which constellation is %s in?", "Q318", List.of(new QuestionCondition(null, "P59", null)), false),

            // Spacecraft
            new QuestionType("Who operates %s?", "Q40218", List.of(new QuestionCondition(null, "P137", null)), false),
            new QuestionType("Who manufactured %s?", "Q40218", List.of(new QuestionCondition(null, "P176", null)), false),
            new QuestionType("What is %s named after?", "Q40218", List.of(new QuestionCondition(null, "P138", null)), false),

            // Constellations
            new QuestionType("Which star is NOT in %s?", "Q8928", List.of(new QuestionCondition(null, "P59", null, true, "Q523")), true),
            new QuestionType("Which galaxy is NOT in %s?", "Q8928", List.of(new QuestionCondition(null, "P59", null, true, "Q318")), true)
    );

    private static CompletableFuture<Void> future;

    static {
        future = CompletableFuture.runAsync(QuestionGenerator::addQuestions);
    }

    private record ConditionSelection(QuestionCondition condition, List<String> answerIds, String answerId) {}
    private record QuestionSelection(Item item, QuestionType type, List<ConditionSelection> conditions) {}

    public static Question pollQuestion() {
        while (bufferedQuestions.isEmpty()) {
            try {Thread.sleep(1000);}
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for a question.");
            }
        }

        Question question = bufferedQuestions.remove((int) (Math.random() * bufferedQuestions.size()));

        if (future.isDone() && bufferedQuestions.size() < TARGET_BUFFER_SIZE) {
            future = CompletableFuture.runAsync(QuestionGenerator::addQuestions);
        }

        return question;
    }

    public static void addQuestions() {
        Logger.addEvent(new Event(System.currentTimeMillis(),"Trying to find new Questions", Thread.currentThread()));

        int batchSize = 50;

        while (bufferedQuestions.size() < TARGET_BUFFER_SIZE) {
            try {
                long start = System.currentTimeMillis();

                List<String> ids = getRandomQuestionIds(batchSize);

                Logger.addEvent(new Event(System.currentTimeMillis(),"Retrieved Question ID's", Thread.currentThread()));

                if (ids.isEmpty()) throw new IllegalStateException("There should always be at least one id");

                List<Item> items = WikidataClient.getItems(ids);

                Logger.addEvent(new Event(System.currentTimeMillis(),"Retrieved Items", Thread.currentThread()));

                List<QuestionSelection> selections = new ArrayList<>();
                Map<String, List<String>> possibleAnswers = new HashMap<>();

                for (Item item : items) {
                    QuestionSelection selection = selectQuestion(item);
                    if (selection == null) continue;

                    selections.add(selection);

                    for (ConditionSelection condition : selection.conditions()) {
                        if (!condition.condition().reverse()) {
                            possibleAnswers
                                    .computeIfAbsent(condition.condition().property(), k -> new ArrayList<>())
                                    .addAll(condition.answerIds());
                        }
                    }
                }

                Logger.addEvent(new Event(System.currentTimeMillis(),"Selected Questions", Thread.currentThread()));

                List<String> requiredAnswerIds = selections.stream()
                        .flatMap(selection -> selection.conditions().stream())
                        .flatMap(condition -> condition.answerIds().stream())
                        .distinct()
                        .toList();

                WikidataClient.getItems(requiredAnswerIds);

                Logger.addEvent(new Event(System.currentTimeMillis(),"Loaded Selected Answers", Thread.currentThread()));

                WikidataClient.addPossibleAnswers(possibleAnswers);

                Logger.addEvent(new Event(System.currentTimeMillis(),"Found Possible Answers", Thread.currentThread()));

                for (QuestionSelection selection : selections) {
                    Question question = createQuestion(selection);

                    if (question != null) {
                        bufferedQuestions.add(question);
                        allQuestions.add(question);
                    }
                }

                Logger.addEvent(new Event(System.currentTimeMillis(),"Created Questions", Thread.currentThread()));

                long end = System.currentTimeMillis();

                System.out.println("It took " + ((double)(end-start)/1000) + " seconds to find new questions");
                System.out.println("New Buffered Questions Size is " + bufferedQuestions.size());
            } catch (Exception e) {
                System.out.println("Error: " + e);
                e.printStackTrace();
            }
        }
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

        try (BufferedReader reader = Files.newBufferedReader(Path.of("qrank.csv"))) {
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