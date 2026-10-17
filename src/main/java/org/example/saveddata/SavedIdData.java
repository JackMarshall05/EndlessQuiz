package org.example.saveddata;

import org.example.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class SavedIdData implements SavedData<Map<String, Long>> {

    private static final Path FILE = Path.of(
            "src/main/resources/idRequestFrequency.csv"
    );

    private final Map<String, Long> idRequestFrequency = new HashMap<>();
    private boolean unsavedChanges = false;

    public SavedIdData(){loadData();}

    @Override
    public synchronized void loadData() {
        Logger.addEvent("Trying To Load Id's");

        Map<String, Long> loaded = new HashMap<>();

        if (Files.exists(FILE)) {
            try (BufferedReader reader = Files.newBufferedReader(FILE)) {
                String line;

                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",");

                    if (values.length != 2 ||
                            values[0].trim().equals("id")) {
                        continue;
                    }

                    loaded.put(
                            values[0].trim(),
                            Long.parseLong(values[1].trim())
                    );
                }

            } catch (IOException | NumberFormatException e) {
                throw new IllegalStateException(
                        "Could not load ID request frequencies", e
                );
            }
        }

        idRequestFrequency.clear();
        idRequestFrequency.putAll(loaded);
        unsavedChanges = false;

        Logger.addEvent("Finished Loading Id's");
    }

    @Override
    public synchronized void recordData(Map<String, Long> data) {
        if (data == null || data.isEmpty()) return;

        data.forEach((id, amount) -> {
            if (id == null || amount == null || amount <= 0) {
                throw new IllegalArgumentException(
                        "Invalid ID request frequency"
                );
            }
        });

        data.forEach((id, amount) ->
                idRequestFrequency.merge(id, amount, Long::sum)
        );

        unsavedChanges = true;
    }

    @Override
    public synchronized void saveData() {
        if (!unsavedChanges) return;

        Path tempFile = FILE.resolveSibling(
                FILE.getFileName() + ".tmp"
        );

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
                writer.write("id,amount");
                writer.newLine();

                var sorted = idRequestFrequency.entrySet().stream()
                        .sorted(
                                Map.Entry.<String, Long>comparingByValue()
                                        .reversed()
                                        .thenComparing(Map.Entry.comparingByKey())
                        )
                        .toList();

                for (Map.Entry<String, Long> entry : sorted) {
                    writer.write(
                            entry.getKey() + "," + entry.getValue()
                    );
                    writer.newLine();
                }
            }

            try {
                Files.move(
                        tempFile,
                        FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(
                        tempFile,
                        FILE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            unsavedChanges = false;
            Logger.addEvent("Saved ID Request Frequencies");

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save ID request frequencies", e
            );
        }
    }

    @Override
    public synchronized Map<String, Long> getData() {
        return new HashMap<>(idRequestFrequency);
    }
}