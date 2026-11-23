package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.export.RoomAttendanceExcelWriter;
import co.id.model.ExamSchedule;
import co.id.model.ExamSession;
import co.id.model.Room;
import co.id.model.RoomAssignment;
import co.id.model.RoomAvailability;
import co.id.model.RoomPlan;
import co.id.model.SeatAssignment;
import co.id.model.Teacher;
import co.id.service.MasterService;
import co.id.service.RoomAllocationService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.RoomAllocationServiceImpl;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/**
 * Penempatan ruangan campur: untuk satu sesi ujian (tanggal + jam), siswa dari semua jadwal pada sesi itu
 * dibagi ke ruangan-ruangan terpilih (campuran 2-3 kelas per ruangan, kursi berselingan), lalu tiap ruangan diberi satu pengawas.
 */
public class RoomAllocationController {
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<ExamSession> comboSession;
    @FXML private Label lblSessionInfo, lblRoomHint, lblStatus, lblSeatsTitle;
    @FXML private Button btnSelectAll, btnAutoRooms, btnGenerate, btnAutoSupervisor, btnSave, btnExport;
    @FXML private ListView<RoomAvailability> listRooms;

    @FXML private TableView<RoomAssignment> tableRooms;
    @FXML private TableColumn<RoomAssignment, String> colRoom, colCapacity, colFilled, colClasses;
    @FXML private TableColumn<RoomAssignment, RoomAssignment> colSupervisor;

    @FXML private TableView<SeatAssignment> tableSeats;
    @FXML private TableColumn<SeatAssignment, String> colSeat, colNumber, colNis, colName, colClass, colSubject;

    @FXML private TextArea areaWarnings;
    @FXML private Button btnManual;
    @FXML private Button btnMoveSeat;
    @FXML private Button btnApply;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private RoomAllocationService service;
    private MasterService masterService;

    private final Map<Integer, BooleanProperty> roomChecked = new HashMap<>();
    private final Teacher noTeacher = new Teacher();      // pilihan "(kosong)" pada kolom pengawas
    private ObservableList<Teacher> teacherItems;

    private ExamSession currentSession;
    private RoomPlan currentPlan;
    private boolean saved;                                // currentPlan sama dengan yang tersimpan di database

    @FXML
    public void initialize() {
        service = new RoomAllocationServiceImpl();
        masterService = new MasterServiceImpl();

        noTeacher.setId(0);
        noTeacher.setName("(kosong)");
        teacherItems = FXCollections.observableArrayList();
        teacherItems.add(noTeacher);
        teacherItems.addAll(masterService.getAllTeachers());

        tableRooms.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableSeats.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colRoom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRoom().getName()));
        colCapacity.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getRoom().getCapacity())));
        colFilled.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getFilled())));
        colClasses.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().describeClasses()));

        // Isi sel = RowData-nya sendiri supaya sel selalu diperbarui saat data diganti
        colSupervisor.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colSupervisor.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<Teacher> combo = new ComboBox<>(teacherItems);
            private boolean updating;

            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setConverter(new StringConverter<>() {
                    @Override public String toString(Teacher t) { return t == null ? "" : t.getName(); }
                    @Override public Teacher fromString(String s) { return null; }
                });
                combo.valueProperty().addListener((obs, oldVal, newVal) -> {
                    RoomAssignment row = getItem();
                    if (!updating && row != null && newVal != null) {
                        row.setSupervisor(newVal == noTeacher ? null : newVal);
                        markUnsaved();
                    }
                });
            }

            @Override
            protected void updateItem(RoomAssignment row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                updating = true;
                combo.setValue(teacherFor(row.getSupervisor()));
                updating = false;
                setGraphic(combo);
            }
        });

        colSeat.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeatLabel()));
        colNumber.setCellValueFactory(c -> new SimpleStringProperty(nullToEmpty(c.getValue().getParticipantNumber())));
        colNis.setCellValueFactory(c -> new SimpleStringProperty(nullToEmpty(c.getValue().getStudent().getNis())));
        colName.setCellValueFactory(c -> new SimpleStringProperty(nullToEmpty(c.getValue().getStudent().getName())));
        colClass.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassName()));
        colSubject.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSchedule() != null ? c.getValue().getSchedule().getSubjectLabel() : "-"));

        tableRooms.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, room) -> showSeats(room));

        comboSession.valueProperty().addListener((obs, oldVal, session) -> openSession(session));
        datePicker.valueProperty().addListener((obs, oldVal, date) -> loadSessions(date));

        listRooms.setCellFactory(list -> new RoomCell());

        btnSelectAll.setOnAction(e -> toggleAllRooms());
        btnAutoRooms.setOnAction(e -> autoSelectRooms());
        btnGenerate.setOnAction(e -> generate());
        btnAutoSupervisor.setOnAction(e -> autoSupervisors());
        btnSave.setOnAction(e -> save());
        btnExport.setOnAction(e -> export());
        btnManual.setOnAction(e -> manual());
        btnMoveSeat.setOnAction(e -> moveSeat());
        btnApply.setOnAction(e -> applyToOtherSessions());
        tableSeats.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateButtons());

        btnSave.setDisable(!AuthContext.isAdmin());
        updateButtons();

        datePicker.setValue(LocalDate.now());
    }

    // ------------------------------------------------------------------ sesi & ruangan

    private void loadSessions(LocalDate date) {
        comboSession.getItems().clear();
        clearPlan();
        listRooms.getItems().clear();
        lblSessionInfo.setText("");
        if (date == null) {
            return;
        }

        List<ExamSession> sessions;
        try {
            sessions = service.getSessions(date);
        } catch (Exception ex) {
            error(ex);
            return;
        }
        comboSession.setItems(FXCollections.observableArrayList(sessions));
        if (sessions.isEmpty()) {
            lblSessionInfo.setText("Tidak ada jadwal ujian pada tanggal ini.");
        } else {
            comboSession.getSelectionModel().selectFirst();
        }
    }

    private void openSession(ExamSession session) {
        currentSession = session;
        clearPlan();
        listRooms.getItems().clear();
        if (session == null) {
            lblSessionInfo.setText("");
            return;
        }

        lblSessionInfo.setText(session.getSchedules().stream()
                .map(s -> (s.getClassroom() != null ? s.getClassroom().getName() : "-") + ": " + s.getSubjectLabel())
                .collect(Collectors.joining("  |  ")));

        try {
            // Semua ruangan ditampilkan; yang dipakai sesi lain (jam bertumpuk) abu-abu dan tidak bisa dicentang
            List<RoomAvailability> rooms = service.getRoomAvailability(session);
            roomChecked.clear();
            for (RoomAvailability availability : rooms) {
                if (availability.isAvailable()) {
                    BooleanProperty checked = new SimpleBooleanProperty(false);
                    checked.addListener((o, a, b) -> updateRoomHint());
                    roomChecked.put(availability.getRoom().getId(), checked);
                }
            }
            listRooms.setItems(FXCollections.observableArrayList(rooms));

            long busy = rooms.stream().filter(r -> !r.isAvailable()).count();
            lblRoomHint.setText(busy > 0 ? busy + " ruangan (abu-abu) sedang dipakai sesi lain yang waktunya bertumpuk." : "");

            RoomPlan savedPlan = service.loadSaved(session);
            if (savedPlan != null) {
                showPlan(savedPlan, true);
                savedPlan.getRooms().forEach(r -> {
                    BooleanProperty checked = roomChecked.get(r.getRoom().getId());
                    if (checked != null) {
                        checked.set(true);
                    }
                });
            } else {
                lblStatus.setText("Belum ada penempatan. Centang ruangan lalu klik \"Buat Penempatan Otomatis\" atau \"Atur Penempatan Manual\".");
            }
        } catch (Exception ex) {
            error(ex);
        }
        updateRoomHint();
        updateButtons();
    }

    private void toggleAllRooms() {
        boolean selectAll = roomChecked.values().stream().anyMatch(p -> !p.get());
        roomChecked.values().forEach(p -> p.set(selectAll));
    }

    private List<Room> selectedRooms() {
        return listRooms.getItems().stream()
                .map(RoomAvailability::getRoom)
                .filter(r -> roomChecked.get(r.getId()) != null && roomChecked.get(r.getId()).get())
                .collect(Collectors.toList());
    }

    // Centang otomatis ruangan kosong secukupnya untuk semua siswa di sesi ini
    private void autoSelectRooms() {
        if (currentSession == null) {
            return;
        }
        try {
            int needed = service.countSeatsNeeded(currentSession);
            List<Room> suggested = service.suggestRooms(currentSession);
            if (suggested.isEmpty()) {
                new Alert(AlertType.WARNING, needed == 0
                        ? "Tidak ada siswa pada sesi ini."
                        : "Tidak ada ruangan kosong pada sesi ini.").showAndWait();
                return;
            }
            roomChecked.values().forEach(p -> p.set(false));
            suggested.forEach(r -> roomChecked.get(r.getId()).set(true));

            int capacity = suggested.stream().mapToInt(Room::getCapacity).sum();
            String text = "Dipilih otomatis " + suggested.size() + " ruangan (kapasitas " + capacity + ") untuk "
                    + needed + " siswa. Klik \"Buat Penempatan Otomatis\" untuk membagi siswa.";
            if (capacity < needed) {
                text = "Semua ruangan kosong dipilih, tetapi kapasitasnya (" + capacity + ") kurang dari jumlah siswa ("
                        + needed + "). Tambah kapasitas ruangan atau pindahkan sebagian jadwal ke sesi lain.";
                new Alert(AlertType.WARNING, text).showAndWait();
            }
            if (currentPlan == null) {
                lblStatus.setText(text);
            }
        } catch (Exception ex) {
            error(ex);
        }
    }

    // Baris daftar ruangan: ruangan kosong = checkbox; ruangan terpakai = abu-abu + keterangan pemakainya
    private class RoomCell extends ListCell<RoomAvailability> {
        private final CheckBox checkBox = new CheckBox();
        private final Label usedBy = new Label();
        private final VBox box = new VBox(2, checkBox, usedBy);
        private BooleanProperty bound;

        RoomCell() {
            usedBy.setWrapText(true);
            usedBy.setStyle("-fx-text-fill: #9e9e9e; -fx-font-size: 11px; -fx-padding: 0 0 0 22;");
        }

        @Override
        protected void updateItem(RoomAvailability item, boolean empty) {
            super.updateItem(item, empty);
            if (bound != null) {
                checkBox.selectedProperty().unbindBidirectional(bound);
                bound = null;
            }
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                setTooltip(null);
                return;
            }
            Room room = item.getRoom();
            checkBox.setText(room.getName() + "  (kapasitas " + room.getCapacity() + ")");
            if (item.isAvailable()) {
                checkBox.setDisable(false);
                checkBox.setStyle("");
                bound = roomChecked.get(room.getId());
                checkBox.selectedProperty().bindBidirectional(bound);
                usedBy.setVisible(false);
                usedBy.setManaged(false);
                setTooltip(null);
            } else {
                checkBox.setSelected(false);
                checkBox.setDisable(true);
                checkBox.setStyle("-fx-opacity: 0.6;");
                usedBy.setText("Dipakai: " + item.getUsedBy());
                usedBy.setVisible(true);
                usedBy.setManaged(true);
                setTooltip(new Tooltip("Ruangan ini dipakai sesi lain yang waktunya bertumpuk:\n" + item.getUsedBy()));
            }
            setText(null);
            setGraphic(box);
        }
    }

    private void updateRoomHint() {
        // pesan "ruangan tidak ditampilkan" tetap; ringkasan kapasitas ditambahkan di status hanya saat belum ada rencana
        if (currentPlan == null && currentSession != null) {
            List<Room> chosen = selectedRooms();
            int capacity = chosen.stream().mapToInt(Room::getCapacity).sum();
            if (!chosen.isEmpty()) {
                lblStatus.setText(chosen.size() + " ruangan dipilih, total kapasitas " + capacity + " kursi.");
            }
        }
    }

    // ------------------------------------------------------------------ aksi

    private void generate() {
        if (currentSession == null) {
            return;
        }
        List<Room> rooms = selectedRooms();
        if (rooms.isEmpty()) {
            new Alert(AlertType.WARNING, "Centang minimal satu ruangan terlebih dahulu.").showAndWait();
            return;
        }
        try {
            RoomPlan plan = service.generate(currentSession, rooms);
            showPlan(plan, false);
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
        }
    }

    // Penempatan manual: admin menentukan jumlah siswa tiap kelas per ruangan
    private void manual() {
        if (currentSession == null) {
            return;
        }
        List<Room> rooms = selectedRooms();
        if (rooms.isEmpty()) {
            new Alert(AlertType.WARNING, "Centang dulu ruangan yang akan dipakai (daftar di kiri), lalu klik "
                    + "\"Atur Penempatan Manual\" lagi.").showAndWait();
            return;
        }
        try {
            java.util.Map<Integer, Integer> counts = service.getStudentCounts(currentSession);
            java.util.Map<Integer, java.util.Map<Integer, Integer>> distribution = co.id.component.ManualPlacementDialog.show(
                    (javafx.scene.layout.Region) btnManual.getScene().getRoot(), currentSession, counts, rooms, currentPlan);
            if (distribution == null) {
                return;
            }
            RoomPlan plan = service.buildManual(currentSession, distribution, rooms);
            // pengawas yang sudah dipilih untuk ruangan yang sama tetap dipakai
            if (currentPlan != null) {
                for (RoomAssignment assignment : plan.getRooms()) {
                    for (RoomAssignment old : currentPlan.getRooms()) {
                        if (old.getRoom().getId() == assignment.getRoom().getId()) {
                            assignment.setSupervisor(old.getSupervisor());
                        }
                    }
                }
            }
            showPlan(plan, false);
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
        }
    }

    // Pindahkan siswa yang dipilih di tabel kursi ke ruangan lain
    private void moveSeat() {
        SeatAssignment seat = tableSeats.getSelectionModel().getSelectedItem();
        RoomAssignment from = tableRooms.getSelectionModel().getSelectedItem();
        if (currentPlan == null || seat == null || from == null) {
            new Alert(AlertType.INFORMATION, "Klik satu ruangan, lalu pilih siswa di daftar kursi.").showAndWait();
            return;
        }
        // tujuan: ruangan yang sudah dipakai di rencana ini + ruangan lain yang dicentang
        java.util.Map<Integer, Room> options = new java.util.LinkedHashMap<>();
        currentPlan.getRooms().forEach(r -> options.put(r.getRoom().getId(), r.getRoom()));
        selectedRooms().forEach(r -> options.putIfAbsent(r.getId(), r));
        options.remove(from.getRoom().getId());
        if (options.isEmpty()) {
            new Alert(AlertType.INFORMATION, "Tidak ada ruangan lain. Centang ruangan tambahan di daftar kiri.").showAndWait();
            return;
        }
        java.util.Map<String, Room> byLabel = new java.util.LinkedHashMap<>();
        options.values().stream()
                .sorted((a, b) -> co.id.component.ManualPlacementDialog.naturalCompare(a.getName(), b.getName()))
                .forEach(r -> {
                    int filled = currentPlan.getRooms().stream().filter(x -> x.getRoom().getId() == r.getId())
                            .mapToInt(RoomAssignment::getFilled).sum();
                    byLabel.put(r.getName() + "  (" + filled + "/" + r.getCapacity() + ")", r);
                });
        javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(
                byLabel.keySet().iterator().next(), byLabel.keySet());
        dialog.setTitle("Pindahkan Siswa");
        dialog.setHeaderText("Pindahkan " + seat.getStudent().getName() + " (" + seat.getClassName() + ") dari "
                + from.getRoom().getName() + " ke:");
        dialog.setContentText("Ruangan:");
        java.util.Optional<String> choice = dialog.showAndWait();
        if (choice.isEmpty()) {
            return;
        }
        try {
            service.moveSeat(currentPlan, seat, byLabel.get(choice.get()));
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }
        RoomAssignment keep = currentPlan.getRooms().contains(from) ? from : null;
        tableRooms.setItems(FXCollections.observableArrayList(currentPlan.getRooms()));
        tableRooms.refresh();
        if (keep != null) {
            tableRooms.getSelectionModel().select(keep);
            showSeats(keep);
        } else if (!currentPlan.getRooms().isEmpty()) {
            tableRooms.getSelectionModel().selectFirst();
        }
        markUnsaved();
    }

    // Salin susunan kelas -> ruangan sesi ini ke sesi lain di periode yang sama
    private void applyToOtherSessions() {
        if (currentPlan == null || currentSession == null) {
            return;
        }
        if (!saved) {
            new Alert(AlertType.INFORMATION, "Simpan dulu penempatan sesi ini, baru terapkan ke sesi lain.").showAndWait();
            return;
        }
        co.id.model.ExamSchedule first = currentSession.getSchedules().get(0);
        try {
            List<ExamSession> sessions = service.getSessionsInPeriod(first.getExamType(), first.getSemester(), first.getAcademicYear());
            List<co.id.component.ManualPlacementDialog.Target> targets = new java.util.ArrayList<>();
            for (ExamSession session : sessions) {
                if (session.getDate().equals(currentSession.getDate()) && session.getStartTime().equals(currentSession.getStartTime())
                        && session.getEndTime().equals(currentSession.getEndTime())) {
                    continue;
                }
                targets.add(new co.id.component.ManualPlacementDialog.Target(session, service.loadSaved(session) != null));
            }
            if (targets.isEmpty()) {
                new Alert(AlertType.INFORMATION, "Tidak ada sesi lain pada periode " + first.getExamType() + " "
                        + first.getSemester() + " " + first.getAcademicYear() + ".").showAndWait();
                return;
            }
            String label = currentSession.getDate() + " " + currentSession.getStartTime().format(TIME) + "-"
                    + currentSession.getEndTime().format(TIME);
            List<ExamSession> chosen = co.id.component.ManualPlacementDialog.pickSessions(
                    (javafx.scene.layout.Region) btnApply.getScene().getRoot(), label, targets);
            if (chosen == null || chosen.isEmpty()) {
                return;
            }
            List<String> report = service.applyLayout(currentPlan, chosen, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
            TextArea area = new TextArea(String.join("\n", report));
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(Math.min(18, report.size() + 1));
            Alert done = new Alert(AlertType.INFORMATION);
            done.setTitle("Terapkan ke Sesi Lain");
            done.setHeaderText(report.stream().filter(r -> r.startsWith("OK")).count() + " sesi berhasil, "
                    + report.stream().filter(r -> !r.startsWith("OK")).count() + " dilewati. "
                    + "Pengawas tiap sesi tetap perlu dicek/diisi.");
            done.getDialogPane().setContent(area);
            done.showAndWait();
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void autoSupervisors() {
        if (currentPlan == null) {
            return;
        }
        try {
            service.assignSupervisorsAutomatically(currentPlan);
            markUnsaved();
            tableRooms.refresh();
            showWarnings();
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void save() {
        if (currentPlan == null) {
            new Alert(AlertType.INFORMATION, "Buat penempatan terlebih dahulu.").showAndWait();
            return;
        }

        boolean missing = currentPlan.getRooms().stream().anyMatch(r -> r.getSupervisor() == null);
        String message = "Menyimpan akan menetapkan ruangan dan kursi tiap siswa, mendaftarkan siswa yang belum menjadi peserta, "
                + "serta mengosongkan ruangan dan pengawas pada jadwal-jadwal sesi ini (kini diatur per ruangan)."
                + (missing ? "\n\nAda ruangan yang belum punya pengawas." : "")
                + "\n\nLanjutkan?";
        Alert confirm = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Simpan Penempatan");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            service.save(currentPlan, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }
        new Alert(AlertType.INFORMATION, "Penempatan ruangan tersimpan: " + currentPlan.getTotalStudents()
                + " siswa di " + currentPlan.getRooms().size() + " ruangan.").showAndWait();
        openSession(currentSession);
    }

    private void export() {
        if (currentPlan == null) {
            new Alert(AlertType.INFORMATION, "Buat atau buka penempatan terlebih dahulu.").showAndWait();
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Simpan Daftar Hadir per Ruangan");
        chooser.setInitialFileName("daftar-hadir-ruangan-" + currentPlan.getDate() + "-"
                + currentPlan.getStartTime().format(DateTimeFormatter.ofPattern("HHmm")) + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        File file = chooser.showSaveDialog(btnExport.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            RoomAttendanceExcelWriter.write(file, currentPlan);
            new Alert(AlertType.INFORMATION, "Daftar hadir tersimpan di:\n" + file.getAbsolutePath()).showAndWait();
        } catch (Exception ex) {
            error(ex);
        }
    }

    // ------------------------------------------------------------------ tampilan

    private void showPlan(RoomPlan plan, boolean fromDatabase) {
        currentPlan = plan;
        saved = fromDatabase;
        tableRooms.setItems(FXCollections.observableArrayList(plan.getRooms()));
        tableRooms.refresh();
        tableSeats.getItems().clear();
        lblSeatsTitle.setText("Daftar kursi: klik satu ruangan di atas");
        if (!plan.getRooms().isEmpty()) {
            tableRooms.getSelectionModel().selectFirst();
        }
        showWarnings();
        updateStatus();
        updateButtons();
    }

    private void showSeats(RoomAssignment room) {
        if (room == null) {
            tableSeats.getItems().clear();
            return;
        }
        tableSeats.setItems(FXCollections.observableArrayList(room.getSeats()));
        lblSeatsTitle.setText("Daftar kursi " + room.getRoom().getName() + " (" + room.getFilled() + " dari "
                + room.getRoom().getCapacity() + " kursi)");
    }

    private void showWarnings() {
        areaWarnings.setText(currentPlan == null ? "" : currentPlan.getWarnings().stream()
                .map(w -> "- " + w).collect(Collectors.joining("\n")));
    }

    private void markUnsaved() {
        saved = false;
        updateStatus();
    }

    private void updateStatus() {
        if (currentPlan == null) {
            return;
        }
        String base = currentPlan.getTotalStudents() + " siswa di " + currentPlan.getRooms().size() + " ruangan. ";
        lblStatus.setText(base + (saved ? "Tersimpan." : "BELUM DISIMPAN."));
        lblStatus.setStyle(saved ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #e65100;");
    }

    private void clearPlan() {
        currentPlan = null;
        saved = false;
        tableRooms.getItems().clear();
        tableSeats.getItems().clear();
        areaWarnings.clear();
        lblStatus.setText("");
        lblSeatsTitle.setText("Daftar kursi: klik satu ruangan di atas");
        updateButtons();
    }

    private void updateButtons() {
        boolean hasPlan = currentPlan != null;
        btnAutoSupervisor.setDisable(!hasPlan || !AuthContext.isAdmin());
        btnExport.setDisable(!hasPlan);
        btnSave.setDisable(!hasPlan || !AuthContext.isAdmin());
        btnGenerate.setDisable(currentSession == null || !AuthContext.isAdmin());
        btnAutoRooms.setDisable(currentSession == null || !AuthContext.isAdmin());
        btnManual.setDisable(currentSession == null || !AuthContext.isAdmin());
        btnMoveSeat.setDisable(!hasPlan || !AuthContext.isAdmin() || tableSeats.getSelectionModel().getSelectedItem() == null);
        btnApply.setDisable(!hasPlan || !AuthContext.isAdmin());
    }

    private Teacher teacherFor(Teacher supervisor) {
        if (supervisor == null) {
            return noTeacher;
        }
        for (Teacher teacher : teacherItems) {
            if (teacher.getId() == supervisor.getId()) {
                return teacher;
            }
        }
        return noTeacher;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void error(Exception ex) {
        new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
    }
}