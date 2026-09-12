package org.example.objects;

public class Property {

    private final String id;
    private final String label;
    private final String description;
    private final String dataType;

    public Property(String id, String label, String description, String dataType) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.dataType = dataType;
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

    public String getDataType() {
        return dataType;
    }
}