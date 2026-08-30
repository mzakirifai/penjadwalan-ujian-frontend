package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.LookupBox;
import co.id.model.ExamParticipant;
import co.id.model.ExamSchedule;
import co.id.model.Student;
import co.id.service.ExamParticipantService;
import co.id.service.MasterService;
import co.id.service.impl.ExamParticipantServiceImpl;
import co.id.service.impl.MasterServiceImpl;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ExamParticipantFormController {
    @FXML private LookupBox<Student> lookupBoxStudent;
    @FXML private TextField textFieldParticipantNumber;
    @FXML private TextField textFieldSeatNumber;
    @FXML private Button saveBtn;

    private ExamParticipantService examParticipantService;
    private MasterService masterService;
    private ExamSchedule examSchedule;
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        examParticipantService = new ExamParticipantServiceImpl();
        masterService = new MasterServiceImpl();
    }

    public void setExamSchedule(ExamSchedule examSchedule) {
        this.examSchedule = examSchedule;

        List<Student> classStudents = examSchedule.getClassroom() != null
                ? masterService.getStudentsByClassroom(examSchedule.getClassroom().getId())
                : List.of();

        TableColumn<Student, String> colStudentName = new TableColumn<>("Nama Siswa");
        colStudentName.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getName()));

        lookupBoxStudent.configure(() -> classStudents, List.of(colStudentName), Student::getName);
    }

    @FXML
    private void saveEntity() {
        Student student = lookupBoxStudent.getSelectedItem();
        String participantNumber = textFieldParticipantNumber.getText();
        String seatNumber = textFieldSeatNumber.getText();

        if (student == null) {
            new Alert(AlertType.WARNING, "Siswa wajib dipilih").showAndWait();
            return;
        }
        if (participantNumber == null || participantNumber.isBlank()
                || seatNumber == null || seatNumber.isBlank()) {
            new Alert(AlertType.WARNING, "No. Peserta dan No. Kursi wajib diisi").showAndWait();
            return;
        }
        if (!participantNumber.matches("\\d+")) {
            new Alert(AlertType.WARNING, "No. Peserta harus berupa angka (contoh: 001)").showAndWait();
            return;
        }

        ExamParticipant participant = new ExamParticipant();
        participant.setExamSchedule(examSchedule);
        participant.setStudent(student);
        participant.setParticipantNumber(participantNumber);
        participant.setSeatNumber(seatNumber);

        try {
            examParticipantService.save(participant, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

        new Alert(AlertType.INFORMATION, "Peserta berhasil ditambahkan").showAndWait();

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