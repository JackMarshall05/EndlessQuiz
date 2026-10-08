package org.example.questiongenerator;

public record QuestionCondition(
        String statement,
        String property,
        String answerType,
        boolean reverse,
        String answerItemType
) {
    public QuestionCondition(String statement, String property, String answerType) {
        this(statement, property, answerType, false, null);
    }
}