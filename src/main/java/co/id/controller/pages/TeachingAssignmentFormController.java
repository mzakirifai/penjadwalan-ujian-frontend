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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Form pengampu.
 *
 * Mode TAMBAH : satu guru dapat langsung dipilihkan beberapa kelas dan beberapa mapel.
 *               Sistem membuat semua kombinasi kelas x mapel yang sesuai (jurusan & tingkat),
 *               dan melewati kombinasi yang sudah terdaftar.
 * Mode EDIT   : mengubah satu data pengampu (satu kelas + satu mapel).
 */
public class TeachingAssignmentFormController {
    @FXML private ComboBox<Teacher> comboTeacher;
    @FXML private TextField textFieldCode;
    @FXML private ComboBox<Classroom> comboClassroom;
    @FXML private ComboBox<Subject> comboSubject;
    @FXML private VBox boxSingle;
    @FXML private VBox boxMulti;
    @FXML private ListView<Classroom> listClassroom;
    @FXML private ListView<Subject> listSubject;
    @FXML private CheckBox chkAllClassroom;
    @FXML private CheckBox chkAllSubject;
    @FXML private Label labelSummary;
    @FXML private Button saveBtn;

    private MasterService masterService;
    private TeachingAssignmentService teachingAssignmentService;
    private TeachingAssignment selectedTeachingAssignment;
    private Runnable onSaveCallback;

    private List<Subject> allSubjects;
    private final Map<Integer, BooleanProperty> classroomChecked = new HashMap<>();
    private final Map<Integer, BooleanProperty> subjectChecked = new HashMap<>();
    private final Map<Integer, Classroom> classroomDetails = new HashMap<>();
    private boolean bulkUpdating = false;

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
        teachingAssignmentService = new TeachingAssignmentServiceImpl();
        allSubjects = masterService.getAllSubjects();

        comboTeacher.setItems(FXCollections.observableArrayList(masterService.getAllTeachers()));
        comboTeacher.setConverter(converter(t -> t.getName() + " (" + t.getNip() + ")"));

        List<Classroom> classrooms = masterService.getAllClassrooms();

        // ----- Mode edit (pilihan tunggal) -----
        comboClassroom.setItems(FXCollections.observableArrayList(classrooms));
        comboClassroom.setConverter(converter(Classroom::getName));
        comboSubject.setConverter(converter(Subject::getName));
        comboSubject.setPromptText("Pilih Kelas terlebih dahulu");
        comboClassroom.valueProperty().addListener((obs, oldVal, newVal) -> updateSubjectOptions(newVal));

        // ----- Mode tambah (pilihan banyak) -----
        for (Classroom classroom : classrooms) {
            BooleanProperty checked = new SimpleBooleanProperty(false);
            checked.addListener((obs, oldVal, newVal) -> onClassroomSelectionChanged());
            classroomChecked.put(classroom.getId(), checked);
        }
        listClassroom.setItems(FXCollections.observableArrayList(classrooms));
        listClassroom.setCellFactory(CheckBoxListCell.forListView(
                classroom -> classroomChecked.get(classroom.getId()),
                converter(Classroom::getName)));

        listSubject.setCellFactory(CheckBoxListCell.forListView(
                subject -> subjectCheckedProperty(subject.getId()),
                converter(this::subjectLabel)));

        updateSummary();
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

    // ================= Mode tambah: pilihan banyak =================

    private BooleanProperty subjectCheckedProperty(int subjectId) {
        return subjectChecked.computeIfAbsent(subjectId, id -> {
            BooleanProperty checked = new SimpleBooleanProperty(false);
            checked.addListener((obs, oldVal, newVal) -> {
                if (!bulkUpdating) {
                    updateSummary();
                }
            });
            return checked;
        });
    }

    private String subjectLabel(Subject subject) {
        StringBuilder label = new StringBuilder(subject.getName());
        if (subject.getGrade() != null && !subject.getGrade().isBlank()) {
            label.append(" - ").append(subject.getGrade());
        }
        if (!"Umum".equalsIgnoreCase(subject.getType()) && subject.getMajor() != null) {
            String major = subject.getMajor().getAbbreviation() != null
                    ? subject.getMajor().getAbbreviation()
                    : subject.getMajor().getName();
            if (major != null && !major.isBlank()) {
                label.append(" (").append(major).append(")");
            }
        }
        return label.toString();
    }

    private Classroom detailOf(Classroom classroom) {
        return classroomDetails.computeIfAbsent(classroom.getId(),
                id -> masterService.getByIdClassroom(id));
    }

    private List<Classroom> selectedClassrooms() {
        return listClassroom.getItems().stream()
                .filter(c -> classroomChecked.get(c.getId()).get())
                .collect(Collectors.toList());
    }

    private List<Subject> selectedSubjects() {
        return listSubject.getItems().stream()
                .filter(s -> subjectCheckedProperty(s.getId()).get())
                .collect(Collectors.toList());
    }

    // Daftar mapel = gabungan mapel yang sesuai dengan kelas-kelas yang dicentang
    private void onClassroomSelectionChanged() {
        if (bulkUpdating) {
            return;
        }

        List<Classroom> selected = selectedClassrooms();
        List<Subject> applicable = allSubjects.stream()
                .filter(s -> selected.stream().anyMatch(c -> isSubjectApplicable(s, detailOf(c))))
                .collect(Collectors.toList());

        // Mapel yang tidak lagi tampil otomatis tidak tercentang
        Set<Integer> visibleIds = applicable.stream().map(Subject::getId).collect(Collectors.toSet());
        bulkUpdating = true;
        try {
            subjectChecked.forEach((id, checked) -> {
                if (!visibleIds.contains(id)) {
                    checked.set(false);
                }
            });
        } finally {
            bulkUpdating = false;
        }

        listSubject.setItems(FXCollections.observableArrayList(applicable));
        chkAllSubject.setSelected(false);
        updateSummary();
    }

    @FXML
    private void toggleAllClassrooms() {
        boolean selectAll = chkAllClassroom.isSelected();
        bulkUpdating = true;
        try {
            classroomChecked.values().forEach(checked -> checked.set(selectAll));
        } finally {
            bulkUpdating = false;
        }
        onClassroomSelectionChanged();
    }

    @FXML
    private void toggleAllSubjects() {
        boolean selectAll = chkAllSubject.isSelected();
        bulkUpdating = true;
        try {
            listSubject.getItems().forEach(s -> subjectCheckedProperty(s.getId()).set(selectAll));
        } finally {
            bulkUpdating = false;
        }
        updateSummary();
    }

    // Semua kombinasi kelas x mapel yang dicentang dan sesuai jurusan/tingkatnya
    private List<TeachingAssignment> buildAssignments(Teacher teacher) {
        List<TeachingAssignment> result = new ArrayList<>();
        List<Subject> subjects = selectedSubjects();
        for (Classroom classroom : selectedClassrooms()) {
            Classroom detail = detailOf(classroom);
            for (Subject subject : subjects) {
                if (isSubjectApplicable(subject, detail)) {
                    TeachingAssignment ta = new TeachingAssignment();
                    ta.setTeacher(teacher);
                    ta.setClassroom(classroom);
                    ta.setSubject(subject);
                    result.add(ta);
                }
            }
        }
        return result;
    }

    private void updateSummary() {
        if (selectedClassrooms().isEmpty() || selectedSubjects().isEmpty()) {
            labelSummary.setText("Centang kelas dan mapel yang diampu guru ini.");
            return;
        }
        int total = buildAssignments(null).size();
        labelSummary.setText("Akan dibuat " + total + " data pengampu. "
                + "Kombinasi yang sudah terdaftar otomatis dilewati.");
    }

    // ================= Mode edit: pilihan tunggal =================

    // Mapel yang tampil disesuaikan dengan jurusan & tingkat kelas (sama seperti form jadwal ujian)
    private void updateSubjectOptions(Classroom classroom) {
        comboSubject.getItems().clear();
        comboSubject.setValue(null);

        if (classroom == null) {
            return;
        }

        Classroom classroomDetail = masterService.getByIdClassroom(classroom.getId());
        List<Subject> filtered = allSubjects.stream()
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

    // Dipakai saat menambah pengampu dari baris guru: guru langsung terpilih
    public void setTeacher(Teacher teacher) {
        comboTeacher.getItems().stream()
                .filter(t -> t.getId() == teacher.getId())
                .findFirst().ifPresent(comboTeacher::setValue);
    }

    public void setTeachingAssignment(TeachingAssignment ta) {
        this.selectedTeachingAssignment = ta;

        // Mode edit: sembunyikan pilihan banyak, tampilkan pilihan tunggal
        boxMulti.setVisible(false);
        boxMulti.setManaged(false);
        boxSingle.setVisible(true);
        boxSingle.setManaged(true);

        comboTeacher.getItems().stream()
                .filter(t -> t.getId() == ta.getTeacher().getId())
                .findFirst().ifPresent(comboTeacher::setValue);

        textFieldCode.setText(ta.getTeacherCode() == null ? "" : String.valueOf(ta.getTeacherCode()));

        comboClassroom.getItems().stream()
                .filter(c -> c.getId() == ta.getClassroom().getId())
                .findFirst().ifPresent(comboClassroom::setValue);

        comboSubject.getItems().stream()
                .filter(s -> s.getId() == ta.getSubject().getId())
                .findFirst().ifPresent(comboSubject::setValue);
    }

    // ================= Simpan =================

    // Kosong = tanpa kode. Jika diisi harus angka bulat; rentang 1-99 dicek lagi di service.
    private Integer readCode() {
        String text = textFieldCode.getText() == null ? "" : textFieldCode.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Kode guru harus berupa angka, contoh: 1");
        }
    }

    @FXML
    private void saveEntity() {
        if (selectedTeachingAssignment != null) {
            saveSingle();
        } else {
            saveMultiple();
        }
    }

    private void saveSingle() {
        if (comboTeacher.getValue() == null || comboClassroom.getValue() == null || comboSubject.getValue() == null) {
            new Alert(AlertType.WARNING, "Guru, Kelas, dan Mapel wajib dipilih").showAndWait();
            return;
        }

        TeachingAssignment ta = selectedTeachingAssignment;
        ta.setTeacher(comboTeacher.getValue());
        ta.setClassroom(comboClassroom.getValue());
        ta.setSubject(comboSubject.getValue());

        try {
            ta.setTeacherCode(readCode());
            teachingAssignmentService.save(ta, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

        new Alert(AlertType.INFORMATION, "Data pengampu berhasil diperbarui").showAndWait();
        finish();
    }

    private void saveMultiple() {
        if (comboTeacher.getValue() == null) {
            new Alert(AlertType.WARNING, "Guru wajib dipilih").showAndWait();
            return;
        }

        Integer code;
        try {
            code = readCode();
        } catch (IllegalArgumentException ex) {
            new Alert(AlertType.WARNING, ex.getMessage()).showAndWait();
            return;
        }

        List<TeachingAssignment> assignments = buildAssignments(comboTeacher.getValue());
        assignments.forEach(a -> a.setTeacherCode(code));
        if (assignments.isEmpty()) {
            new Alert(AlertType.WARNING,
                    "Pilih minimal satu kelas dan satu mapel yang sesuai dengan kelas tersebut").showAndWait();
            return;
        }

        int created;
        try {
            created = teachingAssignmentService.saveAll(
                    assignments, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

        int skipped = assignments.size() - created;
        String message;
        if (created == 0) {
            message = "Tidak ada data baru. Semua " + skipped + " kombinasi sudah terdaftar.";
        } else {
            message = created + " data pengampu berhasil ditambahkan.";
            if (skipped > 0) {
                message += "\n" + skipped + " kombinasi dilewati karena sudah terdaftar (untuk mengubah kodenya, gunakan Edit).";
            }
        }
        new Alert(AlertType.INFORMATION, message).showAndWait();
        finish();
    }

    private void finish() {
        if (onSaveCallback != null) {
            onSaveCallback.run();
        }
        Stage stage = (Stage) saveBtn.getScene().getWindow();
        stage.close();
    }

    // Guru yang dipilih di form; dipakai daftar untuk membuka baris guru tersebut setelah simpan
    public Teacher getSelectedTeacher() {
        return comboTeacher.getValue();
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }
}
