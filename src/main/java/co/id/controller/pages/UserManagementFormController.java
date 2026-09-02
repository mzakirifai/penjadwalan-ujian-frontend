package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.LookupBox;
import co.id.component.PasswordToggleField;
import co.id.model.Teacher;
import co.id.model.User;
import co.id.service.MasterService;
import co.id.service.UserService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.UserServiceImpl;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class UserManagementFormController {
    @FXML private TextField textFieldUsername;
    @FXML private PasswordToggleField passwordField;
    @FXML private ComboBox<String> comboRole;
    @FXML private TextField textFieldFullName;
    @FXML private LookupBox<Teacher> lookupBoxTeacher;
    @FXML private Button saveBtn;

    private UserService userService;
    private MasterService masterService;
    private User selectedUser;
    private Runnable onSaveCallback;

    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_GURU = "guru";

    @FXML
    public void initialize() {
        userService = new UserServiceImpl();
        masterService = new MasterServiceImpl();
        
        passwordField.setPromptText("Password (min. 6 karakter)");

        TableColumn<Teacher, String> colTeacherName = new TableColumn<>("Guru");
        colTeacherName.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getName()));
        lookupBoxTeacher.configure(
                () -> masterService.getAllTeachers(), List.of(colTeacherName), Teacher::getName
        );

        comboRole.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isGuru = ROLE_GURU.equals(newVal);
            lookupBoxTeacher.setDisable(!isGuru);
            textFieldFullName.setDisable(isGuru);

            if (isGuru) {
                textFieldFullName.clear();
            } else {
                lookupBoxTeacher.setSelectedItem(null);
            }
        });

        lookupBoxTeacher.setDisable(true);
    }

    public void setUser(User user) {
        this.selectedUser = user;

        if (user != null) {
            textFieldUsername.setText(user.getUsername());
            textFieldUsername.setDisable(true); // username tidak boleh diubah saat edit
            comboRole.setValue(user.getRole());

            // Sembunyikan field password saat edit — ganti password lewat tombol Reset Password di tabel
            passwordField.setVisible(false);
            passwordField.setManaged(false);

            boolean isGuru = ROLE_GURU.equalsIgnoreCase(user.getRole());
            lookupBoxTeacher.setDisable(!isGuru);
            textFieldFullName.setDisable(isGuru);

            if (isGuru && user.getTeacher() != null) {
                lookupBoxTeacher.setSelectedItem(user.getTeacher());
            } else {
                textFieldFullName.setText(user.getFullName());
            }
        }
    }

    @FXML
    private void saveEntity() {
        String username = textFieldUsername.getText();
        String password = passwordField.getText();
        String role = comboRole.getValue();
        String fullName = textFieldFullName.getText();
        Teacher teacher = lookupBoxTeacher.getSelectedItem();

        if (username == null || username.isBlank()) {
            new Alert(AlertType.WARNING, "Username wajib diisi").showAndWait();
            return;
        }
        if (role == null) {
            new Alert(AlertType.WARNING, "Role wajib dipilih").showAndWait();
            return;
        }
        if (ROLE_GURU.equals(role) && teacher == null) {
            new Alert(AlertType.WARNING, "Guru wajib dipilih untuk role Guru").showAndWait();
            return;
        }

        try {
            if (selectedUser == null) {
                if (password == null || password.isBlank()) {
                    new Alert(AlertType.WARNING, "Password wajib diisi untuk user baru").showAndWait();
                    return;
                }
                if (password.length() < 6) {
                    new Alert(AlertType.WARNING, "Password minimal 6 karakter").showAndWait();
                    return;
                }

                User newUser = new User();
                newUser.setUsername(username);
                newUser.setRole(role);
                newUser.setFullName(ROLE_ADMIN.equals(role) ? fullName : null);
                newUser.setTeacher(ROLE_GURU.equals(role) ? teacher : null);

                userService.register(newUser, password, AuthContext.getCurrentUsername());
                new Alert(AlertType.INFORMATION, "User berhasil ditambahkan").showAndWait();
            } else {
                selectedUser.setRole(role);
                selectedUser.setFullName(ROLE_ADMIN.equals(role) ? fullName : null);
                selectedUser.setTeacher(ROLE_GURU.equals(role) ? teacher : null);
                
                if ("admin".equalsIgnoreCase(selectedUser.getRole()) && ROLE_GURU.equalsIgnoreCase(role)) {
                    long totalAdmin = userService.getAllUsers().stream()
                            .filter(u -> "admin".equalsIgnoreCase(u.getRole()))
                            .count();

                    if (totalAdmin <= 1) {
                        new Alert(AlertType.WARNING, "Tidak bisa mengubah role Admin terakhir menjadi Guru.").showAndWait();
                        return;
                    }
                }
                
                userService.updateProfile(selectedUser, AuthContext.getCurrentUsername());
                new Alert(AlertType.INFORMATION, "User berhasil diperbarui").showAndWait();
            }
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

        if (onSaveCallback != null) {
            onSaveCallback.run();
        }

        Stage stage = (Stage) saveBtn.getScene().getWindow();
        stage.close();
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }
}