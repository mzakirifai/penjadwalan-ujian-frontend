package co.id.controller.pages;

import co.id.component.BulkDeleteBar;
import co.id.auth.AuthContext;
import co.id.model.Teacher;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.TeachingAssignmentService;
import co.id.service.impl.TeachingAssignmentServiceImpl;
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
import co.id.component.ExcelImportActions;

public class TeacherController {
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button importBtn;
    @FXML private Button accountBtn;
    @FXML private Button templateBtn;
    @FXML private Button addBtn;
    @FXML private TableView<Teacher> tableView;

    @FXML private TableColumn<Teacher, String> tableColumnNip, tableColumnName, tableColumnGender,
            tableColumnPhone, tableColumnEmail, tableColumnAddress;
    @FXML private TableColumn<Teacher, Void> tableColumnAction;

    @FXML private Pagination pagination;

    private MasterService masterService;
    private final TeachingAssignmentService teachingAssignmentService = new TeachingAssignmentServiceImpl();
    private ObservableList<Teacher> observableList;

    private ImageView createIcon(String path) {
        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        return imageView;
    }

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
        observableList = FXCollections.observableArrayList();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tableColumnNip.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getNip() != null ? t.getValue().getNip() : "-"));
        tableColumnName.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getName()));
        tableColumnGender.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getGender()));
        tableColumnPhone.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getPhoneNumber()));
        tableColumnEmail.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getEmail()));
        tableColumnAddress.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getAddress()));

        tableColumnAction.setCellFactory(clbck -> new TableCell<>() {
            private final Button buttonEdit = new Button("Edit");
            private final Button buttonDelete = new Button("Hapus");
            private final HBox box = new HBox(5, buttonEdit, buttonDelete);
            {
                buttonEdit.setGraphic(createIcon("/icons/edit.png"));
                buttonDelete.setGraphic(createIcon("/icons/trash.png"));

                buttonEdit.getStyleClass().add("btn-edit");
                buttonDelete.getStyleClass().add("btn-delete");

                if (!AuthContext.isAdmin()) {
                    buttonDelete.setVisible(false);
                    buttonDelete.setManaged(false);
                }

                buttonEdit.setOnAction(eh -> {
                    Teacher teacher = getTableView().getItems().get(getIndex());
                    openForm(teacher);
                });

                buttonDelete.setOnAction(eh -> {
                    Teacher teacher = getTableView().getItems().get(getIndex());

                    // Data pengampu guru ikut terhapus (FK CASCADE), jadi jumlahnya disebutkan di konfirmasi
                    int assignmentCount;
                    try {
                        assignmentCount = teachingAssignmentService.getByTeacher(teacher.getId()).size();
                    } catch (Exception ex) {
                        new Alert(Alert.AlertType.ERROR, "Gagal memeriksa data pengampu: " + ex.getMessage()).showAndWait();
                        return;
                    }
                    String message = "Yakin ingin menghapus data guru \"" + teacher.getName() + "\"?";
                    if (assignmentCount > 0) {
                        message += "\n\n" + assignmentCount + " data pengampu ikut terhapus.";
                    }

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(action -> {
                        if (action == ButtonType.YES) {
                            try {
                                masterService.deleteTeacher(teacher.getId(), AuthContext.getCurrentRole());
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

        int totalRows = masterService.countTeachers();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);

        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setPageFactory(clbck -> {
            loadPage(clbck, rowsPerPage);
            return new VBox(tableView);
        });

        filterBtn.setOnAction(eh -> filterItems());
        addBtn.setOnAction(eh -> openForm(null));
        // Hapus banyak sekaligus (centang + Hapus Terpilih), hanya admin seperti tombol Hapus per baris
        if (AuthContext.isAdmin()) {
            new BulkDeleteBar<Teacher>("guru", x -> x.getId(), x -> x.getName() + (x.getNip() != null && !x.getNip().isBlank() ? " (" + x.getNip() + ")" : ""),
                    x -> masterService.deleteTeacher(x.getId(), AuthContext.getCurrentRole()))
                    .attachTo(tableView)
                    .allItems(() -> searchField.getText() != null && !searchField.getText().isBlank()
                            ? java.util.List.copyOf(tableView.getItems()) : masterService.getAllTeachers())
                    .warning("Data pengampu guru ikut terhapus. Guru yang masih punya akun login atau masih menjadi pengawas tidak akan dihapus.")
                    .afterDelete(this::refreshTable)
                    .placeBelow(addBtn.getParent());
        }
        refreshBtn.setOnAction(e -> refreshTable());
        
        // Import Excel hanya untuk admin (sama seperti hak akses menyimpan data master)
        if (!AuthContext.isAdmin()) {
            importBtn.setVisible(false);
            importBtn.setManaged(false);
            templateBtn.setVisible(false);
            templateBtn.setManaged(false);
            accountBtn.setVisible(false);
            accountBtn.setManaged(false);
        }
        // Buat akun login untuk banyak guru sekaligus (hanya guru yang belum punya akun)
        accountBtn.setOnAction(eh -> co.id.component.TeacherAccountDialog.show(accountBtn.getScene().getWindow(), null));
        importBtn.setOnAction(eh -> ExcelImportActions.importTeachers(
                (Region) importBtn.getScene().getRoot(), importBtn.getScene().getWindow(), this::refreshTable));
        templateBtn.setOnAction(eh -> ExcelImportActions.saveTeacherTemplate(templateBtn.getScene().getWindow()));
    }

    private void filterItems() {
        String keyword = searchField.getText();

        if (keyword == null || keyword.isEmpty()) {
            refreshTable();
        } else {
            observableList.setAll(masterService.getTeacherBy(keyword));
            tableView.setItems(observableList);
        }
    }

    private void openForm(Teacher teacher) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/TeacherForm.fxml"));
            Parent formRoot = loader.load();

            TeacherFormController formController = loader.getController();

            if (teacher != null) {
                formController.setTeacher(teacher);
            }

            formController.setOnSaveCallback(this::refreshTable);

            Region mainRoot = (Region) addBtn.getScene().getRoot();
            mainRoot.setEffect(new GaussianBlur(10));

            Stage dialog = new Stage();
            dialog.setTitle(teacher == null ? "Tambah Guru" : "Edit Guru");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);

            Scene scene = new Scene(formRoot, 420, 420);
            scene.getStylesheets().add(getClass().getResource("/css/material.css").toExternalForm());

            dialog.setScene(scene);
            dialog.centerOnScreen();

            dialog.setOnHidden(eh -> mainRoot.setEffect(null));

            dialog.showAndWait();
        } catch (IOException e) {
            co.id.component.ErrorDialog.show("TeacherController.openForm", "Form guru tidak bisa dibuka.", e);
        }
    }

    private void loadPage(int pageIndex, int rowsPerPage) {
        observableList.setAll(masterService.getTeachers(pageIndex + 1, rowsPerPage));
        tableView.setItems(observableList);
    }

    private void refreshTable() {
        int totalRows = masterService.countTeachers();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        loadPage(pagination.getCurrentPageIndex(), rowsPerPage);
    }
}