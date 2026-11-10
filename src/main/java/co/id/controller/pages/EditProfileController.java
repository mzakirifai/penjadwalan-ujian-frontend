package co.id.controller.pages;

import co.id.controller.layout.ControllerRegistry;
import co.id.controller.layout.NavbarController;
import co.id.auth.AuthContext;
import co.id.model.User;
import co.id.service.UserService;
import co.id.service.impl.UserServiceImpl;
import java.io.File;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
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
    private byte[] selectedPhotoData;
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
        byte[] photoData = currentUser.getPhotoData();

        if (photoData != null && photoData.length > 0) {
            profileImageView.setImage(new Image(new ByteArrayInputStream(photoData)));
            return;
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
            try {
                selectedPhotoData = resizeToJpeg(file);
                profileImageView.setImage(new Image(new ByteArrayInputStream(selectedPhotoData)));
            } catch (Exception e) {
                e.printStackTrace();
                new Alert(AlertType.ERROR, "Gagal memproses foto. Pastikan file berupa gambar PNG atau JPG.").showAndWait();
            }
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
        
        if (selectedPhotoData != null) {
            currentUser.setPhotoData(selectedPhotoData);
        }
        
        try {
            userService.updateProfile(currentUser, currentUser.getUsername());
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }
        
        AuthContext.setCurrentUser(currentUser);
        
        NavbarController navbar = ControllerRegistry.getNavbarController();
        if (navbar != null) {
            navbar.refreshProfile();
}
        
        Alert alert = new Alert(AlertType.INFORMATION, "Profile berhasil diperbarui");
        alert.showAndWait();
    }
    
    // Perkecil foto jadi 256x256 (dipotong persegi di tengah), simpan sebagai JPEG
    private byte[] resizeToJpeg(File sourceFile) throws IOException {
        BufferedImage source = ImageIO.read(sourceFile);
        if (source == null) {
            throw new IOException("File bukan gambar yang didukung");
        }

        int size = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        BufferedImage square = source.getSubimage(x, y, size, size);

        int target = 256;
        java.awt.Image scaled = square.getScaledInstance(target, target, java.awt.Image.SCALE_SMOOTH);

        BufferedImage result = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, target, target);
        graphics.drawImage(scaled, 0, 0, null);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(result, "jpg", output);
        return output.toByteArray();
    }
}