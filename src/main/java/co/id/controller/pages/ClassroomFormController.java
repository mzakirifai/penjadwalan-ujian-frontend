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
    // Pilihan rombel untuk kelas yang tidak memakai rombel (nama kelas hanya Tingkat + Jurusan, misalnya "XI PH")
    private static final String NO_ROMBEL = "Tanpa rombel";

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

        if (!comboRombel.getItems().contains(NO_ROMBEL)) {
            comboRombel.getItems().add(NO_ROMBEL);
        }

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

            // Coba ambil rombel dari nama kelas yang sudah ada (best-effort)
            if (classroom.getName() != null && !classroom.getName().isBlank()) {
                String name = classroom.getName().trim();
                Major major = comboMajor.getValue();
                String withoutRombel = (classroom.getGrade() != null && major != null)
                        ? classroom.getGrade() + " " + majorLabel(major)
                        : null;

                if (withoutRombel != null && name.equalsIgnoreCase(withoutRombel)) {
                    // Nama kelas hanya Tingkat + Jurusan, berarti kelas ini tidak memakai rombel
                    comboRombel.setValue(NO_ROMBEL);
                } else {
                    String[] parts = name.split(" ");
                    String lastPart = parts[parts.length - 1];
                    if (lastPart.length() == 1) {
                        comboRombel.setValue(lastPart.toUpperCase());
                    }
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

        String base = grade + " " + majorLabel(major);
        return NO_ROMBEL.equals(rombel) ? base : base + " " + rombel;
    }

    private String majorLabel(Major major) {
        return major.getAbbreviation() != null ? major.getAbbreviation() : major.getName();
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
            new Alert(AlertType.WARNING, "Rombel wajib dipilih. Kalau kelas ini tidak memakai rombel, pilih \"Tanpa rombel\".").showAndWait();
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
            masterService.saveOrUpdateClassroom(classroom, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
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