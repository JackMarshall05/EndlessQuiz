
package org.example;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
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
    private Label imageStatusLabel;
    private ImageView imageView;
    private StackPane imagePane;
    private List<Button> optionButtons;

    private Question currentQuestion;
    private int questionNumber = 0;
    private boolean answered = false;
    private boolean imageErrorHandled = false;

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

        imageView = new ImageView();
        imageView.setFitWidth(550);
        imageView.setFitHeight(260);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        imageStatusLabel = new Label();
        imageStatusLabel.setWrapText(true);
        imageStatusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #607d8b;");
        imagePane = new StackPane(imageView, imageStatusLabel);
        imagePane.setPrefHeight(270);
        imagePane.setMaxWidth(Double.MAX_VALUE);
        imagePane.setStyle("-fx-background-color: #f1f5f6; -fx-background-radius: 10;");
        imagePane.setVisible(false);
        imagePane.setManaged(false);

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
                imagePane,
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

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #eef7f7; -fx-background-color: transparent;");
        Scene scene = new Scene(scroll, 750, 650);

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
        imageErrorHandled = false;
        imagePane.setVisible(false);
        imagePane.setManaged(false);
        imageView.setImage(null);
        imageStatusLabel.setText("");

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
                Platform.runLater(() -> showQuestion(question));

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

    private void showQuestion(Question question) {
        currentQuestion = question;
        questionNumber++;
        questionNumberLabel.setText("Question " + questionNumber);
        // Every query supplies finished text; do not format URLs or text twice.
        questionLabel.setText(question.questionText());

        List<String> options = question.options();
        for (int i = 0; i < optionButtons.size(); i++) {
            Button button = optionButtons.get(i);
            String option = options.get(i);
            button.setText(option);
            button.setDisable(true);
            button.setStyle(NORMAL_BUTTON);
            button.setOpacity(1);
            button.setOnAction(event -> checkAnswer(button, option));
        }

        if (question.imageUrl() == null || question.imageUrl().isBlank()) {
            enableOptions();
            return;
        }

        imagePane.setVisible(true);
        imagePane.setManaged(true);
        imageStatusLabel.setText("Loading image...");
        // For ordinary questions, the photo is supplementary: do not delay answering.
        if (!question.imageUrl().equals(question.questionText())) enableOptions();
        Image image;
        try {
            image = new Image(question.imageUrl(), 550, 260, true, true, true);
        } catch (Exception e) {
            imageLoadFailed(question);
            return;
        }
        imageView.setImage(image);
        image.errorProperty().addListener((obs, oldValue, error) -> {
            if (error) Platform.runLater(() -> imageLoadFailed(question));
        });
        image.progressProperty().addListener((obs, oldValue, progress) -> {
            if (progress.doubleValue() >= 1) Platform.runLater(() -> {
                if (currentQuestion != question || imageErrorHandled) return;
                if (image.isError() || image.getWidth() <= 0) imageLoadFailed(question);
                else {
                    imageStatusLabel.setText("");
                    enableOptions();
                }
            });
        });
        if (image.isError()) imageLoadFailed(question);
        else if (image.getProgress() >= 1 && image.getWidth() > 0) {
            imageStatusLabel.setText("");
            enableOptions();
        }
    }

    private void enableOptions() {
        for (Button button : optionButtons) button.setDisable(false);
    }

    private void imageLoadFailed(Question question) {
        if (currentQuestion != question || imageErrorHandled) return;
        imageErrorHandled = true;
        imageView.setImage(null);
        if (question.imageUrl().equals(question.questionText())) {
            // The picture IS the question: skip if it cannot load.
            imageStatusLabel.setText("Image unavailable. Skipping this question...");
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(event -> { if (currentQuestion == question) getQuestion(); });
            pause.play();
        } else {
            // An optional supporting image failed; still let the player answer.
            imagePane.setVisible(false);
            imagePane.setManaged(false);
            imageStatusLabel.setText("");
            enableOptions();
        }
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
