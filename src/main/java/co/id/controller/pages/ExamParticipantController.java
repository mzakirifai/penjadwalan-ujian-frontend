package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.EditableCell;
import co.id.component.BulkRegisterDialog;
import co.id.component.LookupBox;
import co.id.component.ParticipantPickerDialog;
import co.id.model.ExamParticipant;
import co.id.model.ExamSchedule;
import co.id.model.Student;
import co.id.service.ExamParticipantService;
import co.id.service.ExamScheduleService;
import co.id.service.MasterService;
import co.id.service.impl.ExamParticipantServiceImpl;
import co.id.service.impl.ExamScheduleServiceImpl;
import co.id.service.impl.MasterServiceImpl;
import java.io.IOException;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ExamParticipantController {
    @FXML private LookupBox<ExamSchedule> lookupBoxExamSchedule;
    @FXML private Label lblCapacityInfo;
    @FXML private Button registerAllBtn;
    @FXML private Button bulkRegisterBtn;
    @FXML private Button addBtn;
    @FXML private TableView<ExamParticipant> tableView;
    @FXML private TextField searchField;
    @FXML private Button filterBtn;

    @FXML private TableColumn<ExamParticipant, String> tableColumnAdminStatus;
    @FXML private TableColumn<ExamParticipant, String> tableColumnCode, tableColumnNis, tableColumnName,
            tableColumnParticipantNumber, tableColumnSeatNumber, tableColumnRoom;
    @FXML private TableColumn<ExamParticipant, Void> tableColumnAction;

    private ExamParticipantService examParticipantService;
    private ExamScheduleService examScheduleService;
    private MasterService masterService;
    private ObservableList<ExamParticipant> observableList;

    private ExamSchedule selectedExamSchedule;
    private int editingRowIndex = -1;

    private ImageView createIcon(String path) {
        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        return imageView;
    }

    @FXML
    public void initialize() {
        examParticipantService = new ExamParticipantServiceImpl();
        examScheduleService = new ExamScheduleServiceImpl();
        masterService = new MasterServiceImpl();
        observableList = FXCollections.observableArrayList();

        if (!AuthContext.isAdmin()) {
            addBtn.setVisible(false);
            addBtn.setManaged(false);
            registerAllBtn.setVisible(false);
            registerAllBtn.setManaged(false);
            bulkRegisterBtn.setVisible(false);
            bulkRegisterBtn.setManaged(false);
        }
        bulkRegisterBtn.setOnAction(e -> registerManySchedules());

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ExamSchedule, String> colScheduleLabel = new TableColumn<>("Jadwal Ujian");
        colScheduleLabel.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getCode() + " - "
                        + (e.getValue().getSubject() != null ? e.getValue().getSubjectLabel() : "-")
                        + " - " + (e.getValue().getClassroom() != null ? e.getValue().getClassroom().getName() : "-")
        ));
        lookupBoxExamSchedule.configure(
                () -> examScheduleService.getAllExamSchedulesDetailed(),
                List.of(colScheduleLabel),
                es -> es.getCode() + " - " + (es.getSubject() != null ? es.getSubjectLabel() : "-")
                        + " - " + (es.getClassroom() != null ? es.getClassroom().getName() : "-")
        );
        lookupBoxExamSchedule.setFieldWidth(300);

        lookupBoxExamSchedule.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedExamSchedule = newVal;
            editingRowIndex = -1;
            refreshTable();
        });

        tableColumnCode.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getCode()));
        tableColumnNis.setCellValueFactory(p ->
                new SimpleStringProperty(p.getValue().getStudent() != null ? p.getValue().getStudent().getNis() : "-"));
        tableColumnName.setCellValueFactory(p ->
                new SimpleStringProperty(p.getValue().getStudent() != null ? p.getValue().getStudent().getName() : "-"));

        // Tanda "Belum Lunas" untuk peserta yang status administrasinya belum lunas (keputusan tetap di admin)
        tableColumnAdminStatus.setCellValueFactory(p -> {
            Student st = p.getValue().getStudent();
            if (st == null) {
                return new SimpleStringProperty("-");
            }
            String text = st.getAdminStatus() == null ? Student.ADMIN_PAID : st.getAdminStatus();
            if (st.getAdminNote() != null && !st.getAdminNote().isBlank()) {
                text += " (" + st.getAdminNote() + ")";
            }
            return new SimpleStringProperty(text);
        });
        tableColumnAdminStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(!empty && item != null && item.startsWith(Student.ADMIN_UNPAID)
                        ? "-fx-text-fill: #c62828; -fx-font-weight: bold;" : "");
            }
        });

        // Ruangan peserta hasil Penempatan Ruangan; "-" bila belum ditempatkan
        tableColumnRoom.setCellValueFactory(p -> new SimpleStringProperty(
                p.getValue().getRoom() != null ? p.getValue().getRoom().getName() : "-"));

        tableColumnParticipantNumber.setCellFactory(tc ->
                new EditableCell<ExamParticipant>(ExamParticipant::getParticipantNumber,
                        ExamParticipant::setParticipantNumber, () -> editingRowIndex));

        // No. Kursi hasil Penempatan Ruangan (tidak bisa diedit di sini); "-" bila belum ditempatkan
        tableColumnSeatNumber.setCellValueFactory(p -> new SimpleStringProperty(
                p.getValue().getSeatNumber() != null ? p.getValue().getSeatNumber() : "-"));

        tableColumnAction.setCellFactory(params -> new TableCell<>() {
            private final Button btnEdit = new Button("Edit");
            private final Button btnDelete = new Button("Hapus");
            private final Button btnSave = new Button("Simpan");
            private final Button btnCancel = new Button("Batal");
            private final HBox boxView = new HBox(5, btnEdit, btnDelete);
            private final HBox boxEdit = new HBox(5, btnSave, btnCancel);

            {
                btnEdit.setGraphic(createIcon("/icons/edit.png"));
                btnDelete.setGraphic(createIcon("/icons/trash.png"));
                btnSave.setGraphic(createIcon("/icons/save.png"));
                btnCancel.setGraphic(createIcon("/icons/cancel.png"));

                btnEdit.getStyleClass().add("btn-edit");
                btnDelete.getStyleClass().add("btn-delete");
                btnSave.getStyleClass().add("btn-save");
                btnCancel.getStyleClass().add("btn-cancel");

                if (!AuthContext.isAdmin()) {
                    btnEdit.setVisible(false);
                    btnEdit.setManaged(false);
                    btnDelete.setVisible(false);
                    btnDelete.setManaged(false);
                }

                btnEdit.setOnAction(e -> {
                    editingRowIndex = getIndex();
                    tableView.refresh();
                });

                btnDelete.setOnAction(e -> {
                    ExamParticipant participant = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Yakin ingin menghapus peserta \"" + participant.getStudent().getName() + "\" dari ujian ini?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            try {
                                examParticipantService.delete(participant.getId(), AuthContext.getCurrentRole());
                                refreshTable();
                            } catch (Exception ex) {
                                new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
                            }
                        }
                    });
                });

                btnSave.setOnAction(e -> {
                    ExamParticipant participant = getTableView().getItems().get(getIndex());

                    if (participant.getParticipantNumber() == null || participant.getParticipantNumber().isBlank()) {
                        new Alert(AlertType.WARNING, "No. Peserta wajib diisi").showAndWait();
                        return;
                    }

                    if (!participant.getParticipantNumber().matches("\\d+")) {
                        new Alert(AlertType.WARNING, "No. Peserta harus berupa angka (contoh: 001)").showAndWait();
                        return;
                    }

                    try {
                        examParticipantService.save(participant, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
                    } catch (Exception ex) {
                        new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
                        return;
                    }

                    editingRowIndex = -1;
                    refreshTable();
                });

                btnCancel.setOnAction(e -> {
                    editingRowIndex = -1;
                    refreshTable();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(getIndex() == editingRowIndex ? boxEdit : boxView);
                }
            }
        });

        filterBtn.setOnAction(e -> filterParticipants());
        addBtn.setOnAction(e -> openAddForm());
        registerAllBtn.setOnAction(e -> registerAllStudents());
    }
    
    private void filterParticipants() {
        if (selectedExamSchedule == null) {
            return;
        }

        String keyword = searchField.getText();
        List<ExamParticipant> all = examParticipantService.getByExamSchedule(selectedExamSchedule.getId());

        if (keyword == null || keyword.isBlank()) {
            observableList.setAll(all);
        } else {
            String lowerKeyword = keyword.toLowerCase();
            List<ExamParticipant> filtered = all.stream()
                    .filter(p -> (p.getStudent().getName() != null && p.getStudent().getName().toLowerCase().contains(lowerKeyword))
                            || (p.getStudent().getNis() != null && p.getStudent().getNis().toLowerCase().contains(lowerKeyword)))
                    .toList();
            observableList.setAll(filtered);
        }

        tableView.setItems(observableList);
    }

    private void refreshTable() {
        tableView.setItems(FXCollections.observableArrayList());

        if (selectedExamSchedule == null) {
            lblCapacityInfo.setText("");
            return;
        }

        observableList.setAll(examParticipantService.getByExamSchedule(selectedExamSchedule.getId()));
        tableView.setItems(observableList);

        // Ruangan tidak lagi melekat pada jadwal: tampilkan jumlah peserta + ruangan hasil Penempatan Ruangan
        int registered = examParticipantService.countByExamSchedule(selectedExamSchedule.getId());
        String rooms;
        try {
            co.id.model.PlacementSummary summary = examScheduleService
                    .getPlacementSummaries(java.util.List.of(selectedExamSchedule.getId()))
                    .get(selectedExamSchedule.getId());
            rooms = summary != null ? summary.getRoomsLabel() : "-";
        } catch (Exception ex) {
            rooms = "-";
        }
        long unpaid = observableList.stream()
                .filter(p -> p.getStudent() != null && p.getStudent().isAdminUnpaid()).count();
        lblCapacityInfo.setText("Terdaftar: " + registered + " peserta  |  Ruangan: " + rooms
                + (unpaid > 0 ? "  |  Belum lunas: " + unpaid : ""));
    }

    private void openAddForm() {
        if (selectedExamSchedule == null) {
            new Alert(AlertType.WARNING, "Pilih jadwal ujian terlebih dahulu").showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/ExamParticipantForm.fxml"));
            Parent formRoot = loader.load();

            ExamParticipantFormController formController = loader.getController();
            formController.setExamSchedule(selectedExamSchedule);
            formController.setOnSaveCallback(this::refreshTable);

            Region mainRoot = (Region) addBtn.getScene().getRoot();
            mainRoot.setEffect(new GaussianBlur(10));

            Stage dialog = new Stage();
            dialog.setTitle("Tambah Peserta Ujian");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);

            Scene scene = new Scene(formRoot, 400, 320);
            scene.getStylesheets().add(getClass().getResource("/css/material.css").toExternalForm());

            dialog.setScene(scene);
            dialog.centerOnScreen();

            dialog.setOnHidden(eh -> mainRoot.setEffect(null));

            dialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void registerAllStudents() {
        if (selectedExamSchedule == null) {
            new Alert(AlertType.WARNING, "Pilih jadwal ujian terlebih dahulu").showAndWait();
            return;
        }

        if (selectedExamSchedule.getClassroom() == null) {
            new Alert(AlertType.WARNING, "Jadwal ujian ini tidak memiliki data kelas").showAndWait();
            return;
        }

        List<Student> classStudents = masterService.getStudentsByClassroom(selectedExamSchedule.getClassroom().getId());
        List<ExamParticipant> alreadyRegistered = examParticipantService.getByExamSchedule(selectedExamSchedule.getId());

        List<Student> notRegisteredYet = classStudents.stream()
                .filter(s -> alreadyRegistered.stream().noneMatch(p -> p.getStudent().getId() == s.getId()))
                .toList();

        if (notRegisteredYet.isEmpty()) {
            new Alert(AlertType.INFORMATION, "Semua siswa di kelas ini sudah terdaftar sebagai peserta.").showAndWait();
            return;
        }

        // Admin memilih siswa yang ikut: Belum Lunas otomatis tidak dicentang, tetapi bisa dicentang manual
        String title = (selectedExamSchedule.getCode() != null ? selectedExamSchedule.getCode() + " - " : "")
                + (selectedExamSchedule.getSubject() != null ? selectedExamSchedule.getSubject().getName() : "")
                + " | Kelas: " + selectedExamSchedule.getClassroom().getName();
        List<Student> chosen = ParticipantPickerDialog.show((Region) registerAllBtn.getScene().getRoot(),
                title, notRegisteredYet, alreadyRegistered.size());
        if (chosen == null) {
            return;
        }
        if (chosen.isEmpty()) {
            new Alert(AlertType.INFORMATION, "Tidak ada siswa yang dicentang. Tidak ada yang didaftarkan.").showAndWait();
            return;
        }
        chosen = chosen.stream()
                .sorted(java.util.Comparator.comparing(s -> s.getName() == null ? "" : s.getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        StringBuilder failedMessages = new StringBuilder();
        int successCount = registerStudents(selectedExamSchedule, chosen, alreadyRegistered, failedMessages);

        refreshTable();

        String summary = "Berhasil mendaftarkan " + successCount + " dari " + chosen.size() + " siswa yang dipilih.";
        int skipped = notRegisteredYet.size() - chosen.size();
        if (skipped > 0) {
            summary += "\n" + skipped + " siswa tidak dicentang, jadi tidak didaftarkan.";
        }
        if (failedMessages.length() > 0) {
            summary += "\n\nGagal:\n" + failedMessages;
        }
        new Alert(AlertType.INFORMATION, summary).showAndWait();
    }

    /**
     * Daftarkan siswa (sudah berurutan nama) ke satu jadwal. Nomor peserta lanjut dari nomor terbesar yang sudah ada
     * (bukan dari jumlah peserta), supaya tidak bentrok bila ada peserta yang pernah dihapus.
     * @return jumlah yang berhasil; kegagalan ditambahkan ke failedMessages
     */
    private int registerStudents(ExamSchedule schedule, List<Student> students, List<ExamParticipant> alreadyRegistered,
                                 StringBuilder failedMessages) {
        int startingNumber = alreadyRegistered.stream()
                .map(ExamParticipant::getParticipantNumber)
                .filter(n -> n != null && n.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max().orElse(0) + 1;
        int successCount = 0;

        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);

            ExamParticipant participant = new ExamParticipant();
            participant.setExamSchedule(schedule);
            participant.setStudent(student);
            participant.setParticipantNumber(String.format("%03d", startingNumber + i));

            try {
                examParticipantService.save(participant, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
                successCount++;
            } catch (Exception ex) {
                failedMessages.append("- ").append(student.getName()).append(": ").append(ex.getMessage()).append("\n");
            }
        }
        return successCount;
    }

    /** Daftarkan peserta untuk banyak jadwal sekaligus; siswa Belum Lunas otomatis dilewati. */
    private void registerManySchedules() {
        List<BulkRegisterDialog.Plan> plans;
        java.util.Map<Integer, List<ExamParticipant>> participantsBySchedule;
        try {
            List<ExamSchedule> schedules = examScheduleService.getAllExamSchedulesDetailed();
            // getStudentBy("") dan getExamParticipantBy("") masing-masing satu query untuk semua data
            java.util.Map<Integer, List<Student>> studentsByClass = masterService.getStudentBy("").stream()
                    .filter(s -> s.getClassroom() != null)
                    .collect(java.util.stream.Collectors.groupingBy(s -> s.getClassroom().getId()));
            participantsBySchedule = examParticipantService.getExamParticipantBy("").stream()
                    .filter(p -> p.getExamSchedule() != null && p.getStudent() != null)
                    .collect(java.util.stream.Collectors.groupingBy(p -> p.getExamSchedule().getId()));
            java.util.Map<Integer, java.util.Set<Integer>> registeredIds = new java.util.HashMap<>();
            participantsBySchedule.forEach((id, list) -> registeredIds.put(id,
                    list.stream().map(p -> p.getStudent().getId()).collect(java.util.stream.Collectors.toSet())));
            plans = BulkRegisterDialog.buildPlans(schedules, studentsByClass, registeredIds);
        } catch (Exception ex) {
            new Alert(AlertType.ERROR, "Gagal memuat data jadwal: " + ex.getMessage()).showAndWait();
            return;
        }

        List<BulkRegisterDialog.Plan> chosen = BulkRegisterDialog.show((Region) bulkRegisterBtn.getScene().getRoot(), plans);
        if (chosen == null || chosen.isEmpty()) {
            return;
        }

        int totalSuccess = 0, totalStudents = 0, totalSkipped = 0;
        StringBuilder perSchedule = new StringBuilder();
        StringBuilder failedMessages = new StringBuilder();
        for (BulkRegisterDialog.Plan plan : chosen) {
            ExamSchedule schedule = plan.getSchedule();
            int ok = registerStudents(schedule, plan.getToRegister(),
                    participantsBySchedule.getOrDefault(schedule.getId(), List.of()), failedMessages);
            totalSuccess += ok;
            totalStudents += plan.getToRegister().size();
            totalSkipped += plan.getUnpaidSkipped().size();
            perSchedule.append("- ").append(schedule.getCode()).append(" ")
                    .append(schedule.getClassroom().getName()).append(": ").append(ok).append(" siswa")
                    .append(plan.getUnpaidSkipped().isEmpty() ? "" : ", " + plan.getUnpaidSkipped().size() + " belum lunas dilewati")
                    .append("\n");
        }

        refreshTable();

        String summary = "Berhasil mendaftarkan " + totalSuccess + " dari " + totalStudents + " siswa di "
                + chosen.size() + " jadwal." + (totalSkipped > 0 ? "\n" + totalSkipped + " siswa belum lunas dilewati." : "")
                + "\n\n" + perSchedule;
        if (failedMessages.length() > 0) {
            summary += "\nGagal:\n" + failedMessages;
        }
        Alert done = new Alert(AlertType.INFORMATION, summary);
        done.setHeaderText("Pendaftaran banyak jadwal selesai");
        done.setResizable(true);
        done.showAndWait();
    }
}