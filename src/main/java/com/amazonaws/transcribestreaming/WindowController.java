/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.amazonaws.transcribestreaming;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponse;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptResultStream;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * This class primarily controls the GUI for this application. Most of the code relevant to starting and working
 * with our streaming API can be found in TranscribeStreamingClientWrapper.java, with the exception of some result
 * parsing logic in this classes method getResponseHandlerForWindow()
 */
public class WindowController {

    private static final double WINDOW_WIDTH = 800;
    private static final double WINDOW_HEIGHT = 1200;

    private TranscribeStreamingClientWrapper client;
    private TranscribeStreamingSynchronousClient synchronousClient;
    private TextArea outputTextArea;
    private Button startStopMicButton;
    private Button saveButton;
    private TextArea finalTextArea;
    private CompletableFuture<Void> inProgressStreamingRequest;
    private String finalTranscript = "";
    private Stage primaryStage;

    public WindowController(Stage primaryStage) {

        client = new TranscribeStreamingClientWrapper();
        synchronousClient = new TranscribeStreamingSynchronousClient(TranscribeStreamingClientWrapper.getClient());

        this.primaryStage = primaryStage;
        initializeWindowWithInProgressTranscription(primaryStage);
    }

    /**
     * Transcription related methods and logic
     */
    private void startFileTranscriptionRequest(File inputFile) {

        if (inProgressStreamingRequest == null) {

            cleanFinalTextArea();
            finalTranscript = "";
            startStopMicButton.setText("Streaming...");
            startStopMicButton.setDisable(true);
            outputTextArea.clear();

            cleanFinalTextArea();
            disableSaveButton(true);

            finalTranscript = synchronousClient.transcribeFile(inputFile);
            setContentOfFinalTextArea(finalTranscript);
            startStopMicButton.setDisable(false);
            disableSaveButton(false);

            startStopMicButton.setText("Start Microphone Transcription");
        }
    }

    private void startTranscriptionRequest(File inputFile) {

        if (inProgressStreamingRequest == null) {

            cleanFinalTextArea();
            finalTranscript = "";
            startStopMicButton.setText("Connecting...");
            startStopMicButton.setDisable(true);
            outputTextArea.clear();

            cleanFinalTextArea();
            disableSaveButton(true);

            inProgressStreamingRequest = client.startTranscription(getResponseHandlerForWindow(), inputFile);
        }
    }

    private void stopTranscription() {
        if (inProgressStreamingRequest != null) {
            try {
                disableSaveButton(true);
                client.stopTranscription();
                inProgressStreamingRequest.get();
            } catch (ExecutionException | InterruptedException e) {
                System.out.println("error closing stream");
            } finally {
                inProgressStreamingRequest = null;
                startStopMicButton.setText("Start Microphone Transcription");
                startStopMicButton.setOnAction(__ -> startTranscriptionRequest(null));
                startStopMicButton.setDisable(false);
            }

        }
    }

    /**
     * UI initialization and handling
     */
    private void initializeWindowWithInProgressTranscription(Stage primaryStage) {

        GridPane grid = new GridPane();

        gridSetup(grid); // Building my grid elements here (2 columns)

        // Setting columns size in percent
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(30);
        grid.getColumnConstraints().add(column);

        column = new ColumnConstraints();
        column.setPercentWidth(70);
        grid.getColumnConstraints().add(column);

        grid.setPrefSize(WINDOW_WIDTH, WINDOW_HEIGHT); // Default width and height
        grid.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        Scene scene = new Scene(grid, WINDOW_WIDTH, WINDOW_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initializeWindowWithStreamFromFileAndFullTranscript(Stage primaryStage) {

        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.CENTER);
        gridPane.setVgap(10);
        gridPane.setHgap(10);

        GridPane.setHgrow(gridPane, Priority.ALWAYS);

        HBox hboxContainer  = new HBox(gridPane);
        hboxContainer.setAlignment(Pos.CENTER);
        hboxContainer.setPadding(new Insets(10));

        // Set Hgrow for TextField
        HBox.setHgrow(gridPane, Priority.ALWAYS);

        BorderPane pane = new BorderPane();
        pane.setCenter(hboxContainer);

        Scene scene = new Scene(pane, 1000, 600);
        primaryStage.setScene(scene);

        fullGridSetup(gridPane);
    }

    private void gridSetup(GridPane gridPane) {

        startStopMicButton = buildStartStopMicControl("Start Microphone Transcription");
        gridPane.add(startStopMicButton, 0, 0, 1, 1);

        Text inProgressText = new Text("In Progress Transcriptions:");
        gridPane.add(inProgressText, 0, 1, 2, 1);

        outputTextArea = buildOutputTextArea();

        Font font = new Font("Verdana", 64);
        outputTextArea.setFont(font);

        gridPane.add(outputTextArea, 0, 2, 2, 1);
    }

    private void fullGridSetup(GridPane gridPane) {

        startStopMicButton = buildStartStopMicControl("Start Microphone Transcription");
        gridPane.add(startStopMicButton, 0, 0, 1, 1);

        Button fileStreamButton = buildFileStreamControl("Stream From Audio File");
        gridPane.add(fileStreamButton, 1, 0, 1, 1);

        Text inProgressText = new Text("In Progress Transcriptions:");
        gridPane.add(inProgressText, 0, 1, 2, 1);

        outputTextArea = buildOutputTextArea();
        gridPane.add(outputTextArea, 0, 2, 2, 1);

        Text finalText = new Text("Final Transcription:");
        gridPane.add(finalText, 0, 3, 2, 1);

        finalTextArea = new TextArea();
        finalTextArea.setWrapText(true);
        finalTextArea.setEditable(false);
        gridPane.add(finalTextArea, 0, 4, 2, 1);

        saveButton = new Button();
        saveButton.setDisable(true);
        saveButton.setText("Save Full Transcript");
        gridPane.add(saveButton, 0, 5, 2, 1);

    }

    private Button buildFileStreamControl(String label) {

        Button button = new Button();

        button.setText(label);

        //TODO: what file types do we support?
        button.setOnAction(__ -> {
            FileChooser inputFileChooser = new FileChooser();
            inputFileChooser.setTitle(label);
            File inputFile = inputFileChooser.showOpenDialog(primaryStage);
            if (inputFile != null) {
                startFileTranscriptionRequest(inputFile);
            }
        });

        return button;
    }

    private TextArea buildOutputTextArea() {

        TextArea textArea = new TextArea();

        textArea.setWrapText(true);
        textArea.setEditable(false);

        return textArea;
    }

    private Button buildStartStopMicControl(String label) {

        Button button = new Button();

        button.setText(label);
        button.setOnAction(__ -> startTranscriptionRequest(null));

        return button;
    }

    private void cleanFinalTextArea() {
        if(finalTextArea != null) {
            finalTextArea.clear();
        }
    }

    private void setContentOfFinalTextArea(String content) {
        if(finalTextArea != null) {
            finalTextArea.setText(content);
        }
    }

    private void disableSaveButton(Boolean status) {
        if(saveButton != null) {
            saveButton.setDisable(status);
        }
    }

    public void close() {
        if (inProgressStreamingRequest != null) {
            inProgressStreamingRequest.completeExceptionally(new InterruptedException());
        }
        client.close();
    }

    /**
     * A StartStreamTranscriptionResponseHandler class listens to events from Transcribe streaming service that return
     * transcriptions, and decides what to do with them. This example displays the transcripts in the GUI window, and
     * combines the transcripts together into a final transcript at the end.
     */
    private StreamTranscriptionBehavior getResponseHandlerForWindow() {
        return new StreamTranscriptionBehavior() {

            //This will handle errors being returned from AWS Transcribe in your response. Here we just print the exception.
            @Override
            public void onError(Throwable e) {
                System.out.println(e.getMessage());
                Throwable cause = e.getCause();
                while (cause != null) {
                    System.out.println("Caused by: " + cause.getMessage());
                    Arrays.stream(cause.getStackTrace()).forEach(l -> System.out.println("  " + l));
                    if (cause.getCause() != cause) { //Look out for circular causes
                        cause = cause.getCause();
                    } else {
                        cause = null;
                    }
                }
                System.out.println("Error Occurred: " + e);
            }

            /*
            This handles each event being received from the Transcribe service. In this example we are displaying the
            transcript as it is updated, and when we receive a "final" transcript, we append it to our finalTranscript
            which is returned at the end of the microphone streaming.
             */
            @Override
            public void onStream(TranscriptResultStream event) {
                List<Result> results = ((TranscriptEvent) event).transcript().results();
                if(results.size()>0) {
                    Result firstResult = results.get(0);
                    if (firstResult.alternatives().size() > 0 && !firstResult.alternatives().get(0).transcript().isEmpty()) {
                        String transcript = firstResult.alternatives().get(0).transcript();
                        if(!transcript.isEmpty()) {
                            System.out.println(transcript);
                            String displayText;
                            if (!firstResult.isPartial()) {
                                finalTranscript += transcript + " ";
                                displayText = transcript;
                            } else {
                                displayText = transcript;
                            }
                            Platform.runLater(() -> {
                                outputTextArea.setText(displayText);
                                outputTextArea.setScrollTop(Double.MAX_VALUE);
                            });
                        }
                    }

                }
            }

            /*
            This handles the initial response from the AWS Transcribe service, generally indicating the streams have
            successfully been opened. Here we just print that we have received the initial response and do some
            UI updates.
             */
            @Override
            public void onResponse(StartStreamTranscriptionResponse r) {
                System.out.println(String.format("=== Received Initial response. Request Id: %s ===", r.requestId()));
                Platform.runLater(() -> {
                    startStopMicButton.setText("Stop Transcription");
                    startStopMicButton.setOnAction(__ -> stopTranscription());
                    startStopMicButton.setDisable(false);
                });
            }

            /*
            This method is called when the stream is terminated without error. In our case we will use this opportunity
            to display the final, total transcript we've been aggregating during the transcription period and activates
            the save button.
             */
            @Override
            public void onComplete() {
                System.out.println("=== All records streamed successfully ===");
                Platform.runLater(() -> {

                    setContentOfFinalTextArea(finalTranscript);
                    outputTextArea.clear();

                    System.out.println("Debugging start...");
                    System.out.println(finalTranscript);
                    System.out.println("Debugging end...");

                    if (saveButton != null) {

                        saveButton.setDisable(false);
                        saveButton.setOnAction(__ -> {
                            FileChooser fileChooser = new FileChooser();
                            fileChooser.setTitle("Save Transcript");
                            File file = fileChooser.showSaveDialog(primaryStage);
                            if (file != null) {
                                try {
                                    FileWriter writer = new FileWriter(file);
                                    writer.write(finalTranscript);
                                    writer.close();
                                } catch (IOException e) {
                                    System.out.println("Error saving transcript to file: " + e);
                                }
                            }
                        });
                    }
                });
            }
        };
    }

}
