package org.example.questiongenerator;

import org.example.Logger;
import org.example.WikidataClient;
import org.example.objects.Item;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class QuestionGenerator {
    public static Difficulty difficulty = Difficulty.EASY;
    public static final HashMap<String, Integer> idsToQRank = getIdsQRank();
    private static final CopyOnWriteArrayList<Question> bufferedQuestions = new CopyOnWriteArrayList<>();
    private static final int TARGET_BUFFER_SIZE = 20000;
    private static final Map<Difficulty, List<String>> questionIdsByDifficulty = new EnumMap<>(Difficulty.class);

    public enum Difficulty {
        EASY(10000000), MEDIUM(1000000), HARD(400000), VERY_HARD(100000);
        public final int PAGEVIEWS;
        Difficulty(int pageviews) { PAGEVIEWS = pageviews; }
    }

    public static final List<Query> allQueries = List.of(
            // People
            new RelationshipQuery("Which country was %s born in?", "Q5", List.of(new QuestionCondition(null, "P19", "P17")), false),
            new RelationshipQuery("Which sports team has %s played for?", "Q5", List.of(new QuestionCondition(null, "P54", null)), false),
            new RelationshipQuery("Which sports team has %s NOT played for?", "Q5", List.of(new QuestionCondition(null, "P54", null)), true),
            new RelationshipQuery("Which song was NOT performed by %s?", "Q5", List.of(new QuestionCondition(null, "P175", null, true, "Q7366")), true),
            new RelationshipQuery("Which song's lyrics were NOT written by %s?", "Q5", List.of(new QuestionCondition(null, "P676", null, true, "Q7366")), true),
            new RelationshipQuery("Which song was NOT composed by %s?", "Q5", List.of(new QuestionCondition(null, "P86", null, true, "Q7366")), true),
            new RelationshipQuery("Which album was NOT performed by %s?", "Q5", List.of(new QuestionCondition(null, "P175", null, true, "Q482994")), true),
            new RelationshipQuery("Which university did %s attend?", "Q5", List.of(new QuestionCondition(null, "P69", null)), false),
            new RelationshipQuery("Which field is %s known for?", "Q5", List.of(new QuestionCondition(null, "P101", null)), false),

            // Countries
            new RelationshipQuery("What is the capital of %s?", "Q6256", List.of(new QuestionCondition(null, "P36", null)), false),
            new RelationshipQuery("What currency is used in %s?", "Q6256", List.of(new QuestionCondition(null, "P38", null)), false),
            new RelationshipQuery("Which continent is %s in?", "Q6256", List.of(new QuestionCondition(null, "P30", null)), false),
            new RelationshipQuery("What is an official language of %s?", "Q6256", List.of(new QuestionCondition(null, "P37", null)), false),
            new RelationshipQuery("Which country shares a border with %s?", "Q6256", List.of(new QuestionCondition(null, "P47", null)), false),
            new RelationshipQuery("Which country does NOT share a border with %s?", "Q6256", List.of(new QuestionCondition(null, "P47", null)), true),
            new RelationshipQuery("Which is NOT an official language of %s?", "Q6256", List.of(new QuestionCondition(null, "P37", null)), true),
            new RelationshipQuery("Which city is NOT in %s?", "Q6256", List.of(new QuestionCondition(null, "P17", null, true, "Q515")), true),

            // Continents
            new RelationshipQuery("Which country is NOT completely or partially in %s?", "Q5107", List.of(new QuestionCondition(null, "P30", null, true, "Q6256")), true),

            // Cities
            new RelationshipQuery("Which country is %s in?", "Q515", List.of(new QuestionCondition(null, "P17", null)), false),
            new RelationshipQuery("Which body of water is %s located beside?", "Q515", List.of(new QuestionCondition(null, "P206", null)), false),

            // Books
            new RelationshipQuery("Who wrote %s?", "Q571", List.of(new QuestionCondition(null, "P50", null)), false),
            new RelationshipQuery("What genre is %s?", "Q571", List.of(new QuestionCondition(null, "P136", null)), false),

            // Films
            new RelationshipQuery("Who directed %s?", "Q11424", List.of(new QuestionCondition(null, "P57", null)), false),
            new RelationshipQuery("Who was a cast member of %s?", "Q11424", List.of(new QuestionCondition(null, "P161", null)), false),
            new RelationshipQuery("Who composed the music for %s?", "Q11424", List.of(new QuestionCondition(null, "P86", null)), false),
            new RelationshipQuery("What genre is %s?", "Q11424", List.of(new QuestionCondition(null, "P136", null)), false),
            new RelationshipQuery("Who was NOT a cast member of %s?", "Q11424", List.of(new QuestionCondition(null, "P161", null)), true),

            // Television series
            new RelationshipQuery("Who was a cast member of %s?", "Q5398426", List.of(new QuestionCondition(null, "P161", null)), false),
            new RelationshipQuery("What genre is %s?", "Q5398426", List.of(new QuestionCondition(null, "P136", null)), false),
            new RelationshipQuery("Who was NOT a cast member of %s?", "Q5398426", List.of(new QuestionCondition(null, "P161", null)), true),
            new RelationshipQuery("Which network originally broadcast %s?", "Q5398426", List.of(new QuestionCondition(null, "P449", null)), false),

            // Songs
            new RelationshipQuery("Who performed %s?", "Q7366", List.of(new QuestionCondition(null, "P175", null)), false),
            new RelationshipQuery("What genre is %s?", "Q7366", List.of(new QuestionCondition(null, "P136", null)), false),
            new RelationshipQuery("Who composed %s?", "Q7366", List.of(new QuestionCondition(null, "P86", null)), false),
            new RelationshipQuery("Who wrote the lyrics for %s?", "Q7366", List.of(new QuestionCondition(null, "P676", null)), false),
            new RelationshipQuery("Who produced %s?", "Q7366", List.of(new QuestionCondition(null, "P162", null)), false),

            // Albums
            new RelationshipQuery("Who performed the album %s?", "Q482994", List.of(new QuestionCondition(null, "P175", null)), false),
            new RelationshipQuery("What genre is the album %s?", "Q482994", List.of(new QuestionCondition(null, "P136", null)), false),
            new RelationshipQuery("Who produced the album %s?", "Q482994", List.of(new QuestionCondition(null, "P162", null)), false),
            new RelationshipQuery("Which genre is NOT associated with the album %s?", "Q482994", List.of(new QuestionCondition(null, "P136", null)), true),
            new RelationshipQuery("Which record label released the album %s?", "Q482994", List.of(new QuestionCondition(null, "P264", null)), false),

            // Companies
            new RelationshipQuery("Who founded %s?", "Q4830453", List.of(new QuestionCondition(null, "P112", null)), false),

            // Universities
            new RelationshipQuery("Which country is %s in?", "Q3918", List.of(new QuestionCondition(null, "P17", null)), false),

            // Famous buildings
            new RelationshipQuery("Which country is %s in?", "Q41176", List.of(new QuestionCondition(null, "P17", null)), false),

            // Mountains
            new RelationshipQuery("Which country is %s in?", "Q8502", List.of(new QuestionCondition(null, "P17", null)), false),
            new RelationshipQuery("Which mountain range is %s part of?", "Q8502", List.of(new QuestionCondition(null, "P4552", null)), false),
            new RelationshipQuery("Which continent is %s in?", "Q8502", List.of(new QuestionCondition(null, "P30", null)), false),

            // Rivers
            new RelationshipQuery("Which country does %s flow through?", "Q4022", List.of(new QuestionCondition(null, "P17", null)), false),
            new RelationshipQuery("Which country does %s NOT flow through?", "Q4022", List.of(new QuestionCondition(null, "P17", null)), true),
            new RelationshipQuery("Where does %s flow into?", "Q4022", List.of(new QuestionCondition(null, "P403", null)), false),

            // Lakes
            new RelationshipQuery("Which country is %s in?", "Q23397", List.of(new QuestionCondition(null, "P17", null)), false),
            new RelationshipQuery("Which river flows into %s?", "Q23397", List.of(new QuestionCondition(null, "P200", null)), false),

            // Airports
            new RelationshipQuery("Which country is %s in?", "Q1248784", List.of(new QuestionCondition(null, "P17", null)), false),

            // Museums
            new RelationshipQuery("Which country is %s in?", "Q33506", List.of(new QuestionCondition(null, "P17", null)), false),

            // Video games
            new RelationshipQuery("Who developed %s?", "Q7889", List.of(new QuestionCondition(null, "P178", null)), false),
            new RelationshipQuery("What genre is %s?", "Q7889", List.of(new QuestionCondition(null, "P136", null)), false),
            new RelationshipQuery("Who published %s?", "Q7889", List.of(new QuestionCondition(null, "P123", null)), false),

            // Planets
            new RelationshipQuery("What is %s named after?", "Q634", List.of(new QuestionCondition(null, "P138", null)), false),
            new RelationshipQuery("Who discovered %s?", "Q634", List.of(new QuestionCondition(null, "P61", null)), false),
            new RelationshipQuery("What astronomical system is %s part of?", "Q634", List.of(new QuestionCondition(null, "P361", null)), false),

            // Chemical elements
            new RelationshipQuery("Who discovered %s?", "Q11344", List.of(new QuestionCondition(null, "P61", null)), false),
            new RelationshipQuery("What is %s named after?", "Q11344", List.of(new QuestionCondition(null, "P138", null)), false),
            new RelationshipQuery("Where was %s discovered?", "Q11344", List.of(new QuestionCondition(null, "P189", null)), false),

            // Natural satellites
            new RelationshipQuery("What astronomical body does %s orbit?", "Q2537", List.of(new QuestionCondition(null, "P397", null)), false),
            new RelationshipQuery("Who discovered %s?", "Q2537", List.of(new QuestionCondition(null, "P61", null)), false),
            new RelationshipQuery("What is %s named after?", "Q2537", List.of(new QuestionCondition(null, "P138", null)), false),

            // Stars
            new RelationshipQuery("Which constellation is %s in?", "Q523", List.of(new QuestionCondition(null, "P59", null)), false),

            // Galaxies
            new RelationshipQuery("Which constellation is %s in?", "Q318", List.of(new QuestionCondition(null, "P59", null)), false),

            new NumericQuery("Q6256", "P1082", "Which of these countries has the largest population?", "Which of these countries has the smallest population?", true),
            new NumericQuery("Q6256", "P2046", "Which of these countries has the largest area?", "Which of these countries has the smallest area?", false),
            new NumericQuery("Q515", "P1082", "Which of these cities has the largest population?", "Which of these cities has the smallest population?", true),
            new NumericQuery("Q515", "P2046", "Which of these cities covers the largest area?", "Which of these cities covers the smallest area?", false),
            new NumericQuery("Q8502", "P2044", "Which of these mountains has the highest elevation?", "Which of these mountains has the lowest elevation?", false),
            new NumericQuery("Q4022", "P2043", "Which of these rivers is the longest?", "Which of these rivers is the shortest?", false),
            new NumericQuery("Q4022", "P2053", "Which of these rivers has the largest drainage basin?", "Which of these rivers has the smallest drainage basin?", false),
            new NumericQuery("Q23397", "P2046", "Which of these lakes has the largest area?", "Which of these lakes has the smallest area?", false),
            new NumericQuery("Q23397", "P2234", "Which of these lakes has the greatest volume?", "Which of these lakes has the smallest volume?", false),
            new NumericQuery("Q41176", "P2048", "Which of these buildings is the tallest?", "Which of these buildings is the shortest?", false),
            new NumericQuery("Q5", "P2048", "Which of these people is the tallest?", "Which of these people is the shortest?", false),
            new NumericQuery("Q1248784", "P2044", "Which of these airports is at the highest elevation?", "Which of these airports is at the lowest elevation?", false),
            new NumericQuery("Q11436", "P2050", "Which of these aircraft has the largest wingspan?", "Which of these aircraft has the smallest wingspan?", false),

            // Image questions
            new ImageQuery("Which person is shown in this image?", "Q5"),
            new ImageQuery("Which mountain is shown in this image?", "Q8502"),
            new ImageQuery("Which building is shown in this image?", "Q41176"),
            new ImageQuery("Which planet is shown in this image?", "Q634")
    );
    private static CompletableFuture<Void> future;

    static { future = CompletableFuture.runAsync(QuestionGenerator::addQuestions); }

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
                if (batches++ % 10 == 0) WikidataClient.enrichCachedItems(50);
                List<Item> subjects = WikidataClient.getItems(getRandomQuestionIds(50));
                Query.Context context = new Query.Context(subjects, new ArrayList<>(WikidataClient.getItems().values()));
                List<Query> shuffled = new ArrayList<>(allQueries);
                Collections.shuffle(shuffled);
                List<Question> results = new ArrayList<>();
                Map<String, Integer> counts = new HashMap<>();
                int errors = 0;
                for (Query query : shuffled) {
                    if (results.size() >= 50) break;
                    try {
                        List<Question> made = query.generate(context, Math.min(3, 50 - results.size()));
                        results.addAll(made);
                        counts.merge(query.getClass().getSimpleName(), made.size(), Integer::sum);
                    } catch (Exception e) {
                        errors++;
                        System.err.println(query.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
                if (results.isEmpty()) {
                    if (++failures >= 25) return;
                    Logger.addEvent("No questions generated; consecutive empty batches = " + failures);
                    continue;
                }
                int remaining = TARGET_BUFFER_SIZE - bufferedQuestions.size();
                bufferedQuestions.addAll(results.subList(0, Math.min(remaining, results.size())));
                failures = 0;
                Logger.addEvent("Generated by Query: " + counts + "; query errors: " + errors);
                Logger.addEvent("Created Questions with new size " + bufferedQuestions.size());
            } catch (Exception e) {
                if (++failures >= 25) return;
                System.err.println("Question generation: " + e.getMessage());
            }
        }
    }

    private static List<String> getRandomQuestionIds(int batchSize) {
        List<String> eligible = questionIdsByDifficulty.computeIfAbsent(difficulty,
                d -> idsToQRank.entrySet().stream().filter(e -> e.getValue() > d.PAGEVIEWS)
                        .map(Map.Entry::getKey).toList());
        if (eligible.size() <= batchSize) return new ArrayList<>(eligible);
        Set<String> selected = new LinkedHashSet<>();
        while (selected.size() < batchSize)
            selected.add(eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())));
        return new ArrayList<>(selected);
    }

    private static HashMap<String, Integer> getIdsQRank() {
        HashMap<String, Integer> ids = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(QuestionGenerator.class.getResourceAsStream("/qrank.csv"))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                try { ids.put(parts[0].trim(), Integer.parseInt(parts[1].trim())); }
                catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) { throw new RuntimeException("Could not read QRank CSV", e); }
        return ids;
    }
}
