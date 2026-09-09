package co.id.controller.pages;

import co.id.controller.layout.ControllerRegistry;
import co.id.controller.layout.NavbarController;
import co.id.auth.AuthContext;
import co.id.model.User;
import co.id.service.UserService;
import co.id.service.impl.UserServiceImpl;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

public class EditProfileController {
    
    @FXML private ImageView profileImageView;
    @FXML private Button btnChoosePhoto;
    @FXML private TextField textFieldFullName;
    @FXML private Label lblUsername;
    @FXML private Label lblRole;
    @FXML private Button saveBtn;
    
    private UserService userService;
    private User currentUser;
    private File selectedPhotoFile;
    private boolean nameLockedToTeacher; 
    
    @FXML
    public void initialize(){
        userService = new UserServiceImpl();
        currentUser = AuthContext.getCurrentUser();
        
        if (currentUser == null) {
            return;
        }
        
        double radius = 50;
        profileImageView.setClip(new Circle(radius, radius, radius));
        
        nameLockedToTeacher = currentUser.getTeacher() != null;

        if (nameLockedToTeacher) {
            textFieldFullName.setText(currentUser.getTeacher().getName());
            textFieldFullName.setDisable(true);
            textFieldFullName.setPromptText("Nama diatur lewat Data Guru");
        } else {
            textFieldFullName.setText(currentUser.getFullName());
        }
        
        lblUsername.setText("Username: " + currentUser.getUsername());
        lblRole.setText("Role: " + currentUser.getRole());
            
        loadCurrentPhoto(); 
    }
    
    private void loadCurrentPhoto(){
        String photoPath = currentUser.getPhotoPath();
        
        if (photoPath != null && !photoPath.isBlank()) {
            File photoFile = new File(photoPath);
            if (photoFile.exists()) {
                profileImageView.setImage(new Image(photoFile.toURI().toString()));
                return;
            }
        }
        
        // Foto default kalau belum ada
        try {
            profileImageView.setImage(new Image(getClass().getResourceAsStream("/images/default_profile.png")));
        } catch (Exception e) {
            // Kalau default juga tidak ada, biarkan kosong
        }
    }
    
    @FXML
    private void handleChoosePhoto(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Profile Photo");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        
        File file = fileChooser.showOpenDialog(btnChoosePhoto.getScene().getWindow());
        
        if (file != null) {
            selectedPhotoFile = file;
            profileImageView.setImage(new Image(file.toURI().toString()));
        }
    }
    
    @FXML
    private void saveProfile(){
        if (!nameLockedToTeacher) {
        String fullName = textFieldFullName.getText();
        
        if (fullName == null || fullName.isBlank()) {
            Alert warning = new Alert(AlertType.WARNING, "Full Name wajib diisi");
            warning.showAndWait();
            return;
        }
        
        currentUser.setFullName(fullName);
    }
        
        if (selectedPhotoFile != null) {
            String savedPath = savePhotoToFolder(selectedPhotoFile);
            if (savedPath != null) {
                currentUser.setPhotoPath(savedPath);
            }
        }
        
        userService.updateProfile(currentUser, currentUser.getUsername());
        AuthContext.setCurrentUser(currentUser);
        
        NavbarController navbar = ControllerRegistry.getNavbarController();
        if (navbar != null) {
            navbar.refreshProfile();
}
        
        Alert alert = new Alert(AlertType.INFORMATION, "Profile berhasil diperbarui");
        alert.showAndWait();
    }
    
    private String savePhotoToFolder(File sourceFile){
        try {
            Path photosDir = Paths.get(System.getProperty("user.dir"), "photos");
            
            if (!Files.exists(photosDir)) {
                Files.createDirectories(photosDir);
            }
            
            String extension = sourceFile.getName().substring(sourceFile.getName().lastIndexOf('.'));
            String fileName = "user_" + currentUser.getId() + extension;
            
            Path targetPath = photosDir.resolve(fileName);
            
            Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            return targetPath.toString();
            
        } catch (IOException e) {
            e.printStackTrace();
            Alert error = new Alert(AlertType.ERROR, "Gagal menyimpan foto");
            error.showAndWait();
            return null;
        }
    }
}