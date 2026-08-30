package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.EditableCell;
import co.id.component.LookupBox;
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
    @FXML private Button addBtn;
    @FXML private TableView<ExamParticipant> tableView;

    @FXML private TableColumn<ExamParticipant, String> tableColumnCode, tableColumnNis, tableColumnName,
            tableColumnParticipantNumber, tableColumnSeatNumber;
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
        }

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ExamSchedule, String> colScheduleLabel = new TableColumn<>("Jadwal Ujian");
        colScheduleLabel.setCellValueFactory(e -> new SimpleStringProperty(
                e.getValue().getCode() + " - "
                        + (e.getValue().getSubject() != null ? e.getValue().getSubject().getName() : "-")
                        + " - " + (e.getValue().getClassroom() != null ? e.getValue().getClassroom().getName() : "-")
        ));
        lookupBoxExamSchedule.configure(
                () -> examScheduleService.getAllExamSchedulesDetailed(),
                List.of(colScheduleLabel),
                es -> es.getCode() + " - " + (es.getSubject() != null ? es.getSubject().getName() : "-")
                        + " - " + (es.getClassroom() != null ? es.getClassroom().getName() : "-")
        );

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

        tableColumnParticipantNumber.setCellFactory(tc ->
                new EditableCell<ExamParticipant>(ExamParticipant::getParticipantNumber,
                        ExamParticipant::setParticipantNumber, () -> editingRowIndex));

        tableColumnSeatNumber.setCellFactory(tc ->
                new EditableCell<ExamParticipant>(ExamParticipant::getSeatNumber,
                        ExamParticipant::setSeatNumber, () -> editingRowIndex));

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

                    if (participant.getParticipantNumber() == null || participant.getParticipantNumber().isBlank()
                            || participant.getSeatNumber() == null || participant.getSeatNumber().isBlank()) {
                        new Alert(AlertType.WARNING, "No. Peserta dan No. Kursi wajib diisi").showAndWait();
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

        addBtn.setOnAction(e -> openAddForm());
        registerAllBtn.setOnAction(e -> registerAllStudents());
    }

    private void refreshTable() {
        tableView.setItems(FXCollections.observableArrayList());

        if (selectedExamSchedule == null) {
            lblCapacityInfo.setText("");
            return;
        }

        observableList.setAll(examParticipantService.getByExamSchedule(selectedExamSchedule.getId()));
        tableView.setItems(observableList);

        int registered = examParticipantService.countByExamSchedule(selectedExamSchedule.getId());
        int capacity = selectedExamSchedule.getRoom() != null ? selectedExamSchedule.getRoom().getCapacity() : 0;
        lblCapacityInfo.setText("Terdaftar: " + registered + " / " + capacity + " kursi");
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

        Alert confirm = new Alert(AlertType.CONFIRMATION,
                "Daftarkan " + notRegisteredYet.size() + " siswa dari kelas \""
                        + selectedExamSchedule.getClassroom().getName() + "\" sebagai peserta ujian ini?\n"
                        + "Nomor peserta & kursi akan digenerate otomatis secara berurutan.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Konfirmasi Pendaftaran Massal");
        confirm.setHeaderText(null);

        boolean proceed = confirm.showAndWait().map(r -> r == ButtonType.YES).orElse(false);
        if (!proceed) {
            return;
        }

        int startingNumber = alreadyRegistered.size() + 1;
        int successCount = 0;
        StringBuilder failedMessages = new StringBuilder();

        for (int i = 0; i < notRegisteredYet.size(); i++) {
            Student student = notRegisteredYet.get(i);
            int seq = startingNumber + i;

            ExamParticipant participant = new ExamParticipant();
            participant.setExamSchedule(selectedExamSchedule);
            participant.setStudent(student);
            participant.setParticipantNumber(String.format("%03d", seq));
            participant.setSeatNumber(String.format("K%02d", seq));

            try {
                examParticipantService.save(participant, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
                successCount++;
            } catch (Exception ex) {
                failedMessages.append("- ").append(student.getName()).append(": ").append(ex.getMessage()).append("\n");
            }
        }

        refreshTable();

        String summary = "Berhasil mendaftarkan " + successCount + " dari " + notRegisteredYet.size() + " siswa.";
        if (failedMessages.length() > 0) {
            summary += "\n\nGagal:\n" + failedMessages;
        }
        new Alert(AlertType.INFORMATION, summary).showAndWait();
    }
}