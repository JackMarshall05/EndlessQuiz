package org.example.saveddata;
import java.util.List;

public interface SavedData<T> {
    void recordData(T data);
    void saveData();
    void loadData();
    T getData();
    void runShutdownHook();
}
