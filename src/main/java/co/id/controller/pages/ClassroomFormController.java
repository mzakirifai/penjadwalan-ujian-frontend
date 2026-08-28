package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.Classroom;
import co.id.model.Major;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class ClassroomFormController {
    @FXML private ComboBox<String> comboGrade;
    @FXML private ComboBox<Major> comboMajor;
    @FXML private ComboBox<String> comboRombel;
    @FXML private Label lblPreview;
    @FXML private Button saveBtn;

    private MasterService masterService;
    private Classroom selectedClassroom;
    private Runnable onSaveCallback;

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();

        List<Major> majors = masterService.getAllMajors();
        comboMajor.setItems(FXCollections.observableArrayList(majors));

        comboMajor.setConverter(new StringConverter<>() {
            @Override
            public String toString(Major major) {
                if (major == null) {
                    return "";
                }
                return major.getAbbreviation() != null ? major.getAbbreviation() : major.getName();
            }

            @Override
            public Major fromString(String string) {
                return null;
            }
        });

        comboGrade.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        comboMajor.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        comboRombel.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());
    }

    public void setClassroom(Classroom classroom) {
        this.selectedClassroom = classroom;

        if (classroom != null) {
            comboGrade.setValue(classroom.getGrade());

            if (classroom.getMajor() != null) {
                comboMajor.getItems().stream()
                        .filter(m -> m.getId() == classroom.getMajor().getId())
                        .findFirst()
                        .ifPresent(m -> comboMajor.setValue(m));
            }

            // Coba ambil rombel dari huruf terakhir nama kelas yang sudah ada (best-effort)
            if (classroom.getName() != null && !classroom.getName().isBlank()) {
                String[] parts = classroom.getName().trim().split(" ");
                String lastPart = parts[parts.length - 1];
                if (lastPart.length() == 1) {
                    comboRombel.setValue(lastPart.toUpperCase());
                }
            }

            updatePreview();
        }
    }

    private void updatePreview() {
        String generatedName = generateName();
        lblPreview.setText("Nama kelas: " + (generatedName != null ? generatedName : "-"));
    }

    private String generateName() {
        String grade = comboGrade.getValue();
        Major major = comboMajor.getValue();
        String rombel = comboRombel.getValue();

        if (grade == null || major == null || rombel == null) {
            return null;
        }

        String abbreviation = major.getAbbreviation() != null ? major.getAbbreviation() : major.getName();
        return grade + " " + abbreviation + " " + rombel;
    }

    @FXML
    private void saveEntity() {
        String grade = comboGrade.getValue();
        Major major = comboMajor.getValue();
        String rombel = comboRombel.getValue();

        if (grade == null) {
            new Alert(AlertType.WARNING, "Tingkat wajib dipilih").showAndWait();
            return;
        }
        if (major == null) {
            new Alert(AlertType.WARNING, "Jurusan wajib dipilih").showAndWait();
            return;
        }
        if (rombel == null) {
            new Alert(AlertType.WARNING, "Rombel wajib dipilih").showAndWait();
            return;
        }

        String generatedName = generateName();
        int currentId = (selectedClassroom != null) ? selectedClassroom.getId() : 0;

        boolean isDuplicate = masterService.getAllClassrooms().stream()
                .anyMatch(c -> c.getId() != currentId
                        && generatedName.equalsIgnoreCase(c.getName()));

        if (isDuplicate) {
            new Alert(AlertType.WARNING,
                    "Kelas \"" + generatedName + "\" sudah ada. Gunakan kombinasi Tingkat/Jurusan/Rombel lain.")
                    .showAndWait();
            return;
        }

        Classroom classroom = (selectedClassroom != null) ? selectedClassroom : new Classroom();
        classroom.setName(generatedName);
        classroom.setGrade(grade);
        classroom.setMajor(major);

        Alert alert;
        try {
            masterService.saveOrUpdateClassroom(classroom, AuthContext.getCurrentUsername());
            alert = new Alert(AlertType.INFORMATION,
                    selectedClassroom == null ? "Data kelas berhasil disimpan" : "Data kelas berhasil diperbarui");
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