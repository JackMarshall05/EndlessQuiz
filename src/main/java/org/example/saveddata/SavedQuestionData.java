package org.example.saveddata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.Logger;
import org.example.questiongenerator.Question;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SavedQuestionData implements SavedData<List<Question>> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final File FILE = new File("src/main/resources/questions.json");

    private final List<Question> questions = new ArrayList<>();
    private int unsavedCount = 0;

    public SavedQuestionData(){loadData();}

    @Override
    public synchronized void loadData() {
        Logger.addEvent("Retrieving File Loading Questions");

        if (!FILE.exists()) {
            throw new IllegalStateException("Question File doesn't exist");
        }

        try {
            List<Question> loaded = mapper.readValue(
                    FILE,
                    new TypeReference<List<Question>>() {}
            );

            if (loaded == null) {
                throw new IllegalStateException("questions.json is empty");
            }

            questions.clear();
            questions.addAll(loaded);
            unsavedCount = 0;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not load questions.json", e
            );
        }

        Logger.addEvent("Loaded Questions");
    }

    @Override
    public synchronized void recordData(List<Question> data) {
        if (data == null || data.isEmpty()) return;
        questions.addAll(data);
        unsavedCount += data.size();
    }

    @Override
    public synchronized void saveData() {
        if (unsavedCount == 0) return;
        Logger.addEvent("Saving New Questions");
        try {
            mapper.writeValue(FILE, questions);
            unsavedCount = 0;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not save questions.json", e
            );
        }
        Logger.addEvent("New Questions Size is now " + questions.size());
    }

    @Override
    public synchronized List<Question> getData() {
        return new ArrayList<>(questions);
    }

    public synchronized int getUnsavedCount() {
        return unsavedCount;
    }
}