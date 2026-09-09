package co.id.controller.pages;

import co.id.component.PasswordToggleField;
import co.id.auth.AuthContext;
import co.id.model.User;
import co.id.service.UserService;
import co.id.service.impl.UserServiceImpl;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;

public class AccountSettingController {
    @FXML private PasswordToggleField fieldOldPassword;
    @FXML private PasswordToggleField fieldNewPassword;
    @FXML private PasswordToggleField fieldConfirmPassword;
    @FXML private Button saveBtn;
    
    private UserService userService;
    
    @FXML
    public void initialize(){
        userService = new UserServiceImpl();
        fieldOldPassword.setPromptText("Enter your current password");
        fieldNewPassword.setPromptText("Enter new password");
        fieldConfirmPassword.setPromptText("Re-enter new password");
    }
    
    @FXML
    private void handleChangePassword(){
        User currentUser = AuthContext.getCurrentUser();
        
        if (currentUser == null) {
            return;
        }
        
        String oldPassword = fieldOldPassword.getText();
        String newPassword = fieldNewPassword.getText();
        String confirmPassword = fieldConfirmPassword.getText();
        
        // Menangani kalau semua field wajib diisi
        if (oldPassword == null || oldPassword.isBlank() 
            || newPassword == null || newPassword.isBlank() 
            || confirmPassword == null || confirmPassword.isBlank()) {
            Alert warning = new Alert(AlertType.WARNING, "Semua field wajib diisi");
            warning.showAndWait();
            return;
        }
        
        // Menangani kalau password minimal 6 karakter
        if (newPassword.length() < 6) {
            Alert warning = new Alert(AlertType.WARNING, "Password baru minimal 6 karakter");
            warning.showAndWait();
            return;
        }
        
        // Menangani kalau konfirmasi password tidak cocok
        if (!newPassword.equals(confirmPassword)) {
            Alert warning = new Alert(AlertType.WARNING, "Konfirmasi password tidak cocok");
            warning.showAndWait();
            return;
        }
        
        try {
            userService.changePassword(currentUser.getId(), oldPassword, newPassword);
            
            Alert success = new Alert(AlertType.INFORMATION, "Password berhasil diubah");
            success.showAndWait();
            
            fieldOldPassword.setText("");
            fieldNewPassword.setText("");
            fieldConfirmPassword.setText("");
            
        } catch (RuntimeException e) {
            Alert error = new Alert(AlertType.ERROR, e.getMessage());
            error.showAndWait();
        }
    }
}