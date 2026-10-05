package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.objects.Item;

import java.io.File;
import java.util.*;

public class SavedData {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final File ITEM_FILE = new File("items.json");
    private static final Map<String, Item> items = loadItems();
    private static int unsavedItems = 0;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Running Shutdown Hook");
            saveItems();
        }));
    }

    /**
     * Loads stored items when the application starts.
     */
    private static Map<String, Item> loadItems() {
        Logger.addEvent(new Event(System.currentTimeMillis(),"Starting Loading Items", Thread.currentThread()));

        if (!ITEM_FILE.exists()) {throw new IllegalStateException("Item File doesn't exist");}

        Map<String, Item> map = new HashMap<>();
        try {
            map = mapper.readValue(ITEM_FILE, new TypeReference<Map<String, Item>>() {});
        } catch (Exception e) {
            System.out.println("Could not load items.json: " + e.getMessage());
        }

        Logger.addEvent(new Event(System.currentTimeMillis(),"Loaded Items", Thread.currentThread()));

        return map;
    }

    /**
     * Saves the current items to items.json.
     */
    private static synchronized void saveItems() {
        if (unsavedItems == 0) return;

        try {
            mapper.writeValue(ITEM_FILE, items);
            unsavedItems = 0;
        } catch (Exception e) {
            System.out.println("Could not save items.json: " + e.getMessage());
        }
        System.out.println("New Items Size is now " + items.size());
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
        unsavedItems += newItems.size();

        if (unsavedItems >= 50) saveItems();
    }
}