package org.example.saveddata;

import org.example.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SaveIdData extends SavedData{

    private static final Path FILE = Path.of("src/main/resources/idRequestFrequency.csv");

    private static final Map<String, Integer> idRequestFrequency = loadIdRequestFrequency();

    private static boolean unsavedChanges = false;

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "ID Frequency Saver");
                thread.setDaemon(true);
                return thread;
            });

    static {
        scheduler.scheduleAtFixedRate(
                SaveIdData::saveIdRequestFrequency,
                1,
                1,
                TimeUnit.MINUTES
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.addEvent("Saving ID Request Frequencies on Shutdown");
            scheduler.shutdown();
            saveIdRequestFrequency();
        }));
    }

    /**
     * Loads stored ID request frequencies when the application starts.
     */
    private static Map<String, Integer> loadIdRequestFrequency() {
        Map<String, Integer> frequencies = new HashMap<>();

        if (!Files.exists(FILE)) {
            return frequencies;
        }

        try (BufferedReader reader = Files.newBufferedReader(FILE)) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");

                if (values.length != 2 || values[0].equals("id")) {
                    continue;
                }

                String id = values[0].trim();
                int amount = Integer.parseInt(values[1].trim());

                frequencies.put(id, amount);
            }

        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException(
                    "Could not load ID request frequencies", e
            );
        }

        return frequencies;
    }

    /**
     * Records the IDs requested from the API.
     */
    public static synchronized void recordRequests(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        for (String id : ids) {
            idRequestFrequency.merge(id, 1, Integer::sum);
        }

        unsavedChanges = true;
    }

    /**
     * Saves frequencies to CSV in descending order.
     */
    private static synchronized void saveIdRequestFrequency() {
        if (!unsavedChanges) return;

        Path tempFile = FILE.resolveSibling(
                FILE.getFileName() + ".tmp"
        );

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
                writer.write("id,amount");
                writer.newLine();

                idRequestFrequency.entrySet().stream()
                        .sorted(
                                Map.Entry.<String, Integer>comparingByValue()
                                        .reversed()
                                        .thenComparing(Map.Entry.comparingByKey())
                        )
                        .forEach(entry -> {
                            try {
                                writer.write(
                                        entry.getKey() + "," + entry.getValue()
                                );
                                writer.newLine();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
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

            Logger.addEvent(
                    "Saved ID Request Frequencies: " + idRequestFrequency.size()
            );

        } catch (IOException | UncheckedIOException e) {
            System.err.println(
                    "Could not save ID request frequencies: " + e.getMessage()
            );
        }
    }

    /**
     * Returns a copy of the current frequency data.
     */
    public static synchronized Map<String, Integer> getIdRequestFrequency() {
        return new HashMap<>(idRequestFrequency);
    }
}