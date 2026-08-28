package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.LookupBox;
import co.id.model.Classroom;
import co.id.model.ExamSchedule;
import co.id.model.Room;
import co.id.model.Subject;
import co.id.model.Teacher;
import co.id.service.ExamScheduleService;
import co.id.service.MasterService;
import co.id.service.impl.ExamScheduleServiceImpl;
import co.id.service.impl.MasterServiceImpl;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class ExamScheduleFormController {
    @FXML private ComboBox<String> comboExamType;
    @FXML private ComboBox<String> comboSemester;
    @FXML private TextField textFieldAcademicYear;
    @FXML private DatePicker datePickerDate;
    @FXML private TextField textFieldStartTime;
    @FXML private TextField textFieldEndTime;
    @FXML private LookupBox<Classroom> lookupBoxClassroom;
    @FXML private ComboBox<Subject> comboSubject;
    @FXML private LookupBox<Room> lookupBoxRoom;
    @FXML private LookupBox<Teacher> lookupBoxTeacher;
    @FXML private TextArea textAreaNotes;
    @FXML private Button saveBtn;

    private ExamScheduleService examScheduleService;
    private MasterService masterService;
    private ExamSchedule selectedExamSchedule;
    private Runnable onSaveCallback;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    public void initialize() {
        examScheduleService = new ExamScheduleServiceImpl();
        masterService = new MasterServiceImpl();

        TableColumn<Classroom, String> colClassroomName = new TableColumn<>("Kelas");
        colClassroomName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        lookupBoxClassroom.configure(
                () -> masterService.getAllClassrooms(), List.of(colClassroomName), Classroom::getName
        );

        TableColumn<Room, String> colRoomName = new TableColumn<>("Ruangan");
        colRoomName.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().getName()));
        lookupBoxRoom.configure(
                () -> masterService.getAllRooms(), List.of(colRoomName), Room::getName
        );

        TableColumn<Teacher, String> colTeacherName = new TableColumn<>("Guru");
        colTeacherName.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getName()));
        lookupBoxTeacher.configure(
                () -> masterService.getAllTeachers(), List.of(colTeacherName), Teacher::getName
        );

        comboSubject.setConverter(new StringConverter<>() {
            @Override
            public String toString(Subject subject) {
                return subject != null ? subject.getName() : "";
            }

            @Override
            public Subject fromString(String string) {
                return null;
            }
        });

        // Setiap kali kelas berubah, refresh daftar mapel yang relevan
        lookupBoxClassroom.selectedItemProperty().addListener((obs, oldVal, newVal) -> updateSubjectOptions(newVal));
    }

    private void updateSubjectOptions(Classroom classroom) {
        comboSubject.getItems().clear();
        comboSubject.setValue(null);

        if (classroom == null) {
            comboSubject.setPromptText("Pilih Kelas terlebih dahulu");
            return;
        }

        List<Subject> allSubjects = masterService.getAllSubjects();

        List<Subject> filtered = allSubjects.stream()
                .filter(s -> isSubjectApplicable(s, classroom))
                .collect(Collectors.toList());

        comboSubject.setItems(FXCollections.observableArrayList(filtered));
        comboSubject.setPromptText("Pilih Mapel");

        if (pendingSubjectAfterDuplicate != null) {
            filtered.stream()
                    .filter(s -> s.getId() == pendingSubjectAfterDuplicate.getId())
                    .findFirst()
                    .ifPresent(s -> comboSubject.setValue(s));
            pendingSubjectAfterDuplicate = null;
        }
    }

    private boolean isSubjectApplicable(Subject subject, Classroom classroom) {
        boolean typeMatch;
        if ("Umum".equalsIgnoreCase(subject.getType())) {
            typeMatch = true;
        } else {
            typeMatch = subject.getMajor() != null
                    && classroom.getMajor() != null
                    && subject.getMajor().getId() == classroom.getMajor().getId();
        }

        boolean gradeMatch = subject.getGrade() == null
                || subject.getGrade().equalsIgnoreCase(classroom.getGrade());

        return typeMatch && gradeMatch;
    }

    public void setExamSchedule(ExamSchedule examSchedule, boolean isDuplicate) {
        this.selectedExamSchedule = isDuplicate ? null : examSchedule;

        if (examSchedule != null) {
            comboExamType.setValue(examSchedule.getExamType());
            comboSemester.setValue(examSchedule.getSemester());
            textFieldAcademicYear.setText(examSchedule.getAcademicYear());
            datePickerDate.setValue(examSchedule.getDate());
            textAreaNotes.setText(examSchedule.getNotes());

            if (!isDuplicate) {
                // Mode Edit: salin juga jam, kelas, ruangan, pengawas
                textFieldStartTime.setText(examSchedule.getStartTime() != null ? examSchedule.getStartTime().format(TIME_FORMAT) : "");
                textFieldEndTime.setText(examSchedule.getEndTime() != null ? examSchedule.getEndTime().format(TIME_FORMAT) : "");

                if (examSchedule.getRoom() != null) {
                    lookupBoxRoom.setSelectedItem(examSchedule.getRoom());
                }
                if (examSchedule.getTeacher() != null) {
                    lookupBoxTeacher.setSelectedItem(examSchedule.getTeacher());
                }
            }
            // Mode Duplikat: jam, ruangan, pengawas SENGAJA dikosongkan,
            // karena field ini wajib beda per kelas.

            if (examSchedule.getClassroom() != null && !isDuplicate) {
                lookupBoxClassroom.setSelectedItem(examSchedule.getClassroom());
                updateSubjectOptions(examSchedule.getClassroom());

                if (examSchedule.getSubject() != null) {
                    comboSubject.getItems().stream()
                            .filter(s -> s.getId() == examSchedule.getSubject().getId())
                            .findFirst()
                            .ifPresent(s -> comboSubject.setValue(s));
                }
            } else if (isDuplicate) {
                // Kelas juga dikosongkan, tapi tetap simpan mapel target
                // untuk dipilihkan otomatis begitu user pilih kelas baru
                pendingSubjectAfterDuplicate = examSchedule.getSubject();
            }
        }
    }

    private Subject pendingSubjectAfterDuplicate;

    @FXML
    private void saveEntity() {
        String examType = comboExamType.getValue();
        String semester = comboSemester.getValue();
        String academicYear = textFieldAcademicYear.getText();
        LocalDate date = datePickerDate.getValue();
        Classroom classroom = lookupBoxClassroom.getSelectedItem();
        Subject subject = comboSubject.getValue();
        Room room = lookupBoxRoom.getSelectedItem();
        Teacher teacher = lookupBoxTeacher.getSelectedItem();
        String notes = textAreaNotes.getText();

        if (examType == null || semester == null || academicYear == null || academicYear.isBlank()
                || date == null || classroom == null || subject == null || room == null || teacher == null) {
            new Alert(AlertType.WARNING, "Semua field wajib diisi (kecuali Keterangan)").showAndWait();
            return;
        }

        LocalTime startTime;
        LocalTime endTime;
        try {
            startTime = LocalTime.parse(textFieldStartTime.getText().trim(), TIME_FORMAT);
            endTime = LocalTime.parse(textFieldEndTime.getText().trim(), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            new Alert(AlertType.WARNING, "Format jam harus HH:mm, contoh: 07:30").showAndWait();
            return;
        }

        if (!endTime.isAfter(startTime)) {
            new Alert(AlertType.WARNING, "Jam selesai harus setelah jam mulai").showAndWait();
            return;
        }

        int excludeId = (selectedExamSchedule != null) ? selectedExamSchedule.getId() : 0;
        boolean isDuplicateSubject = examScheduleService.hasSameSubjectForClassroom(
                classroom.getId(), subject.getId(), examType, semester, academicYear, excludeId);

        if (isDuplicateSubject) {
            Alert confirm = new Alert(AlertType.CONFIRMATION,
                    "Kelas \"" + classroom.getName() + "\" sudah memiliki jadwal ujian \"" + subject.getName()
                            + "\" untuk " + examType + " semester " + semester + " " + academicYear + ".\n\n"
                            + "Lanjutkan menyimpan sebagai sesi ujian tambahan/susulan?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Konfirmasi Duplikasi Mapel");
            confirm.setHeaderText(null);

            boolean proceed = confirm.showAndWait()
                    .map(response -> response == ButtonType.YES)
                    .orElse(false);

            if (!proceed) {
                return;
            }
        }

        ExamSchedule examSchedule = (selectedExamSchedule != null) ? selectedExamSchedule : new ExamSchedule();
        examSchedule.setExamType(examType);
        examSchedule.setSemester(semester);
        examSchedule.setAcademicYear(academicYear);
        examSchedule.setDate(date);
        examSchedule.setStartTime(startTime);
        examSchedule.setEndTime(endTime);
        examSchedule.setClassroom(classroom);
        examSchedule.setSubject(subject);
        examSchedule.setRoom(room);
        examSchedule.setTeacher(teacher);
        examSchedule.setNotes(notes);

        Alert alert;
        try {
            examScheduleService.save(examSchedule, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
            alert = new Alert(AlertType.INFORMATION,
                    selectedExamSchedule == null ? "Jadwal ujian berhasil disimpan" : "Jadwal ujian berhasil diperbarui");
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