package org.example.saveddata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.Logger;
import org.example.objects.Item;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SavedItemData implements SavedData<Map<String, Item>> {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final File FILE = new File("src/main/resources/items.json");

    private final Map<String, Item> items = new HashMap<>();
    private int unsavedCount = 0;

    public SavedItemData(){loadData();}

    @Override
    public synchronized void loadData() {
        Logger.addEvent("Trying to Load Items");

        if (!FILE.exists()) {
            throw new IllegalStateException("Item File doesn't exist");
        }

        try {
            Map<String, Item> loaded = mapper.readValue(
                    FILE,
                    new TypeReference<Map<String, Item>>() {}
            );

            if (loaded == null) {
                throw new IllegalStateException("items.json is empty");
            }

            items.clear();
            items.putAll(loaded);
            unsavedCount = 0;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not load items.json", e
            );
        }

        Logger.addEvent("Loaded Items");
    }

    @Override
    public synchronized void recordData(Map<String, Item> data) {
        if (data == null || data.isEmpty()) return;

        items.putAll(data);
        unsavedCount += data.size();
    }

    @Override
    public synchronized void saveData() {
        Logger.addEvent("Saving " + unsavedCount + " New Items. Current size = " + items.size());

        if (unsavedCount == 0) return;

        try {
            mapper.writeValue(FILE, items);
            unsavedCount = 0;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not save items.json", e
            );
        }

        Logger.addEvent("New Items Size is now " + items.size());
    }

    @Override
    public synchronized Map<String, Item> getData() {
        return new HashMap<>(items);
    }

    public synchronized int getUnsavedCount() {
        return unsavedCount;
    }
}