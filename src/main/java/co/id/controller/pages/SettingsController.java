package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.SchoolSettings;
import co.id.service.SchoolSettingsService;
import co.id.service.impl.SchoolSettingsServiceImpl;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class SettingsController {
    @FXML private TextField textFieldSchoolName;
    @FXML private TextField textFieldAddress;
    @FXML private TextField textFieldPhone;
    @FXML private TextField textFieldEmail;
    @FXML private TextField textFieldPrincipalName;
    @FXML private TextField textFieldPrincipalNip;
    @FXML private ComboBox<String> comboActiveSemester;
    @FXML private TextField textFieldActiveAcademicYear;
    @FXML private Button saveBtn;

    private SchoolSettingsService schoolSettingsService;
    private SchoolSettings currentSettings;

    @FXML
    public void initialize() {
        schoolSettingsService = new SchoolSettingsServiceImpl();
        comboActiveSemester.setItems(FXCollections.observableArrayList("Ganjil", "Genap"));

        loadSettings();

        if (!AuthContext.isAdmin()) {
            applyReadOnlyMode();
        }
    }

    private void loadSettings() {
        currentSettings = schoolSettingsService.getSettings();

        if (currentSettings == null) {
            new Alert(AlertType.ERROR, "Data pengaturan tidak ditemukan. Pastikan tabel pengaturan sudah diisi 1 baris data (id = 1).").showAndWait();
            return;
        }

        textFieldSchoolName.setText(currentSettings.getSchoolName());
        textFieldAddress.setText(currentSettings.getAddress());
        textFieldPhone.setText(currentSettings.getPhone());
        textFieldEmail.setText(currentSettings.getEmail());
        textFieldPrincipalName.setText(currentSettings.getPrincipalName());
        textFieldPrincipalNip.setText(currentSettings.getPrincipalNip());
        comboActiveSemester.setValue(currentSettings.getActiveSemester());
        textFieldActiveAcademicYear.setText(currentSettings.getActiveAcademicYear());
    }

    private void applyReadOnlyMode() {
        textFieldSchoolName.setEditable(false);
        textFieldAddress.setEditable(false);
        textFieldPhone.setEditable(false);
        textFieldEmail.setEditable(false);
        textFieldPrincipalName.setEditable(false);
        textFieldPrincipalNip.setEditable(false);
        comboActiveSemester.setDisable(true);
        textFieldActiveAcademicYear.setEditable(false);
        saveBtn.setVisible(false);
        saveBtn.setManaged(false);
    }

    @FXML
    private void saveSettings() {
        if (currentSettings == null) {
            return;
        }

        currentSettings.setSchoolName(textFieldSchoolName.getText());
        currentSettings.setAddress(textFieldAddress.getText());
        currentSettings.setPhone(textFieldPhone.getText());
        currentSettings.setEmail(textFieldEmail.getText());
        currentSettings.setPrincipalName(textFieldPrincipalName.getText());
        currentSettings.setPrincipalNip(textFieldPrincipalNip.getText());
        currentSettings.setActiveSemester(comboActiveSemester.getValue());
        currentSettings.setActiveAcademicYear(textFieldActiveAcademicYear.getText());

        try {
            schoolSettingsService.updateSettings(currentSettings, AuthContext.getCurrentUser().getUsername());
            new Alert(AlertType.INFORMATION, "Pengaturan berhasil disimpan.").showAndWait();
        } catch (IllegalArgumentException ex) {
            new Alert(AlertType.WARNING, ex.getMessage()).showAndWait();
        }
    }
}