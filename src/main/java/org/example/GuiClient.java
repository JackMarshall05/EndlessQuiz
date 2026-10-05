package org.example;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.questiongenerator.Question;
import org.example.questiongenerator.QuestionGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

public class GuiClient extends Application {

    private Label questionLabel;
    private Label resultLabel;
    private Label questionNumberLabel;
    private List<Button> optionButtons;

    private Question currentQuestion;
    private int questionNumber = 0;
    private boolean answered = false;

    private static final String NORMAL_BUTTON = """
            -fx-background-color: white;
            -fx-text-fill: #263238;
            -fx-font-size: 16px;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
            -fx-border-color: #cfd8dc;
            -fx-border-width: 1.5;
            -fx-padding: 14;
            -fx-cursor: hand;
            """;

    private static final String CORRECT_BUTTON = """
            -fx-background-color: #d8f3dc;
            -fx-text-fill: #1b5e20;
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
            -fx-border-color: #43a047;
            -fx-border-width: 2;
            -fx-padding: 14;
            """;

    private static final String WRONG_BUTTON = """
            -fx-background-color: #ffdddd;
            -fx-text-fill: #b71c1c;
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
            -fx-border-color: #e53935;
            -fx-border-width: 2;
            -fx-padding: 14;
            """;

    @Override
    public void start(Stage stage) {

        Label titleLabel = new Label("WIKI QUIZ");
        titleLabel.setStyle("""
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-text-fill: #3aafa9;
                """);

        questionNumberLabel = new Label("Getting first question...");
        questionNumberLabel.setStyle("""
                -fx-font-size: 13px;
                -fx-text-fill: #78909c;
                """);

        questionLabel = new Label("Loading...");
        questionLabel.setWrapText(true);
        questionLabel.setMaxWidth(Double.MAX_VALUE);
        questionLabel.setMinHeight(70);
        questionLabel.setStyle("""
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-text-fill: #263238;
                """);

        resultLabel = new Label();
        resultLabel.setWrapText(true);
        resultLabel.setMinHeight(30);
        resultLabel.setStyle("""
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                """);

        optionButtons = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            Button button = new Button();
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinHeight(55);
            button.setWrapText(true);
            button.setDisable(true);
            button.setStyle(NORMAL_BUTTON);
            optionButtons.add(button);
        }

        VBox card = new VBox(
                14,
                titleLabel,
                questionNumberLabel,
                questionLabel,
                optionButtons.get(0),
                optionButtons.get(1),
                optionButtons.get(2),
                optionButtons.get(3),
                resultLabel
        );

        card.setPadding(new Insets(30));
        card.setMaxWidth(650);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 18;
                -fx-effect: dropshadow(
                    gaussian,
                    rgba(0,0,0,0.15),
                    20,
                    0.1,
                    0,
                    5
                );
                """);

        VBox root = new VBox(card);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(35));
        root.setStyle("""
                -fx-background-color: linear-gradient(
                    to bottom right,
                    #eef7f7,
                    #e4eeee
                );
                """);

        Scene scene = new Scene(root, 750, 550);

        stage.setTitle("Wiki Quiz");
        stage.setScene(scene);
        stage.setMinWidth(650);
        stage.setMinHeight(500);
        stage.show();

        getQuestion();
    }

    /**
     * Gets the next question from QuestionGenerator.
     */
    private void getQuestion() {
        currentQuestion = null;
        answered = false;

        questionLabel.setText("Finding your next question...");
        resultLabel.setText("");

        for (Button button : optionButtons) {
            button.setText("");
            button.setDisable(true);
            button.setStyle(NORMAL_BUTTON);
            button.setOpacity(1);
        }

        Thread thread = new Thread(() -> {
            try {
                Question question = QuestionGenerator.pollQuestion();
                currentQuestion = question;

                Platform.runLater(() -> {
                    questionNumber++;

                    questionNumberLabel.setText("Question " + questionNumber);

                    questionLabel.setText(
                            question.questionText().formatted(
                                    question.item().getLabel()
                            )
                    );

                    List<String> options = question.options();

                    for (int i = 0; i < optionButtons.size(); i++) {
                        Button button = optionButtons.get(i);
                        String option = options.get(i);

                        button.setText(option);
                        button.setDisable(false);
                        button.setStyle(NORMAL_BUTTON);
                        button.setOpacity(1);

                        button.setOnAction(event -> checkAnswer(button, option));
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    questionLabel.setText("Could not create question.");
                    resultLabel.setText(e.getMessage());

                    PauseTransition pause = new PauseTransition(Duration.seconds(3));
                    pause.setOnFinished(event -> getQuestion());
                    pause.play();
                });
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Checks the selected answer.
     */
    private void checkAnswer(Button selectedButton, String selected) {
        if (currentQuestion == null || answered) {return;}

        answered = true;

        String answer = currentQuestion.answer();
        List<String> options = currentQuestion.options();

        for (Button button : optionButtons) {
            button.setDisable(true);
            button.setOpacity(1);
        }

        if (selected.equals(answer)) {
            selectedButton.setStyle(CORRECT_BUTTON);

            resultLabel.setText("Correct!");
            resultLabel.setStyle("""
                    -fx-font-size: 16px;
                    -fx-font-weight: bold;
                    -fx-text-fill: #2e7d32;
                    """);

        } else {
            selectedButton.setStyle(WRONG_BUTTON);

            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).equals(answer)) {
                    optionButtons.get(i).setStyle(CORRECT_BUTTON);
                    break;
                }
            }

            resultLabel.setText("The correct answer is " + answer + ".");

            resultLabel.setStyle("""
                    -fx-font-size: 16px;
                    -fx-font-weight: bold;
                    -fx-text-fill: #c62828;
                    """);
        }

        PauseTransition pause = new PauseTransition(Duration.seconds(3));
        pause.setOnFinished(event -> getQuestion());
        pause.play();
    }

    public static void main(String[] args) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            Scanner scanner = new Scanner(System.in);

            while (true) {
                if (scanner.nextLine().equals("exit")) {
                    System.exit(0);
                }
            }
        });
        launch(args);
    }
}