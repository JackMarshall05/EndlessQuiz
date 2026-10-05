package org.example.questiongenerator;

import java.util.List;

public record QuestionType(
        String question,
        String itemType,
        List<QuestionCondition> conditions,
        boolean notTrue
) { }