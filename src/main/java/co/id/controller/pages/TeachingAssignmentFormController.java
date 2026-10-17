package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.Classroom;
import co.id.model.Subject;
import co.id.model.Teacher;
import co.id.model.TeachingAssignment;
import co.id.service.MasterService;
import co.id.service.TeachingAssignmentService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.TeachingAssignmentServiceImpl;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class TeachingAssignmentFormController {
    @FXML private ComboBox<Teacher> comboTeacher;
    @FXML private ComboBox<Classroom> comboClassroom;
    @FXML private ComboBox<Subject> comboSubject;
    @FXML private Button saveBtn;

    private MasterService masterService;
    private TeachingAssignmentService teachingAssignmentService;
    private TeachingAssignment selectedTeachingAssignment;
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
        teachingAssignmentService = new TeachingAssignmentServiceImpl();

        comboTeacher.setItems(FXCollections.observableArrayList(masterService.getAllTeachers()));
        comboTeacher.setConverter(converter(t -> t.getName() + " (" + t.getNip() + ")"));

        comboClassroom.setItems(FXCollections.observableArrayList(masterService.getAllClassrooms()));
        comboClassroom.setConverter(converter(Classroom::getName));

        comboSubject.setConverter(converter(Subject::getName));
        comboSubject.setPromptText("Pilih Kelas terlebih dahulu");

        comboClassroom.valueProperty().addListener((obs, oldVal, newVal) -> updateSubjectOptions(newVal));
    }

    private <T> StringConverter<T> converter(Function<T, String> formatter) {
        return new StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : formatter.apply(item);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    // Mapel yang tampil disesuaikan dengan jurusan & tingkat kelas (sama seperti form jadwal ujian)
    private void updateSubjectOptions(Classroom classroom) {
        comboSubject.getItems().clear();
        comboSubject.setValue(null);

        if (classroom == null) {
            return;
        }

        Classroom classroomDetail = masterService.getByIdClassroom(classroom.getId());
        List<Subject> filtered = masterService.getAllSubjects().stream()
                .filter(s -> isSubjectApplicable(s, classroomDetail))
                .collect(Collectors.toList());

        comboSubject.setItems(FXCollections.observableArrayList(filtered));
        comboSubject.setPromptText("Pilih Mapel");
    }

    private boolean isSubjectApplicable(Subject subject, Classroom classroom) {
        boolean typeMatch = "Umum".equalsIgnoreCase(subject.getType())
                || (subject.getMajor() != null && classroom.getMajor() != null
                    && subject.getMajor().getId() == classroom.getMajor().getId());

        boolean gradeMatch = subject.getGrade() == null
                || subject.getGrade().equalsIgnoreCase(classroom.getGrade());

        return typeMatch && gradeMatch;
    }

    public void setTeachingAssignment(TeachingAssignment ta) {
        this.selectedTeachingAssignment = ta;

        comboTeacher.getItems().stream()
                .filter(t -> t.getId() == ta.getTeacher().getId())
                .findFirst().ifPresent(comboTeacher::setValue);

        comboClassroom.getItems().stream()
                .filter(c -> c.getId() == ta.getClassroom().getId())
                .findFirst().ifPresent(comboClassroom::setValue);

        comboSubject.getItems().stream()
                .filter(s -> s.getId() == ta.getSubject().getId())
                .findFirst().ifPresent(comboSubject::setValue);
    }

    @FXML
    private void saveEntity() {
        if (comboTeacher.getValue() == null || comboClassroom.getValue() == null || comboSubject.getValue() == null) {
            new Alert(AlertType.WARNING, "Guru, Kelas, dan Mapel wajib dipilih").showAndWait();
            return;
        }

        TeachingAssignment ta = (selectedTeachingAssignment != null) ? selectedTeachingAssignment : new TeachingAssignment();
        ta.setTeacher(comboTeacher.getValue());
        ta.setClassroom(comboClassroom.getValue());
        ta.setSubject(comboSubject.getValue());

        try {
            teachingAssignmentService.save(ta, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

        new Alert(AlertType.INFORMATION,
                selectedTeachingAssignment == null ? "Data pengampu berhasil disimpan" : "Data pengampu berhasil diperbarui")
                .showAndWait();

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