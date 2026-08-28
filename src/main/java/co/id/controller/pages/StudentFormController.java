package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.LookupBox;
import co.id.model.Classroom;
import co.id.model.Student;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class StudentFormController {
    @FXML private TextField textFieldNis, textFieldName, textFieldPhone;
    @FXML private TextArea textAreaAddress;
    @FXML private LookupBox<Classroom> lookupBoxClassroom;
    @FXML private RadioButton radioMale, radioFemale;
    @FXML private Button saveBtn;
    
    private MasterService masterService;
    private Student selectedStudent;
    private Runnable onSaveCallback;
    
    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();

        TableColumn<Classroom, String> colName = new TableColumn<>("Kelas");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        lookupBoxClassroom.configure(
                () -> masterService.getAllClassrooms(), List.of(colName), Classroom::getName
        );
    }
    
    public void setStudent(Student student) {
        this.selectedStudent = student;

        if (student != null) {
            textFieldNis.setText(student.getNis());
            textFieldName.setText(student.getName());
            textFieldPhone.setText(student.getPhoneNumber());
            textAreaAddress.setText(student.getAddress());

            if (student.getClassroom() != null) {
                lookupBoxClassroom.setSelectedItem(student.getClassroom());
            }

            if ("Laki-laki".equals(student.getGender())) {
                radioMale.setSelected(true);
            } else if ("Perempuan".equals(student.getGender())) {
                radioFemale.setSelected(true);
            }
        }
    }
    
    @FXML
    private void saveEntity() {
        String nis = textFieldNis.getText();
        String name = textFieldName.getText();
        String phone = textFieldPhone.getText();
        String address = textAreaAddress.getText();
        Classroom classroom = lookupBoxClassroom.getSelectedItem();

        String gender = null;
        if (radioMale.isSelected()) {
            gender = "Laki-laki";
        } else if (radioFemale.isSelected()) {
            gender = "Perempuan";
        }

        if (nis == null || nis.isBlank() || name == null || name.isBlank()) {
            new Alert(AlertType.WARNING, "NIS dan Nama wajib diisi").showAndWait();
            return;
        }

        if (classroom == null) {
            new Alert(AlertType.WARNING, "Kelas wajib dipilih").showAndWait();
            return;
        }

        Student student = (selectedStudent != null) ? selectedStudent : new Student();
        student.setNis(nis);
        student.setName(name);
        student.setPhoneNumber(phone);
        student.setAddress(address);
        student.setGender(gender);
        student.setClassroom(classroom);

        Alert alert;
        try {
            masterService.saveOrUpdateStudent(student, AuthContext.getCurrentUsername());
            alert = new Alert(AlertType.INFORMATION,
                    selectedStudent == null ? "Data siswa berhasil disimpan" : "Data siswa berhasil diperbarui");
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
