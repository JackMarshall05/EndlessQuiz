package org.example.saveddata;

import com.fasterxml.jackson.core.type.TypeReference;
import org.example.Logger;
import org.example.objects.Item;
import org.example.questiongenerator.Question;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SavedQuestionData extends SavedData {
    private static final File FILE = new File("src/main/resources/questions.json");
    private static final List<Question> questions = loadQuestions();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.addEvent("Running Shutdown Hook");
            saveQuestions();
        }));
    }

    /**
     * Loads stored items when the application starts.
     */
    private static List<Question> loadQuestions() {
        Logger.addEvent("Retrieving File Loading Questions");

        if (!FILE.exists()) {
            throw new IllegalStateException("Question File doesn't exist");
        }

        Logger.addEvent("Started Loading");

        List<Question> list = new ArrayList<Question>();
        try {
            list = mapper.readValue(FILE, new TypeReference<>() {
            });
        } catch (Exception e) {
            //throw new IllegalStateException("Could not load question.json: " + e.getMessage());
        }

        Logger.addEvent("Loaded Questions");

        return list;
    }

    /**
     * Saves the current items to items.json.
     */
    private static synchronized void saveQuestions() {
        Logger.addEvent("Saving New Questions");
        try {
            mapper.writeValue(FILE, questions);
            unsavedCount = 0;
        } catch (Exception e) {
            System.out.println("Could not save questions.json: " + e.getMessage());
        }
        Logger.addEvent("New Questions Size is now " + questions.size());
    }

    /**
     * Returns all currently cached items.
     */
    public static List<Question> getQuestions() {
        return new ArrayList<>(questions);
    }

    /**
     * Adds items directly to the cache and saves them.
     */
    public static void addQuestions(List<Question> newQuestions) {
        questions.addAll(newQuestions);
        unsavedCount += newQuestions.size();
        if (unsavedCount >= 50) saveQuestions();
    }
}