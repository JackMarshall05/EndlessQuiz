package org.example.saveddata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.Logger;
import org.example.objects.Item;

import java.io.File;
import java.util.*;

public class SavedItemData extends SavedData {
    private static final File FILE = new File("src/main/resources/items.json");
    private static final Map<String, Item> items = loadItems();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.addEvent("Running Shutdown Hook");
            saveItems();
        }));
    }

    /**
     * Loads stored items when the application starts.
     */
    private static Map<String, Item> loadItems() {
        Logger.addEvent("Retrieving File Loading Items");

        if (!FILE.exists()) {throw new IllegalStateException("Item File doesn't exist");}

        Logger.addEvent("Started Loading");

        Map<String, Item> map;
        try {
            map = mapper.readValue(FILE, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not load items.json: " + e.getMessage());
        }

        Logger.addEvent("Loaded Items");

        return map;
    }

    /**
     * Saves the current items to items.json.
     */
    private static synchronized void saveItems() {
        if (unsavedCount == 0) return;
        Logger.addEvent("Saving " + unsavedCount + " New Items");
        try {
            mapper.writeValue(FILE, items);
            unsavedCount = 0;
        } catch (Exception e) {
            System.out.println("Could not save items.json: " + e.getMessage());
        }
        Logger.addEvent("New Items Size is now " + items.size());
    }

    /**
     * Returns all currently cached items.
     */
    public static Map<String, Item> getItems() {
        return new HashMap<>(items);
    }

    /**
     * Adds items directly to the cache and saves them.
     */
    public static void addItems(Map<String, Item> newItems) {
        items.putAll(newItems);
        unsavedCount += newItems.size();

        if (unsavedCount >= 50) saveItems();
    }
}