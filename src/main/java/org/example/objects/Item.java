package org.example.objects;

import java.util.List;
import java.util.Map;

public class Item {

    private final String id;
    private final String label;
    private final String description;
    private final Map<String, List<String>> statements;

    /**
     * @param id the Wikidata item identifier, e.g. Q42
     * @param label the name or label of the item
     * @param description a short description of the item
     * @param statements a map of Wikidata property IDs to lists of related Wikidata item IDs
     */
    public Item(String id, String label, String description, Map<String, List<String>> statements) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.statements = statements;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, List<String>> getStatements() {
        return statements;
    }
}