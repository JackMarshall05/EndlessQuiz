package org.example.objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class Item {
    private final String id;
    private final String label;
    private final String description;
    private final Map<String, List<String>> statements;
    private final long qrank;

    /**
     * @param id the Wikidata item identifier, e.g. Q42
     * @param label the name or label of the item
     * @param description a short description of the item
     * @param statements a map of Wikidata property IDs to lists of related Wikidata item IDs
     */
    @JsonCreator
    public Item(
            @JsonProperty("id") String id,
            @JsonProperty("label") String label,
            @JsonProperty("description") String description,
            @JsonProperty("statements") Map<String, List<String>> statements,
            @JsonProperty("qrank") long qrank
    ) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.statements = statements;
        this.qrank = qrank;
    }

    public String getId() {return id;}
    public String getLabel() {return label;}
    public String getDescription() {return description;}
    public Map<String, List<String>> getStatements() {return statements;}
    public long getQrank() {return qrank;}
}