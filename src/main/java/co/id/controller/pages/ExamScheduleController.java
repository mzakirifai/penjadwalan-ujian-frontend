package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.ExamSchedule;
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
    @FXML private TableView<ExamSchedule> tableView;

    @FXML private TableColumn<ExamSchedule, String> tableColumnCode, tableColumnType, tableColumnDate,
            tableColumnStartTime, tableColumnEndTime, tableColumnSubject, tableColumnClassroom,
            tableColumnRoom, tableColumnTeacher;
    @FXML private TableColumn<ExamSchedule, Void> tableColumnAction;

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
        }

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tableColumnCode.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getCode()));
        tableColumnType.setCellValueFactory(e -> new SimpleStringProperty(e.getValue().getExamType()));
        tableColumnDate.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getDate() != null ? e.getValue().getDate().toString() : "-"));
        tableColumnStartTime.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getStartTime() != null ? e.getValue().getStartTime().toString() : "-"));
        tableColumnEndTime.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getEndTime() != null ? e.getValue().getEndTime().toString() : "-"));
        tableColumnSubject.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getSubject() != null ? e.getValue().getSubject().getName() : "-"));
        tableColumnClassroom.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getClassroom() != null ? e.getValue().getClassroom().getName() : "-"));
        tableColumnRoom.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getRoom() != null ? e.getValue().getRoom().getName() : "-"));
        tableColumnTeacher.setCellValueFactory(e ->
                new SimpleStringProperty(e.getValue().getTeacher() != null ? e.getValue().getTeacher().getName() : "-"));

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
        refreshBtn.setOnAction(e -> refreshTable());
    }

    private void filterItems() {
        String keyword = searchField.getText();

        if (keyword == null || keyword.isEmpty()) {
            refreshTable();
        } else {
            observableList.setAll(examScheduleService.getExamScheduleBy(keyword));
            tableView.setItems(observableList);
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
        observableList.setAll(examScheduleService.getExamSchedules(pageIndex + 1, rowsPerPage));
        tableView.setItems(observableList);
    }

    private void refreshTable() {
        int totalRows = examScheduleService.countExamSchedules();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        loadPage(pagination.getCurrentPageIndex(), rowsPerPage);
    }
}