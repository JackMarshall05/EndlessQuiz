package org.example.saveddata;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class SavedData {
    protected static final ObjectMapper mapper = new ObjectMapper();
    protected static int unsavedCount = 0;
}
