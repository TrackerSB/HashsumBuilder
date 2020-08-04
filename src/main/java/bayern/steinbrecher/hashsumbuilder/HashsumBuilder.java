package bayern.steinbrecher.hashsumbuilder;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HashsumBuilder extends Application {

    private static final List<String> DIGEST_ALGORITHMS
            = Arrays.asList("MD2", "MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512");
    private static final Logger LOGGER = Logger.getLogger(HashsumBuilder.class.getName());
    private final Map<String, TextField> DIGEST_TEXTFIELDS = new HashMap<>(DIGEST_ALGORITHMS.size());
    private Stage stage;
    private final Property<File> opened = new SimpleObjectProperty<>();

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        // Create components of window
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("grid-pane");

        int rowIndex = 0;

        gridPane.add(new Label("File"), 0, rowIndex);
        Label path = new Label("No file chosen");
        gridPane.add(path, 1, rowIndex);
        Button choose = new Button("Choose file");
        choose.setOnAction(aevt -> showSelectionDialog());
        gridPane.add(choose, 2, rowIndex);
        rowIndex++;

        for (String digest : DIGEST_ALGORITHMS) {
            gridPane.add(new Label(digest), 0, rowIndex);

            TextField algorithmOutput = new TextField();
            algorithmOutput.setEditable(false);
            algorithmOutput.setDisable(true);
            algorithmOutput.setPrefWidth(400);
            DIGEST_TEXTFIELDS.put(digest, algorithmOutput);
            gridPane.add(algorithmOutput, 1, rowIndex);

            Button copyHashsum = new Button("Copy");
            copyHashsum.setOnAction(aevt -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(algorithmOutput.getText());
                Clipboard.getSystemClipboard().setContent(content);
            });
            copyHashsum.disableProperty().bind(algorithmOutput.textProperty().isEmpty());
            gridPane.add(copyHashsum, 2, rowIndex);

            rowIndex++;
        }

        gridPane.add(new Label("Expected hash sum"), 0, rowIndex);
        TextField compareHashTextField = new TextField();
        Platform.runLater(compareHashTextField::requestFocus);
        gridPane.add(compareHashTextField, 1, rowIndex, 2, 1);
        rowIndex++;

        Label verify = new Label("No reference hash sum given");
        verify.getStyleClass()
                .addAll("message", "unknown");
        gridPane.add(verify, 0, rowIndex, 3, 1);
        rowIndex++;

        // Initiate bindings and listener
        path.textProperty().addListener((obs, oldVal, newVal) -> {
            Service<Void> service = new Service<>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<>() {
                        @Override
                        protected Void call() {
                            addStyleclassIfAbsent(verify, "calculating");
                            generateHashes(new File(newVal));
                            verify.getStyleClass().remove("calculating");
                            return null;
                        }
                    };
                }
            };

            service.setOnFailed(
                    evt -> new Alert(Alert.AlertType.ERROR, "The calculation of hash sums failed"));
            // If the current file is not the first to verify the hash sums should be cleared
            if (!DIGEST_TEXTFIELDS.isEmpty()) {
                DIGEST_TEXTFIELDS.values()
                        .forEach(tf -> tf.setText(""));
            }
            Platform.runLater(service::start);
        });
        verify.getStyleClass().addListener((Observable c) -> Platform.runLater(() -> {
            if (verify.getStyleClass().contains("fail")) {
                verify.setText("No match found");
            } else if (verify.getStyleClass().contains("successful")) {
                String digest = findConformingDigest(compareHashTextField.getText())
                        .orElseThrow(() -> new NoSuchElementException("Could not find matching digest eventhough a "
                                + "match was found"));
                verify.setText(digest + " matches");
            } else if (verify.getStyleClass().contains("calculating")) {
                verify.setText("Calculating...");
            } else if (verify.getStyleClass().contains("unknown")) {
                verify.setText("No hash sum to compare with");
            } else {
                verify.setText("Not initialized yet");
            }
        }));
        Runnable updateLabelStyleClass = () -> {
            List<String> exclusiveStyleclassGroup = new ArrayList<>(Arrays.asList("unknown", "successful", "fail"));
            String styleclass;
            if (compareHashTextField.getText().isEmpty()
                    || DIGEST_TEXTFIELDS.values().stream().allMatch(tf -> tf.getText().isEmpty())) {
                styleclass = "unknown";
            } else {
                Optional<String> digest = findConformingDigest(compareHashTextField.getText());
                if (digest.isPresent()) {
                    styleclass = "successful";
                } else {
                    styleclass = "fail";
                }
            }
            addStyleclassIfAbsent(verify, styleclass);
            exclusiveStyleclassGroup.remove(styleclass);
            verify.getStyleClass().removeAll(exclusiveStyleclassGroup);
        };
        DIGEST_TEXTFIELDS.values().parallelStream()
                .forEach(tf -> tf.textProperty().addListener(obs -> updateLabelStyleClass.run()));
        compareHashTextField.textProperty().addListener(obs -> updateLabelStyleClass.run());
        opened.addListener((obs, oldVal, newVal) -> Platform.runLater(() -> {
            try {
                path.setText(newVal.getCanonicalPath());
            } catch (IOException ex) {
                throw new IllegalStateException("No file to calculate hashvalues for.", ex);
            }
        }));

        // Finalize window
        Scene scene = new Scene(gridPane);
        URL stylesheet = getClass()
                .getResource("styles/styles.css");
        scene.getStylesheets().add(stylesheet.toExternalForm());
        scene.setOnDragOver(devt -> {
            Dragboard dragboard = devt.getDragboard();
            if (dragboard.hasFiles()) {
                List<File> draggedFiles = dragboard.getFiles();
                if (draggedFiles != null && draggedFiles.size() == 1 && draggedFiles.get(0).isFile()) {
                    devt.acceptTransferModes(TransferMode.ANY);
                } else {
                    devt.acceptTransferModes(TransferMode.NONE);
                }
            }
            devt.consume();
        });
        scene.setOnDragDropped(devt -> {
            Dragboard dragboard = devt.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles()) {
                success = true;
                String filepath = dragboard.getFiles().get(0).getAbsolutePath();
                path.setText(filepath);
            }
            devt.setDropCompleted(success);
            devt.consume();
        });

        stage.setTitle("Calculate and verify hash sums");
        stage.getIcons().add(new Image(
                getClass().getResource("/bayern/steinbrecher/hashsumbuilder/icons/logo.png").toExternalForm()));
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    private Optional<String> findConformingDigest(String hash) {
        for (Map.Entry<String, TextField> entry : DIGEST_TEXTFIELDS.entrySet()) {
            if (entry.getValue().getText().equalsIgnoreCase(hash)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static void addStyleclassIfAbsent(Node node, String styleclass) {
        if (!node.getStyleClass().contains(styleclass)) {
            node.getStyleClass().add(styleclass);
        }
    }

    private void generateHashes(File newVal) {
        DIGEST_TEXTFIELDS.forEach((key, value) -> {
            try {
                MessageDigest digest = MessageDigest.getInstance(key);
                FileInputStream fis = new FileInputStream(newVal);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = fis.read(buffer)) > -1) {
                    digest.update(buffer, 0, read);
                }
                byte[] hashedBytes = digest.digest();
                StringBuilder output = new StringBuilder();
                for (byte hashedByte : hashedBytes) {
                    output.append(Integer.toString((hashedByte & 0xff) + 0x100, 16).substring(1));
                }
                value.setText(output.toString());
            } catch (NoSuchAlgorithmException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                value.setText("Digest not supported");
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        });
    }

    private void showSelectionDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose file");
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            opened.setValue(selected);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
