package org.example.objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public class Item {
    private final String id;
    private final String label;
    private final String description;
    private final Map<String, List<String>> statements;
    private final long qrank;
    private final Map<String, Measurement> numericProperties;
    private final boolean numericLoaded;
    private final boolean imageLoaded;

    // Values are converted into a consistent unit; year is optional (P585).
    public record Measurement(double value, String unit, Integer year) {}

    @JsonCreator
    public Item(@JsonProperty("id") String id,
                @JsonProperty("label") String label,
                @JsonProperty("description") String description,
                @JsonProperty("statements") Map<String, List<String>> statements,
                @JsonProperty("qrank") long qrank,
                @JsonProperty("numericProperties") Map<String, Measurement> numericProperties,
                @JsonProperty("numericLoaded") boolean numericLoaded,
                @JsonProperty("imageLoaded") boolean imageLoaded) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.statements = statements == null ? new HashMap<>() : statements;
        this.qrank = qrank;
        this.numericProperties = numericProperties == null ? new HashMap<>() : numericProperties;
        this.numericLoaded = numericLoaded;
        this.imageLoaded = imageLoaded;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public Map<String, List<String>> getStatements() { return statements; }
    public long getQrank() { return qrank; }
    public Map<String, Measurement> getNumericProperties() { return numericProperties; }
    public boolean isNumericLoaded() { return numericLoaded; }
    public boolean isImageLoaded() { return imageLoaded; }
}
