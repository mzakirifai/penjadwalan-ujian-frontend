package co.id.controller.pages;

import co.id.component.ReportParameters;
import co.id.model.ExamSession;
import co.id.model.RoomAssignment;
import co.id.model.RoomPlan;
import co.id.model.report.RoomAttendanceReportItem;
import co.id.service.ReportService;
import co.id.service.RoomAllocationService;
import co.id.service.impl.ReportServiceImpl;
import co.id.service.impl.RoomAllocationServiceImpl;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import win.zqxu.jrviewer.JRViewerFX;

/**
 * Daftar hadir.
 * - Offline (per ruangan): pilih tanggal, sesi, lalu ruangan (atau semua ruangan). Isi diambil dari penempatan
 *   ruangan yang sudah disimpan, sehingga satu lembar memuat semua siswa di ruangan itu walaupun dari beberapa kelas.
 * - Online (per kelas): pilih tanggal, sesi, lalu kelas (atau semua kelas). Satu lembar per kelas + mapel,
 *   berisi peserta terdaftar jadwal itu, tanpa ruangan dan nomor kursi.
 */
public class ReportAttendanceListController {
    private static final Locale ID = new Locale("id", "ID");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", ID);

    /** Pilihan ruangan (Offline) atau jadwal/kelas (Online); roomId null = semua pada sesi tersebut. */
    private record RoomOption(Integer roomId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final String MODE_OFFLINE = "Offline - per ruangan";
    private static final String MODE_ONLINE = "Online - per kelas";

    @FXML private ComboBox<String> comboMode;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<ExamSession> comboSession;
    @FXML private ComboBox<RoomOption> comboRoom;
    @FXML private ComboBox<String> comboExamType;
    @FXML private ComboBox<String> comboSemester;
    @FXML private TextField textFieldAcademicYear;
    @FXML private Button btnViewReport;
    @FXML private Label lblInfo;
    @FXML private StackPane reportPane;

    private RoomAllocationService roomAllocationService;
    private ReportService reportService;
    private RoomPlan currentPlan;

    @FXML
    public void initialize() {
        roomAllocationService = new RoomAllocationServiceImpl();
        reportService = new ReportServiceImpl();

        comboSession.setCellFactory(list -> new SessionCell());
        comboSession.setButtonCell(new SessionCell());

        comboExamType.setItems(FXCollections.observableArrayList(new co.id.service.impl.ExamTypeServiceImpl().getAllCodes()));
        comboSemester.setItems(FXCollections.observableArrayList("Ganjil", "Genap"));

        comboMode.setItems(FXCollections.observableArrayList(MODE_OFFLINE, MODE_ONLINE));
        comboMode.setValue(MODE_OFFLINE);
        comboMode.valueProperty().addListener((obs, oldVal, mode) -> loadSessions(datePicker.getValue()));
        datePicker.valueProperty().addListener((obs, oldVal, date) -> loadSessions(date));
        comboSession.valueProperty().addListener((obs, oldVal, session) -> loadPlan(session));
        btnViewReport.setOnAction(e -> onViewReport());

        datePicker.setValue(LocalDate.now());
    }

    private void loadSessions(LocalDate date) {
        comboSession.getItems().clear();
        loadPlan(null);
        if (date == null) {
            return;
        }
        try {
            List<ExamSession> sessions = isOnline()
                    ? roomAllocationService.getOnlineSessions(date)
                    : roomAllocationService.getSessions(date);
            comboSession.setItems(FXCollections.observableArrayList(sessions));
            if (sessions.isEmpty()) {
                lblInfo.setText(isOnline() ? "Tidak ada ujian online pada tanggal ini."
                        : "Tidak ada jadwal ujian (offline) pada tanggal ini.");
            } else {
                comboSession.getSelectionModel().selectFirst();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Gagal memuat sesi ujian: " + ex.getMessage()).showAndWait();
        }
    }

    private void loadPlan(ExamSession session) {
        currentPlan = null;
        comboRoom.getItems().clear();
        lblInfo.setText("");
        if (session == null) {
            return;
        }
        if (isOnline()) {
            List<RoomOption> options = new ArrayList<>();
            options.add(new RoomOption(null, "Semua kelas (" + session.getSchedules().size() + ")"));
            session.getSchedules().stream()
                    .sorted(java.util.Comparator.comparing(s -> s.getClassroom().getName()))
                    .forEach(s -> options.add(new RoomOption(s.getId(), s.getClassroom().getName()
                            + (s.getSubject() != null ? " - " + s.getSubjectLabel() : ""))));
            comboRoom.setItems(FXCollections.observableArrayList(options));
            comboRoom.getSelectionModel().selectFirst();
            lblInfo.setText(session.getSchedules().size() + " kelas ujian online. Satu lembar per kelas, berisi peserta yang sudah didaftarkan.");
            return;
        }
        try {
            currentPlan = roomAllocationService.loadSaved(session);
        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Gagal memuat penempatan ruangan: " + ex.getMessage()).showAndWait();
            return;
        }
        if (currentPlan == null || currentPlan.getRooms().isEmpty()) {
            currentPlan = null;
            lblInfo.setText("Sesi ini belum ditempatkan. Atur dulu di menu Penempatan Ruangan.");
            return;
        }

        List<RoomOption> options = new ArrayList<>();
        options.add(new RoomOption(null, "Semua ruangan (" + currentPlan.getRooms().size() + ")"));
        for (RoomAssignment room : currentPlan.getRooms()) {
            options.add(new RoomOption(room.getRoom().getId(),
                    room.getRoom().getName() + " (" + room.getFilled() + " siswa)"));
        }
        comboRoom.setItems(FXCollections.observableArrayList(options));
        comboRoom.getSelectionModel().selectFirst();
        lblInfo.setText(currentPlan.getTotalStudents() + " siswa di " + currentPlan.getRooms().size() + " ruangan.");
    }

    private void onViewReport() {
        ExamSession session = comboSession.getValue();
        RoomOption room = comboRoom.getValue();
        String examType = comboExamType.getValue();
        String semester = comboSemester.getValue();
        String academicYear = textFieldAcademicYear.getText() == null ? "" : textFieldAcademicYear.getText().trim();

        boolean online = isOnline();
        if (session == null || (!online && currentPlan == null) || room == null || examType == null || semester == null
                || academicYear.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, online
                    ? "Tanggal, Sesi, Kelas, Jenis Ujian, Semester, dan Tahun Akademik wajib diisi!"
                    : "Tanggal, Sesi (yang sudah ditempatkan), Ruangan, Jenis Ujian, Semester, dan Tahun Akademik wajib diisi!",
                    ButtonType.OK).showAndWait();
            return;
        }

        try {
            JasperReport jasperReport = (JasperReport) JRLoader.loadObject(
                getClass().getResourceAsStream("/reports/ReportAttendanceList.jasper")
            );

            List<RoomAttendanceReportItem> data = online
                    ? reportService.getOnlineAttendanceReport(session.getSchedules(), room.roomId())
                    : reportService.getRoomAttendanceReport(currentPlan, room.roomId());
            if (data.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, online
                        ? "Belum ada peserta terdaftar. Daftarkan dulu di menu Peserta Ujian."
                        : "Tidak ada peserta di ruangan ini.", ButtonType.OK).showAndWait();
                return;
            }

            LocalDate date = session.getDate();
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("EXAM_DATE", date.getDayOfWeek().getDisplayName(TextStyle.FULL, ID) + ", " + date.format(DATE));
            parameters.put("SIGN_DATE", date.format(DATE));
            parameters.put("EXAM_TIME", session.getStartTime().format(TIME) + " - " + session.getEndTime().format(TIME));
            parameters.put("EXAM_TYPE_LABEL", toExamTypeLabel(examType));
            parameters.put("SEMESTER", semester.toUpperCase());
            parameters.put("ACADEMIC_YEAR", academicYear);

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport,
                    ReportParameters.withSchool(parameters), new JRBeanCollectionDataSource(data));

            JRViewerFX viewerFX = new JRViewerFX(jasperPrint);
            reportPane.getChildren().setAll(viewerFX);

        } catch (JRException ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Gagal memuat laporan: " + ex.getMessage()).showAndWait();
        }
    }

    private boolean isOnline() {
        return MODE_ONLINE.equals(comboMode.getValue());
    }

    private String toExamTypeLabel(String examType) {
        return new co.id.service.impl.ExamTypeServiceImpl().label(examType);
    }

    /** Menampilkan sesi sebagai "08:00 - 09:30  |  X TKJ 1, XI TKJ 1, X TP". */
    private static class SessionCell extends ListCell<ExamSession> {
        @Override
        protected void updateItem(ExamSession session, boolean empty) {
            super.updateItem(session, empty);
            if (empty || session == null) {
                setText(null);
                return;
            }
            Set<String> classes = new LinkedHashSet<>();
            session.getSchedules().forEach(s -> {
                if (s.getClassroom() != null) {
                    classes.add(s.getClassroom().getName());
                }
            });
            setText(session.getStartTime().format(TIME) + " - " + session.getEndTime().format(TIME)
                    + "  |  " + String.join(", ", classes));
        }
    }
}