package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.LookupBox;
import co.id.model.Classroom;
import co.id.model.ExamSchedule;
import co.id.model.SessionSlot;
import co.id.model.Subject;
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
    @FXML private TextArea textAreaNotes;
    @FXML private Button saveBtn;
    @FXML private Label labelPlacementInfo;

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

    // Bentrok yang diperiksa di form ini hanya bentrok kelas (ruangan & pengawas ada di Penempatan Ruangan)
    private void handleConflict(ScheduleConflictException ex) {
        new Alert(AlertType.ERROR, ex.getMessage() + "\n\nUbah tanggal atau jam ujian untuk kelas ini.").showAndWait();
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
                // Mode Edit: salin juga jam dan kelas
                textFieldStartTime.setText(examSchedule.getStartTime() != null ? examSchedule.getStartTime().format(TIME_FORMAT) : "");
                textFieldEndTime.setText(examSchedule.getEndTime() != null ? examSchedule.getEndTime().format(TIME_FORMAT) : "");

                if (examScheduleService.hasPlacement(examSchedule.getId())) {
                    labelPlacementInfo.setText("Jadwal ini sudah ditempatkan di ruangan. Jika tanggal, jam, atau kelas diubah, "
                            + "penempatan ruangan & kursinya dikosongkan dan perlu diatur ulang di menu Penempatan Ruangan.");
                }
            }
            // Mode Duplikat: jam dan kelas SENGAJA dikosongkan, karena wajib beda per kelas.

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
        doSave();
    }

    private void doSave() {
        String examType = comboExamType.getValue();
        String semester = comboSemester.getValue();
        String academicYear = textFieldAcademicYear.getText();
        LocalDate date = datePickerDate.getValue();
        Classroom classroom = lookupBoxClassroom.getSelectedItem();
        Subject subject = comboSubject.getValue();
        String notes = textAreaNotes.getText();

        if (examType == null || semester == null || academicYear == null || academicYear.isBlank()
                || date == null || classroom == null || subject == null) {
            new Alert(AlertType.WARNING, "Semua field wajib diisi (kecuali Keterangan)").showAndWait();
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

        // Sesi ujian dikelompokkan berdasarkan jam yang sama persis. Jika jam ini bertumpuk dengan sesi lain
        // tapi tidak sama (mis. 07:30 vs 08:00), kelas ini tidak ikut dicampur di Penempatan Ruangan.
        LocalTime[] alignedTime = askAlignSession(date, startTime, endTime, excludeId);
        if (alignedTime == null) {
            return;
        }
        startTime = alignedTime[0];
        endTime = alignedTime[1];

        boolean isDuplicateSubject = examScheduleService.hasSameSubjectForClassroom(
                classroom.getId(), subject.getId(), teacherCode, examType, semester, academicYear, excludeId);

        if (isDuplicateSubject) {
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

        // Objek baru (bukan baris tabel) supaya data di tabel tidak ikut berubah bila penyimpanan dibatalkan
        ExamSchedule examSchedule = new ExamSchedule();
        if (selectedExamSchedule != null) {
            examSchedule.setId(selectedExamSchedule.getId());
            examSchedule.setCode(selectedExamSchedule.getCode());
            // data lama: ruangan/pengawas yang tercatat di jadwal dipertahankan sampai dipindah ke Penempatan Ruangan
            examSchedule.setRoom(selectedExamSchedule.getRoom());
            examSchedule.setTeacher(selectedExamSchedule.getTeacher());
        }
        examSchedule.setExamType(examType);
        examSchedule.setSemester(semester);
        examSchedule.setAcademicYear(academicYear);
        examSchedule.setDate(date);
        examSchedule.setStartTime(startTime);
        examSchedule.setEndTime(endTime);
        examSchedule.setClassroom(classroom);
        examSchedule.setSubject(subject);
        examSchedule.setTeacherCode(teacherCode);
        examSchedule.setNotes(notes);

        // Tanggal/jam/kelas berubah pada jadwal yang sudah ditempatkan: minta konfirmasi dulu
        try {
            if (examScheduleService.willResetPlacement(examSchedule)) {
                Alert confirm = new Alert(AlertType.CONFIRMATION,
                        "Jadwal ini sudah ditempatkan di ruangan. Karena tanggal, jam, atau kelasnya diubah, "
                                + "penempatan ruangan, nomor kursi, dan pengawasnya akan dikosongkan.\n\n"
                                + "Setelah disimpan, atur ulang lewat menu Penempatan Ruangan. Lanjutkan?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setTitle("Penempatan Ruangan Akan Dikosongkan");
                confirm.setHeaderText(null);
                boolean proceed = confirm.showAndWait().map(r -> r == ButtonType.YES).orElse(false);
                if (!proceed) {
                    return;
                }
            }
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }

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

    /**
     * Menawarkan penyamaan jam dengan sesi lain yang bertumpuk. Mengembalikan jam yang dipakai
     * (jam sesi yang dipilih, atau jam semula bila "Tetap Simpan"), atau null bila dibatalkan.
     */
    private LocalTime[] askAlignSession(LocalDate date, LocalTime startTime, LocalTime endTime, int excludeId) {
        List<SessionSlot> slots;
        try {
            slots = examScheduleService.findMisalignedSessions(date, startTime, endTime, excludeId);
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return null;
        }
        if (slots.isEmpty()) {
            return new LocalTime[]{startTime, endTime};
        }

        StringBuilder message = new StringBuilder("Pada tanggal ini sudah ada sesi ujian yang jamnya bertumpuk, tetapi tidak sama persis:\n");
        for (SessionSlot slot : slots) {
            message.append("\n- ").append(slot.getLabel()).append(" (").append(slot.getScheduleCount()).append(" jadwal)");
        }
        message.append("\n\nJadwal hanya bisa dicampur dalam satu ruangan jika jamnya sama persis. ")
                .append("Samakan jam dengan salah satu sesi di atas?");

        Alert alert = new Alert(AlertType.CONFIRMATION, message.toString());
        alert.setTitle("Jam Sesi Tidak Sama");
        alert.setHeaderText("Jam " + startTime.format(TIME_FORMAT) + " - " + endTime.format(TIME_FORMAT) + " bertumpuk dengan sesi lain");
        alert.getButtonTypes().clear();

        java.util.Map<ButtonType, SessionSlot> choices = new java.util.LinkedHashMap<>();
        for (SessionSlot slot : slots.subList(0, Math.min(3, slots.size()))) {
            ButtonType button = new ButtonType("Pakai " + slot.getLabel());
            choices.put(button, slot);
            alert.getButtonTypes().add(button);
        }
        ButtonType keep = new ButtonType("Tetap Simpan");
        ButtonType cancel = new ButtonType("Batal", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().addAll(keep, cancel);

        ButtonType result = alert.showAndWait().orElse(cancel);
        if (choices.containsKey(result)) {
            SessionSlot chosen = choices.get(result);
            textFieldStartTime.setText(chosen.getStartTime().format(TIME_FORMAT));
            textFieldEndTime.setText(chosen.getEndTime().format(TIME_FORMAT));
            return new LocalTime[]{chosen.getStartTime(), chosen.getEndTime()};
        }
        return result == keep ? new LocalTime[]{startTime, endTime} : null;
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }
}