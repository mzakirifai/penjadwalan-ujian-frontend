package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.Teacher;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class TeacherFormController {
    @FXML private TextField textFieldNip, textFieldName, textFieldPhone, textFieldEmail;
    @FXML private TextArea textAreaAddress;
    @FXML private RadioButton radioMale, radioFemale;
    @FXML private Button saveBtn;

    private MasterService masterService;
    private Teacher selectedTeacher;
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
    }

    public void setTeacher(Teacher teacher) {
        this.selectedTeacher = teacher;

        if (teacher != null) {
            textFieldNip.setText(teacher.getNip());
            textFieldName.setText(teacher.getName());
            textFieldPhone.setText(teacher.getPhoneNumber());
            textFieldEmail.setText(teacher.getEmail());
            textAreaAddress.setText(teacher.getAddress());

            if ("Laki-laki".equals(teacher.getGender())) {
                radioMale.setSelected(true);
            } else if ("Perempuan".equals(teacher.getGender())) {
                radioFemale.setSelected(true);
            }
        }
    }

    @FXML
    private void saveEntity() {
        String nip = textFieldNip.getText();
        String name = textFieldName.getText();
        String phone = textFieldPhone.getText();
        String email = textFieldEmail.getText();
        String address = textAreaAddress.getText();

        String gender = null;
        if (radioMale.isSelected()) {
            gender = "Laki-laki";
        } else if (radioFemale.isSelected()) {
            gender = "Perempuan";
        }

        if (nip == null || nip.isBlank() || name == null || name.isBlank()) {
            new Alert(AlertType.WARNING, "NIP dan Nama wajib diisi").showAndWait();
            return;
        }

        Teacher teacher = (selectedTeacher != null) ? selectedTeacher : new Teacher();
        teacher.setNip(nip);
        teacher.setName(name);
        teacher.setGender(gender);
        teacher.setPhoneNumber(phone);
        teacher.setEmail(email);
        teacher.setAddress(address);

        Alert alert;
        try {
            masterService.saveOrUpdateTeacher(teacher, AuthContext.getCurrentUsername());
            alert = new Alert(AlertType.INFORMATION,
                    selectedTeacher == null ? "Data guru berhasil disimpan" : "Data guru berhasil diperbarui");
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

        alert.showAndWait();

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