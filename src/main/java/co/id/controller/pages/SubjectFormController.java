package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.Major;
import co.id.model.Subject;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class SubjectFormController {
    @FXML private TextField textFieldName;
    @FXML private ComboBox<String> comboGrade;
    @FXML private ComboBox<String> comboType;
    @FXML private ComboBox<Major> comboMajor;
    @FXML private TextField textFieldKkm;
    @FXML private Button saveBtn;
    
    private MasterService masterService;
    private Subject selectedSubject;
    private Runnable onSaveCallback;
    
    private static final String GRADE_ALL = "Semua Tingkat";
    private static final String TYPE_UMUM = "Umum";
    private static final String TYPE_KEJURUAN = "Kejuruan";
    
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

        // Aturan: kalau jenis "Umum", jurusan tidak boleh dipilih (otomatis dikosongkan & disable).
        // Kalau "Kejuruan", jurusan wajib dipilih.
        comboType.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isKejuruan = TYPE_KEJURUAN.equals(newVal);
            comboMajor.setDisable(!isKejuruan);
            if (!isKejuruan) {
                comboMajor.setValue(null);
            }
        });

        comboMajor.setDisable(true);
    }
    
    public void setSubject(Subject subject) {
        this.selectedSubject = subject;

        if (subject != null) {
            textFieldName.setText(subject.getName());
            comboGrade.setValue(subject.getGrade() != null ? subject.getGrade() : GRADE_ALL);
            comboType.setValue(subject.getType());
            textFieldKkm.setText(String.valueOf(subject.getPassingGrade()));

            boolean isKejuruan = TYPE_KEJURUAN.equals(subject.getType());
            comboMajor.setDisable(!isKejuruan);

            if (isKejuruan && subject.getMajor() != null) {
                comboMajor.getItems().stream()
                        .filter(m -> m.getId() == subject.getMajor().getId())
                        .findFirst()
                        .ifPresent(m -> comboMajor.setValue(m));
            }
        }
    }
    
    @FXML
    private void saveEntity() {
        String name = textFieldName.getText();
        String grade = comboGrade.getValue();
        String type = comboType.getValue();
        Major major = comboMajor.getValue();
        String kkmText = textFieldKkm.getText();

        if (name == null || name.isBlank()) {
            new Alert(AlertType.WARNING, "Nama Mapel wajib diisi").showAndWait();
            return;
        }
        if (type == null) {
            new Alert(AlertType.WARNING, "Jenis mapel wajib dipilih").showAndWait();
            return;
        }
        if (TYPE_KEJURUAN.equals(type) && major == null) {
            new Alert(AlertType.WARNING, "Jurusan wajib dipilih untuk mapel Kejuruan").showAndWait();
            return;
        }

        int kkm;
        try {
            kkm = Integer.parseInt(kkmText.trim());
            if (kkm < 0 || kkm > 100) {
                new Alert(AlertType.WARNING, "KKM harus di antara 0 - 100").showAndWait();
                return;
            }
        } catch (NumberFormatException ex) {
            new Alert(AlertType.WARNING, "KKM harus berupa angka").showAndWait();
            return;
        }

        Subject subject = (selectedSubject != null) ? selectedSubject : new Subject();
        subject.setName(name);
        subject.setGrade(GRADE_ALL.equals(grade) ? null : grade);
        subject.setType(type);
        subject.setPassingGrade(kkm);
        subject.setMajor(TYPE_UMUM.equals(type) ? null : major);

        Alert alert;
        try {
            masterService.saveOrUpdateSubject(subject, AuthContext.getCurrentUsername());
            alert = new Alert(AlertType.INFORMATION,
                    selectedSubject == null ? "Data mapel berhasil disimpan" : "Data mapel berhasil diperbarui");
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