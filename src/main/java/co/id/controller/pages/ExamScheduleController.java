package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.ExcelImportActions;
import co.id.model.ExamSchedule;
import co.id.model.PlacementSummary;
import co.id.service.ExamScheduleService;
import co.id.service.impl.ExamScheduleServiceImpl;
import java.io.IOException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ExamScheduleController {
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private Button importBtn;
    @FXML private Button templateBtn;
    @FXML private Button modeBtn;
    @FXML private TableView<ExamSchedule> tableView;

    @FXML private TableColumn<ExamSchedule, String> tableColumnCode, tableColumnType, tableColumnAcademicYear,
            tableColumnDate, tableColumnStartTime, tableColumnEndTime, tableColumnSubject, tableColumnClassroom,
            tableColumnRoom, tableColumnTeacher, tableColumnStatus;
    @FXML private TableColumn<ExamSchedule, Void> tableColumnAction;

    // Ringkasan penempatan (ruangan, pengawas, status) untuk baris yang sedang tampil, per id jadwal
    private java.util.Map<Integer, PlacementSummary> summaries = new java.util.HashMap<>();

    @FXML private Pagination pagination;

    private ExamScheduleService examScheduleService;
    private ObservableList<ExamSchedule> observableList;

    private ImageView createIcon(String path) {
        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        return imageView;
    }

    @FXML
    public void initialize() {
        examScheduleService = new ExamScheduleServiceImpl();
        observableList = FXCollections.observableArrayList();

        if (!AuthContext.isAdmin()) {
            addBtn.setVisible(false);
            addBtn.setManaged(false);
            importBtn.setVisible(false);
            importBtn.setManaged(false);
            templateBtn.setVisible(false);
            templateBtn.setManaged(false);
            modeBtn.setVisible(false);
            modeBtn.setManaged(false);
        }

        // tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tableColumnCode.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getCode()));
        tableColumnType.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getExamType()));
        tableColumnAcademicYear.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getAcademicYear()));
        tableColumnDate.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getDate() != null ? e.getValue().getDate().toString() : "-"));
        tableColumnStartTime.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getStartTime() != null ? e.getValue().getStartTime().toString() : "-"));
        tableColumnEndTime.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getEndTime() != null ? e.getValue().getEndTime().toString() : "-"));
        tableColumnSubject.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getSubject() != null ? e.getValue().getSubjectLabel() : "-"));
        tableColumnClassroom.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getClassroom() != null ? e.getValue().getClassroom().getName() : "-"));
        // Ruangan, pengawas, dan status diambil dari hasil menu Penempatan Ruangan (bukan dari form jadwal)
        tableColumnRoom.setCellValueFactory(e -> new SimpleStringProperty(summaryOf(e.getValue()).getRoomsLabel()));
        tableColumnRoom.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                ExamSchedule row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                    return;
                }
                PlacementSummary summary = summaryOf(row);
                setText(text);
                setTooltip(new Tooltip(summary.getRoomsDetail()));
                setStyle(summary.getRooms().isEmpty() ? "-fx-text-fill: #9e9e9e; -fx-font-style: italic;" : "");
            }
        });
        tableColumnTeacher.setCellValueFactory(e -> new SimpleStringProperty(summaryOf(e.getValue()).getSupervisorsLabel()));
        tableColumnStatus.setCellValueFactory(e -> new SimpleStringProperty(summaryOf(e.getValue()).getStatusLabel()));
        tableColumnStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                ExamSchedule row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(text);
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + statusColor(summaryOf(row).getStatus()) + ";");
            }
        });

        tableColumnAction.setCellFactory(clbck -> new TableCell<>() {
        private final Button buttonEdit = new Button("Edit");
        private final Button buttonDuplicate = new Button("Duplikat");
        private final Button buttonDelete = new Button("Hapus");
        private final HBox box = new HBox(5, buttonEdit, buttonDuplicate, buttonDelete);
        {
            buttonEdit.setGraphic(createIcon("/icons/edit.png"));
            buttonDuplicate.setGraphic(createIcon("/icons/copy.png"));
            buttonDelete.setGraphic(createIcon("/icons/trash.png"));

            buttonEdit.getStyleClass().add("btn-edit");
            buttonDuplicate.getStyleClass().add("btn-duplicate");
            buttonDelete.getStyleClass().add("btn-delete");

            if (!AuthContext.isAdmin()) {
                buttonEdit.setVisible(false);
                buttonEdit.setManaged(false);
                buttonDuplicate.setVisible(false);
                buttonDuplicate.setManaged(false);
                buttonDelete.setVisible(false);
                buttonDelete.setManaged(false);
            }

            buttonEdit.setOnAction(eh -> {
                ExamSchedule examSchedule = getTableView().getItems().get(getIndex());
                openForm(examSchedule, false);
            });

            buttonDuplicate.setOnAction(eh -> {
                ExamSchedule examSchedule = getTableView().getItems().get(getIndex());
                openForm(examSchedule, true);
            });

            buttonDelete.setOnAction(eh -> {
                ExamSchedule examSchedule = getTableView().getItems().get(getIndex());

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Yakin ingin menghapus jadwal ujian \"" + examSchedule.getCode() + "\"?\n"
                                + "Semua peserta dan nilai yang terdaftar di sesi ini akan ikut terhapus.",
                        ButtonType.YES, ButtonType.NO);
                confirm.setTitle("Konfirmasi Hapus");
                confirm.setHeaderText(null);

                confirm.showAndWait().ifPresent(action -> {
                    if (action == ButtonType.YES) {
                        try {
                            examScheduleService.delete(examSchedule.getId(), AuthContext.getCurrentRole());
                            refreshTable();
                        } catch (Exception ex) {
                            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                        }
                    }
                });
            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            setGraphic(empty ? null : box);
        }
    });

        int totalRows = examScheduleService.countExamSchedules();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);

        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setPageFactory(clbck -> {
            loadPage(clbck, rowsPerPage);
            return new VBox(tableView);
        });

        filterBtn.setOnAction(eh -> filterItems());
        addBtn.setOnAction(eh -> openForm(null, false));
        importBtn.setOnAction(eh -> ExcelImportActions.importExamSchedules(
                (Region) importBtn.getScene().getRoot(), importBtn.getScene().getWindow(), this::refreshTable));
        templateBtn.setOnAction(eh -> ExcelImportActions.saveExamScheduleTemplate(templateBtn.getScene().getWindow()));
        modeBtn.setOnAction(eh -> changeExamMode());
        refreshBtn.setOnAction(e -> refreshTable());
    }

    private void filterItems() {
        String keyword = searchField.getText();

        if (keyword == null || keyword.isEmpty()) {
            refreshTable();
        } else {
            showSchedules(examScheduleService.getExamScheduleBy(keyword));
        }
    }

    // Ambil ringkasan penempatan untuk baris yang akan tampil, lalu tampilkan barisnya
    private void showSchedules(java.util.List<ExamSchedule> schedules) {
        try {
            summaries = examScheduleService.getPlacementSummaries(
                    schedules.stream().map(ExamSchedule::getId).collect(java.util.stream.Collectors.toList()));
        } catch (Exception ex) {
            summaries = new java.util.HashMap<>();
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
        }
        observableList.setAll(schedules);
        tableView.setItems(observableList);
        tableView.refresh();
    }

    // Ubah banyak jadwal sekaligus menjadi Online / Offline
    private void changeExamMode() {
        Region root = (Region) modeBtn.getScene().getRoot();
        co.id.component.ExamModeDialog.Result result;
        try {
            result = co.id.component.ExamModeDialog.show(root, examScheduleService.getAllExamSchedulesDetailed());
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            return;
        }
        if (result == null || result.getSchedules().isEmpty()) {
            return;
        }

        java.util.List<Integer> ids = new java.util.ArrayList<>();
        result.getSchedules().forEach(s -> ids.add(s.getId()));
        boolean toOnline = ExamSchedule.MODE_ONLINE.equals(result.getMode());

        StringBuilder message = new StringBuilder(ids.size() + " jadwal akan dijadikan " + result.getMode() + ".");
        if (toOnline) {
            long placed = examScheduleService.getPlacementSummaries(ids).values().stream()
                    .filter(s -> !s.getRooms().isEmpty()).count();
            message.append("\n\nJadwal Online tidak memakai ruangan dan pengawas.");
            if (placed > 0) {
                message.append(" ").append(placed).append(" jadwal di antaranya sudah ditempatkan; ruangan, nomor kursi, ")
                        .append("dan pengawasnya akan dikosongkan.");
            }
        } else {
            message.append("\n\nSetelah ini atur ruangan dan pengawasnya lewat menu Penempatan Ruangan.");
        }
        message.append("\n\nLanjutkan?");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message.toString(), ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Ubah Mode Ujian");
        confirm.setHeaderText(null);
        if (!confirm.showAndWait().map(r -> r == ButtonType.YES).orElse(false)) {
            return;
        }

        try {
            int changed = examScheduleService.changeMode(ids, result.getMode(),
                    AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
            new Alert(Alert.AlertType.INFORMATION, changed + " jadwal sekarang " + result.getMode() + ".").showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
        }
        refreshTable();
    }

    private PlacementSummary summaryOf(ExamSchedule schedule) {
        return summaries.getOrDefault(schedule.getId(), new PlacementSummary(schedule.getId()));
    }

    private static String statusColor(PlacementSummary.Status status) {
        switch (status) {
            case SIAP:
                return "#2e7d32";   // hijau
            case TANPA_PENGAWAS:
            case SEBAGIAN:
                return "#ef6c00";   // oranye
            case TANPA_SISWA:
                return "#757575";   // abu-abu
            case ONLINE:
                return "#1565c0";   // biru: ujian online, tanpa ruangan
            default:
                return "#c62828";   // merah: belum ditempatkan
        }
    }

    private void openForm(ExamSchedule examSchedule, boolean isDuplicate) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/ExamScheduleForm.fxml"));
            Parent formRoot = loader.load();

            ExamScheduleFormController formController = loader.getController();

            if (examSchedule != null) {
                formController.setExamSchedule(examSchedule, isDuplicate);
            }

            formController.setOnSaveCallback(this::refreshTable);

            Region mainRoot = (Region) addBtn.getScene().getRoot();
            mainRoot.setEffect(new GaussianBlur(10));

            Stage dialog = new Stage();
            dialog.setTitle(examSchedule == null ? "Tambah Jadwal Ujian"
                    : (isDuplicate ? "Duplikat Jadwal Ujian" : "Edit Jadwal Ujian"));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);

            Scene scene = new Scene(formRoot, 460, 620);
            scene.getStylesheets().add(getClass().getResource("/css/material.css").toExternalForm());

            dialog.setScene(scene);
            dialog.centerOnScreen();

            dialog.setOnHidden(eh -> mainRoot.setEffect(null));

            dialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPage(int pageIndex, int rowsPerPage) {
        showSchedules(examScheduleService.getExamSchedules(pageIndex + 1, rowsPerPage));
    }

    private void refreshTable() {
        int totalRows = examScheduleService.countExamSchedules();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        loadPage(pagination.getCurrentPageIndex(), rowsPerPage);
    }
}