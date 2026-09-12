package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

public class SavedData {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final File LABEL_FILE = new File("labels.json");
    private static final Map<String, String> labels = loadLabels();

    /**
     * Loads stored labels when the application starts.
     */
    private static Map<String, String> loadLabels() {
        if (!LABEL_FILE.exists()) {return new HashMap<>();}

        try {
            return mapper.readValue(LABEL_FILE, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            System.out.println("Could not load labels.json: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Saves the current labels to labels.json.
     */
    private static void saveLabels() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(LABEL_FILE, labels);
        } catch (Exception e) {
            System.out.println("Could not save labels.json: " + e.getMessage());
        }
    }

    /**
     * Returns all currently cached labels.
     */
    public static Map<String, String> getLabels() {
        return new HashMap<>(labels);
    }

    /**
     * Adds labels directly to the cache and saves them.
     */
    public static void addLabels(Map<String, String> newLabels) {
        labels.putAll(newLabels);
        saveLabels();
    }
}