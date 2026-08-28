package co.id.component;

import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public class PasswordToggleField extends StackPane{
    
    private final PasswordField passwordField = new PasswordField();
    private final TextField textField = new TextField();
    private final Button toggleButton = new Button();
    private boolean passwordVisible = false;

    public PasswordToggleField() {
        textField.setManaged(false);
        textField.setVisible(false);
        
        // Sinkronkan isi kedua field
        textField.textProperty().bindBidirectional(passwordField.textProperty());
        
        toggleButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        updateToggleIcon();
        
        toggleButton.setOnAction(e -> togglePasswordVisibility());
        
        StackPane.setAlignment(toggleButton, javafx.geometry.Pos.CENTER_RIGHT);
        toggleButton.setTranslateX(-5);
        
        getChildren().addAll(passwordField, textField, toggleButton);
    }
    
    private void togglePasswordVisibility(){
        passwordVisible = !passwordVisible;
        
        passwordField.setManaged(!passwordVisible);
        passwordField.setVisible(!passwordVisible);
        textField.setManaged(passwordVisible);
        textField.setVisible(passwordVisible);
        
        updateToggleIcon();
    }
    
    private void updateToggleIcon(){
        String iconPath = passwordVisible ? "/icons/eye_open.png" : "/icons/eye_closed.png";
        try {
            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream(iconPath)));
            icon.setFitWidth(16);
            icon.setFitHeight(16);
            toggleButton.setGraphic(icon);
        } catch (Exception e) {
            toggleButton.setText(passwordVisible ? "Hide" : "Show");
        }
    }

    public String getText(){
        return passwordField.getText();
    }
    
    public void setText(String text){
        passwordField.setText(text);
    }
    
    public void setPromptText(String prompt){
        passwordField.setPromptText(prompt);
        textField.setPromptText(prompt);
    }
    
    public PasswordField getPasswordField(){
        return passwordField;
    }
}
