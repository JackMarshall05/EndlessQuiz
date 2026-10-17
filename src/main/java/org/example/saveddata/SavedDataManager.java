package org.example.saveddata;

import org.example.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SavedDataManager {
    public static final SavedItemData SAVED_ITEM_DATA = new SavedItemData();
    public static final SavedQuestionData SAVED_QUESTION_DATA = new SavedQuestionData();
    public static final SavedIdData SAVED_ID_DATA = new SavedIdData();

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "Saved Data Timer");
                thread.setDaemon(true);
                return thread;
            });

    static {
        scheduler.scheduleAtFixedRate(
                SavedDataManager::saveAllData,
                1,
                1,
                TimeUnit.MINUTES
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.addEvent("Running Saved Data Shutdown Hook");
            scheduler.shutdown();
            saveAllData();
        }, "Saved Data Shutdown Hook"));
    }

    public static void saveAllData() {
        saveSafely("ID request frequencies", SAVED_ID_DATA);
        saveSafely("questions", SAVED_QUESTION_DATA);
        saveSafely("items", SAVED_ITEM_DATA);
    }

    private static void saveSafely(String name, SavedData<?> savedData) {
        try {
            savedData.saveData();
        } catch (Exception e) {
            System.err.println("Could not save " + name + ": " + e.getMessage());
        }
    }
}