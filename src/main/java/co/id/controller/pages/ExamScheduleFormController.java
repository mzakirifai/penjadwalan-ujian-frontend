package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.LookupBox;
import co.id.model.Classroom;
import co.id.model.ExamSchedule;
import co.id.model.Room;
import co.id.model.RoomSuggestion;
import co.id.model.ScheduleConflict;
import co.id.model.Subject;
import co.id.model.Teacher;
import co.id.model.TeachingAssignment;
import co.id.service.ExamScheduleService;
import co.id.service.MasterService;
import co.id.service.impl.ExamScheduleServiceImpl;
import co.id.service.ScheduleConflictException;
import co.id.service.TeachingAssignmentService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.TeachingAssignmentServiceImpl;
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
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
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
    @FXML private ComboBox<TeachingAssignment> comboCode;
    @FXML private LookupBox<Room> lookupBoxRoom;
    @FXML private LookupBox<Teacher> lookupBoxTeacher;
    @FXML private TextArea textAreaNotes;
    @FXML private Button saveBtn;
    @FXML private Button btnFindRoom;
    @FXML private Label labelRoomStatus;

    private ExamScheduleService examScheduleService;
    private MasterService masterService;
    private TeachingAssignmentService teachingAssignmentService;
    private ExamSchedule selectedExamSchedule;
    private Integer pendingCode; // kode yang dipilihkan otomatis saat mode edit
    private Runnable onSaveCallback;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    public void initialize() {
        examScheduleService = new ExamScheduleServiceImpl();
        masterService = new MasterServiceImpl();
        teachingAssignmentService = new TeachingAssignmentServiceImpl();

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

        // Status ruangan langsung terlihat (kosong / terpakai oleh siapa) saat ruangan, tanggal, atau jam berubah
        lookupBoxRoom.selectedItemProperty().addListener((obs, oldVal, newVal) -> refreshRoomStatus());
        datePickerDate.valueProperty().addListener((obs, oldVal, newVal) -> refreshRoomStatus());
        textFieldStartTime.textProperty().addListener((obs, oldVal, newVal) -> refreshRoomStatus());
        textFieldEndTime.textProperty().addListener((obs, oldVal, newVal) -> refreshRoomStatus());

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

        // Kode guru: "1 - Nama Guru", diambil dari data Pengampu untuk mapel + kelas yang dipilih
        comboCode.setConverter(new StringConverter<>() {
            @Override
            public String toString(TeachingAssignment ta) {
                return ta == null ? "" : ta.getTeacherCode() + " - " + ta.getTeacher().getName();
            }

            @Override
            public TeachingAssignment fromString(String string) {
                return null;
            }
        });
        comboCode.setDisable(true);
        comboSubject.valueProperty().addListener((obs, oldVal, newVal) -> updateCodeOptions());

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

        // classroom hasil LookupBox (dari getAllClassrooms) belum JOIN ke Jurusan,
        // jadi classroom.getMajor() masih null di titik ini. Ambil ulang detail
        // lengkapnya dulu supaya filter type-nya (Umum/Kejuruan) bekerja benar.
        Classroom classroomDetail = masterService.getByIdClassroom(classroom.getId());

        List<Subject> allSubjects = masterService.getAllSubjects();

        List<Subject> filtered = allSubjects.stream()
                .filter(s -> isSubjectApplicable(s, classroomDetail))
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

    // Kode guru yang tersedia = pengampu berkode untuk mapel + kelas ini. Jika tidak ada, mapel diujikan tanpa kode.
    private void updateCodeOptions() {
        comboCode.getItems().clear();
        comboCode.setValue(null);

        Classroom classroom = lookupBoxClassroom.getSelectedItem();
        Subject subject = comboSubject.getValue();

        if (classroom == null || subject == null) {
            comboCode.setDisable(true);
            comboCode.setPromptText("Kode guru (pilih mapel terlebih dahulu)");
            return;
        }

        // satu entri per kode (kode yang sama di mapel + kelas yang sama hanya milik satu guru)
        java.util.Map<Integer, TeachingAssignment> byCode = new java.util.LinkedHashMap<>();
        for (TeachingAssignment ta : teachingAssignmentService.getCodedByClassroomAndSubject(classroom.getId(), subject.getId())) {
            byCode.putIfAbsent(ta.getTeacherCode(), ta);
        }

        if (byCode.isEmpty()) {
            comboCode.setDisable(true);
            comboCode.setPromptText("Mapel ini tanpa kode guru");
            pendingCode = null;
            return;
        }

        comboCode.setItems(FXCollections.observableArrayList(byCode.values()));
        comboCode.setDisable(false);
        comboCode.setPromptText("Pilih kode guru");

        if (pendingCode != null) {
            comboCode.getItems().stream()
                    .filter(ta -> ta.getTeacherCode().equals(pendingCode))
                    .findFirst().ifPresent(comboCode::setValue);
            pendingCode = null;
        } else if (comboCode.getItems().size() == 1) {
            comboCode.setValue(comboCode.getItems().get(0));
        }
    }

    // ================= Bentrok & ruangan kosong =================

    private LocalTime[] parseTimeRange() {
        try {
            LocalTime start = LocalTime.parse(textFieldStartTime.getText().trim(), TIME_FORMAT);
            LocalTime end = LocalTime.parse(textFieldEndTime.getText().trim(), TIME_FORMAT);
            return end.isAfter(start) ? new LocalTime[]{start, end} : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private int currentExcludeId() {
        return selectedExamSchedule != null ? selectedExamSchedule.getId() : 0;
    }

    private void refreshRoomStatus() {
        labelRoomStatus.setText("");
        Room room = lookupBoxRoom.getSelectedItem();
        LocalDate date = datePickerDate.getValue();
        LocalTime[] range = parseTimeRange();
        if (room == null || date == null || range == null) {
            return;
        }

        try {
            // kelas & pengawas = 0 supaya hanya pemakaian ruangan yang diperiksa
            List<ScheduleConflict> usage = examScheduleService.findConflicts(
                    date, range[0], range[1], room.getId(), 0, 0, currentExcludeId());
            if (usage.isEmpty()) {
                labelRoomStatus.setStyle("-fx-text-fill: #2e7d32;");
                labelRoomStatus.setText("Ruangan " + room.getName() + " kosong pada waktu ini.");
            } else {
                labelRoomStatus.setStyle("-fx-text-fill: #c62828;");
                labelRoomStatus.setText(usage.get(0).describe() + ". Klik \"Cari Ruangan Kosong\".");
            }
        } catch (Exception ex) {
            labelRoomStatus.setText("");
        }
    }

    @FXML
    private void findFreeRoom() {
        LocalDate date = datePickerDate.getValue();
        LocalTime[] range = parseTimeRange();
        if (date == null || range == null) {
            new Alert(AlertType.WARNING, "Isi tanggal ujian serta jam mulai dan selesai terlebih dahulu (format HH:mm).").showAndWait();
            return;
        }

        Classroom classroom = lookupBoxClassroom.getSelectedItem();
        List<RoomSuggestion> suggestions;
        try {
            suggestions = examScheduleService.suggestRooms(date, range[0], range[1], currentExcludeId(),
                    classroom != null ? classroom.getId() : null);
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

        chooseRoom("Ruangan kosong pada " + date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                + ", " + range[0].format(TIME_FORMAT) + " - " + range[1].format(TIME_FORMAT)
                + (classroom != null ? "\nUntuk kelas " + classroom.getName() : ""), suggestions);
    }

    // Menampilkan pilihan ruangan kosong (yang paling pas sudah terpilih); true jika ruangan diganti
    private boolean chooseRoom(String header, List<RoomSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            new Alert(AlertType.INFORMATION, "Tidak ada ruangan kosong pada waktu ini. Semua ruangan sudah terpakai.").showAndWait();
            return false;
        }

        ChoiceDialog<RoomSuggestion> dialog = new ChoiceDialog<>(suggestions.get(0), suggestions);
        dialog.setTitle("Pilih Ruangan Kosong");
        dialog.setHeaderText(header);
        dialog.setContentText("Ruangan:");

        java.util.Optional<RoomSuggestion> chosen = dialog.showAndWait();
        chosen.ifPresent(suggestion -> lookupBoxRoom.setSelectedItem(suggestion.getRoom()));
        return chosen.isPresent();
    }

    private void handleConflict(ScheduleConflictException ex) {
        if (ex.isRoomOnly() && !ex.getSuggestions().isEmpty()) {
            // Hanya ruangan yang bentrok: tawarkan ruangan kosong, lalu simpan ulang dengan ruangan baru
            if (chooseRoom(ex.getMessage() + "\n\nPilih ruangan kosong sebagai pengganti:", ex.getSuggestions())) {
                doSave(true);
            }
            return;
        }

        String hint = ex.isRoomOnly()
                ? "\n\nTidak ada ruangan kosong lain pada waktu ini. Ubah tanggal atau jam."
                : "\n\nUbah tanggal/jam, atau ganti kelas/pengawas yang bentrok.";
        new Alert(AlertType.ERROR, ex.getMessage() + hint).showAndWait();
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
                pendingCode = examSchedule.getTeacherCode();
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
        doSave(false);
    }

    private void doSave(boolean skipDuplicatePrompt) {
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
                || date == null || classroom == null || subject == null) {
            // Ruangan dan pengawas boleh kosong: pada ruangan campur keduanya diatur lewat menu Penempatan Ruangan
            new Alert(AlertType.WARNING, "Semua field wajib diisi (kecuali Ruangan, Pengawas, dan Keterangan)").showAndWait();
            return;
        }

        // Jika mapel punya kode guru di data Pengampu, kodenya wajib dipilih (tiap kode = ujian tersendiri)
        if (!comboCode.isDisabled() && comboCode.getValue() == null) {
            new Alert(AlertType.WARNING, "Pilih kode guru untuk mapel ini").showAndWait();
            return;
        }
        Integer teacherCode = comboCode.getValue() != null ? comboCode.getValue().getTeacherCode() : null;
        String subjectLabel = teacherCode == null ? subject.getName() : subject.getName() + " (" + teacherCode + ")";

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
                classroom.getId(), subject.getId(), teacherCode, examType, semester, academicYear, excludeId);

        if (isDuplicateSubject && !skipDuplicatePrompt) {
            Alert confirm = new Alert(AlertType.CONFIRMATION,
                    "Kelas \"" + classroom.getName() + "\" sudah memiliki jadwal ujian \"" + subjectLabel
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
        examSchedule.setTeacherCode(teacherCode);
        examSchedule.setRoom(room);
        examSchedule.setTeacher(teacher);
        examSchedule.setNotes(notes);

        Alert alert;
        try {
            examScheduleService.save(examSchedule, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
            alert = new Alert(AlertType.INFORMATION,
                    selectedExamSchedule == null ? "Jadwal ujian berhasil disimpan" : "Jadwal ujian berhasil diperbarui");
        } catch (ScheduleConflictException ex) {
            handleConflict(ex);
            return;
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