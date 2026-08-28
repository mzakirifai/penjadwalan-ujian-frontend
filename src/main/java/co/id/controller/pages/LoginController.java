package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.PasswordToggleField;
import co.id.model.User;
import co.id.service.UserService;
import co.id.service.impl.UserServiceImpl;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {
    @FXML private TextField textFieldUsername;
    @FXML private PasswordToggleField passwordFieldPassword;
    @FXML private Label lblErrorMessage;
    @FXML private Button btnLogin;
    
    private UserService userService;
    
    @FXML
    public void initialize(){
        userService = new UserServiceImpl();
        passwordFieldPassword.setPromptText("Password");
    }
    
    @FXML
    private void handleLogin(){
        String username = textFieldUsername.getText();
        String password = passwordFieldPassword.getText();
        
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            showError("Username dan password wajib diisi");
            return;
        }
        
        User user = userService.login(username, password);
        
        if (user == null) {
            showError("Username atau password salah");
            return;
        }
        
        // Login berhasil, buka MainLayout
        openMainLayout(user);
    }
    
    private void showError(String message){
        lblErrorMessage.setText(message);
        lblErrorMessage.setVisible(true);
        lblErrorMessage.setManaged(true);
    }
    
    private void openMainLayout(User user){
        AuthContext.login(user);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/MainLayout.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) btnLogin.getScene().getWindow();
            Scene scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(getClass().getResource("/css/material.css").toExternalForm());

            stage.setScene(scene);
            stage.setTitle("Sistem Penjadwalan Ujian");
            stage.centerOnScreen();

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
