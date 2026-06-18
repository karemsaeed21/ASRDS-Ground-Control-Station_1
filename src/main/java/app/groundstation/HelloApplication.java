package app.groundstation;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("index.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1280, 800);
        stage.setTitle("ASRDS Ground Control Station");
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.centerOnScreen();
        stage.setMaximized(true);

        stage.setOnShown(e -> Platform.runLater(() -> {
            if (stage.isIconified()) {
                stage.setIconified(false);
            }
            stage.toFront();
            stage.requestFocus();
        }));

        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    public static void main(String[] args) {
        launch();
    }
}
