package org.example.questiongenerator;

import org.example.objects.Item;
import java.util.Collection;
import java.util.List;

public interface Query {
    record Context(List<Item> subjects, Collection<Item> cachedItems) {}
    List<Question> generate(Context context, int count) throws Exception;

    static boolean validLabel(Item item) {
        return item != null && item.getLabel() != null && !item.getLabel().isBlank()
                && !item.getLabel().matches("Q\\d+");
    }
}
