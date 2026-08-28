package co.id;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Main extends Application{

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/pages/Login.fxml"));
        
        // Ukuran Layar Utama
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double screenWidth = screenBounds.getWidth();
        double screenHeight = screenBounds.getHeight();
        
        Scene scene = new Scene(root, screenWidth * 0.75, screenHeight * 0.75);
        
        scene.getStylesheets().add(
                getClass().getResource("/css/material.css").toExternalForm()
        );
        
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/favicon.png")));
        
        stage.setTitle("Sistem Penjadwalan Ujian - Login");
        stage.setScene(scene);
        
        stage.setX(screenBounds.getMinX() + screenWidth * 0.125);
        stage.setY(screenBounds.getMinY() + screenHeight * 0.125);
        
        stage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
