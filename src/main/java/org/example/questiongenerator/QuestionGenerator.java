package org.example.questiongenerator;

import org.example.SavedData;
import org.example.WikidataClient;
import org.example.objects.Item;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class QuestionGenerator {
    public static Difficulty difficulty = Difficulty.EASY;

    public static HashMap<String, Integer> idsToQRank = getIdsQRank(difficulty);

    private static final CopyOnWriteArrayList<Question> bufferedQuestions = new CopyOnWriteArrayList<>();

    private static final List<Question> allQuestions = new ArrayList<>();

    private static final int TARGET_BUFFER_SIZE = 20000;

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
            new QuestionType("Where was %s born?", "Q5", "P19"),
            new QuestionType("Where did %s die?", "Q5", "P20"),
            new QuestionType("When was %s born?", "Q5", "P569"),
            new QuestionType("When did %s die?", "Q5", "P570"),
            new QuestionType("What was the cause of death of %s?", "Q5", "P509"),
            new QuestionType("What was %s's occupation?", "Q5", "P106"),
            new QuestionType("What was %s's field of work?", "Q5", "P101"),
            new QuestionType("What award did %s receive?", "Q5", "P166"),

            // Sport
            new QuestionType("What sport did %s participate in?", "Q5", "P641"),
            new QuestionType("Which sports team did %s play for?", "Q5", "P54"),
            new QuestionType("Which country did %s represent in sport?", "Q5", "P1532"),

            // Countries
            new QuestionType("What is the capital of %s?", "Q6256", "P36"),
            new QuestionType("What currency is used in %s?", "Q6256", "P38"),
            new QuestionType("Which continent is %s in?", "Q6256", "P30"),
            new QuestionType("What is an official language of %s?", "Q6256", "P37"),

            // Cities
            new QuestionType("Which country is %s in?", "Q515", "P17"),
            new QuestionType("When was %s established?", "Q515", "P571"),

            // Books
            new QuestionType("Who wrote %s?", "Q571", "P50"),
            new QuestionType("When was %s published?", "Q571", "P577"),
            new QuestionType("What genre is %s?", "Q571", "P136"),
            new QuestionType("Who published %s?", "Q571", "P123"),

            // Films
            new QuestionType("Who directed %s?", "Q11424", "P57"),
            new QuestionType("Who was a cast member of %s?", "Q11424", "P161"),
            new QuestionType("Who wrote the screenplay for %s?", "Q11424", "P58"),
            new QuestionType("Who composed the music for %s?", "Q11424", "P86"),
            new QuestionType("What genre is %s?", "Q11424", "P136"),
            new QuestionType("Which country produced %s?", "Q11424", "P495"),
            new QuestionType("When was %s released?", "Q11424", "P577"),

            // Television series
            new QuestionType("Who created %s?", "Q5398426", "P170"),
            new QuestionType("What genre is %s?", "Q5398426", "P136"),
            new QuestionType("Which country is %s from?", "Q5398426", "P495"),

            // Songs
            new QuestionType("Who performed %s?", "Q7366", "P175"),
            new QuestionType("Who wrote %s?", "Q7366", "P676"),
            new QuestionType("What genre is %s?", "Q7366", "P136"),
            new QuestionType("Which record label released %s?", "Q7366", "P264"),
            new QuestionType("When was %s released?", "Q7366", "P577"),

            // Albums
            new QuestionType("Who performed the album %s?", "Q482994", "P175"),
            new QuestionType("What genre is the album %s?", "Q482994", "P136"),
            new QuestionType("Which record label released %s?", "Q482994", "P264"),
            new QuestionType("When was the album %s released?", "Q482994", "P577"),

            // Companies
            new QuestionType("Who founded %s?", "Q4830453", "P112"),
            new QuestionType("When was %s founded?", "Q4830453", "P571"),
            new QuestionType("What industry is %s part of?", "Q4830453", "P452"),

            // Universities
            new QuestionType("Which country is %s in?", "Q3918", "P17"),
            new QuestionType("Where is %s located?", "Q3918", "P131"),
            new QuestionType("When was %s founded?", "Q3918", "P571"),

            // Buildings
            new QuestionType("Which country is %s in?", "Q41176", "P17"),
            new QuestionType("Where is %s located?", "Q41176", "P131"),
            new QuestionType("Who designed %s?", "Q41176", "P84"),
            new QuestionType("When was %s built or established?", "Q41176", "P571"),

            // Mountains
            new QuestionType("Which country is %s in?", "Q8502", "P17"),
            new QuestionType("Which mountain range is %s part of?", "Q8502", "P4552")
    );

    private static CompletableFuture<Void> future;

    static {
        future = CompletableFuture.runAsync(
                QuestionGenerator::addQuestions
        );
    }

    /**
     * Returns the next buffered question.
     */
    public static Question pollQuestion() {
        while (bufferedQuestions.isEmpty()) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for a question.");
            }
        }

        int randomIndex = (int) (Math.random() * bufferedQuestions.size());
        Question question = bufferedQuestions.remove(randomIndex);

        if (future.isDone() && bufferedQuestions.size() < TARGET_BUFFER_SIZE) {
            future = CompletableFuture.runAsync(QuestionGenerator::addQuestions);
        }

        return question;
    }

    /**
     * Fills the question buffer.
     */
    public static void addQuestions() {
        System.out.println("Trying to find new Questions");

        int batchSize = 50;

        while (bufferedQuestions.size() < TARGET_BUFFER_SIZE) {
            try {
                long start = System.currentTimeMillis();

                List<String> ids = idsToQRank.keySet().stream().limit(batchSize).toList();

                // No more IDs available.
                if (ids.isEmpty()) break;

                List<Item> items = WikidataClient.getItems(ids);

                // Store labels of the question items themselves.
                Map<String, String> mappedIds = new HashMap<>();
                for (Item item : items) mappedIds.put(item.getId(), item.getLabel());
                SavedData.addLabels(mappedIds);

                /*
                 * Collect answers only for properties that we actually ask
                 * questions about.
                 */
                Set<String> questionProperties = new HashSet<>();
                for (QuestionType query : queries) questionProperties.add(query.property());

                Map<String, List<String>> possibleAnswers = new HashMap<>();
                Set<String> answerIds = new HashSet<>();

                for (Item item : items) {
                    for (Map.Entry<String, List<String>> entry : item.getStatements().entrySet()) {
                        if (!questionProperties.contains(entry.getKey())) continue;
                        possibleAnswers.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                        answerIds.addAll(entry.getValue());
                    }
                }

                // Make these answers available as distractors.
                WikidataClient.addPossibleAnswers(possibleAnswers);

                // Get English labels for all answer Q IDs.
                WikidataClient.requestLabels(answerIds);

                for (Item item : items) {
                    Question question = createQuestion(item);
                    if (question != null) {
                        bufferedQuestions.add(question);
                        allQuestions.add(question);
                    }
                }

                long end = System.currentTimeMillis();

                System.out.println("It took " + ((double)(end-start)/1000) + " seconds to find new questions");
                System.out.println("New Buffered Questions Size is " + bufferedQuestions.size());
            } catch (Exception e) {
                System.out.println("Error: " + e);
                e.printStackTrace();
            }
        }
    }

    private static Question createQuestion(Item item) {
        List<QuestionType> possibleQuestions = queries.stream().filter(q -> worksWith(item, q)).toList();

        if (possibleQuestions.isEmpty()) return null;

        QuestionType type = possibleQuestions.get((int) (Math.random() * possibleQuestions.size()));

        List<String> answerIds = item.getStatements().get(type.property());
        String answerId = answerIds.get((int) (Math.random() * answerIds.size()));

        String answer = WikidataClient.getLabels().getOrDefault(answerId, answerId);

        List<String> options = new ArrayList<>(WikidataClient.getSimilarAnswers(type.property(), item));

        // Don't make a multiple-choice question unless we have enough alternatives.
        if (options.size() < 3) return null;

        options.add(answer);
        Collections.shuffle(options);

        return new Question(
                type.question().formatted(item.getLabel()),
                item,
                answer,
                options,
                idsToQRank.getOrDefault(item.getId(), 0)
        );
    }

    private static boolean worksWith(Item item, QuestionType type) {
        List<String> itemTypes = item.getStatements().getOrDefault("P31", List.of());
        List<String> answers = item.getStatements().getOrDefault(type.property(), List.of());

        return itemTypes.contains(type.itemType()) && !answers.isEmpty();
    }

    private static HashMap<String, Integer> getIdsQRank(Difficulty d) {
        HashMap<String, Integer> ids = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(Path.of("qrank.csv"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                try {
                    String id = parts[0].trim();
                    int pageViews = Integer.parseInt(parts[1].trim());
                    if (pageViews > d.PAGEVIEWS) {ids.put(id, pageViews);}
                } catch (NumberFormatException ignored) {
                    // Skips the header or malformed rows
                }
            }

        } catch (IOException e) {throw new RuntimeException("Could not read QRank CSV", e);}

        return ids;
    }
}