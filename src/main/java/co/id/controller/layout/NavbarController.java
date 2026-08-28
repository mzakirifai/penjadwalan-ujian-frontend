package co.id.controller.layout;

import co.id.auth.AuthContext;
import co.id.model.User;
import java.io.File;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class NavbarController {
    @FXML private Button toggleSidebarBtn;
    @FXML private ImageView profileImage;
    @FXML private MenuButton menuButtonUser;
    @FXML private MenuItem menuItemLogout;
    
    @FXML
    public void initialize(){
        ControllerRegistry.setNavbarController(this);
        
        double radius = Math.min(profileImage.getFitWidth(), profileImage.getFitHeight()) / 2;
        profileImage.setClip(new Circle(radius, radius, radius));
        
        toggleSidebarBtn.setOnAction(eh -> {
            SidebarController sidebar = ControllerRegistry.getSidebarController();
            if (sidebar != null) {
                sidebar.toggleSidebar();
            }
        });
        
        User currentUser = AuthContext.getCurrentUser();
        if (currentUser != null) {
            menuButtonUser.setText(resolveDisplayName(currentUser));
            loadProfilePhoto(currentUser);
        }
    }
    
    @FXML
    private void handleEditProfile(){
        MainLayoutController main = ControllerRegistry.getMainLayoutController();
        if (main != null) {
            main.setContent("/pages/EditProfile.fxml");
        }
    }
    
    @FXML
    private void handleAccountSetting(){
        MainLayoutController main = ControllerRegistry.getMainLayoutController();
        if (main != null) {
            main.setContent("/pages/AccountSetting.fxml");
        }
    }
    
    @FXML
    private void handleLogout(){
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
            "Apakah Anda yakin ingin logout?", 
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Logout");
        confirm.setHeaderText(null);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                AuthContext.logout();

                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/Login.fxml"));
                    Parent root = loader.load();

                    Stage stage = (Stage) toggleSidebarBtn.getScene().getWindow();
                    Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
                    scene.getStylesheets().add(getClass().getResource("/css/material.css").toExternalForm());

                    stage.setScene(scene);
                    stage.setTitle("Sistem Penjadwalan Ujian - Login");
                    stage.centerOnScreen();

                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        });
    }
    
    private void loadProfilePhoto(User user){
        String photoPath = user.getPhotoPath();

        if (photoPath != null && !photoPath.isBlank()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                profileImage.setImage(new Image(photoFile.toURI().toString()));
                return;
            }
        }

        try {
            profileImage.setImage(new Image(getClass().getResourceAsStream("/images/default_profile.png")));
        } catch (Exception e) {
            // Kalau default juga tidak ada, biarkan kosong
        }
    }
    
    public void refreshProfile(){
        User currentUser = AuthContext.getCurrentUser();
        if (currentUser != null) {
            menuButtonUser.setText(resolveDisplayName(currentUser));
            loadProfilePhoto(currentUser);
        }
    }
    
    private String resolveDisplayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getTeacher() != null && user.getTeacher().getName() != null) {
            return user.getTeacher().getName();
        }
        return user.getUsername();
    }
}
