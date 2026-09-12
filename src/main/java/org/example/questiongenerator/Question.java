package org.example.questiongenerator;

import org.example.objects.Item;

import java.util.List;

public record Question (
        String questionText,
        Item item,
        String answer,
        List<String> options,
        int qRank
){}
