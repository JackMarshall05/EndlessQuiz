package org.example.questiongenerator;

import org.example.objects.Item;
import java.util.List;

public record Question(
        String questionText,
        Item item,
        String answer,
        List<String> options,
        String imageUrl
) {
    // Relationship questions automatically use a photo of their subject when available.
    public Question(String questionText, Item item, String answer, List<String> options) {
        this(questionText, item, answer, options, ImageQuery.imageUrl(item));
    }
}
