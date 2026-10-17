package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.TeachingAssignment;
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

public class TeachingAssignmentController {
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private TableView<TeachingAssignment> tableView;

    @FXML private TableColumn<TeachingAssignment, String> tableColumnNip, tableColumnTeacher, tableColumnSubject, tableColumnClassroom;
    @FXML private TableColumn<TeachingAssignment, Void> tableColumnAction;

    @FXML private Pagination pagination;

    private TeachingAssignmentService teachingAssignmentService;
    private ObservableList<TeachingAssignment> observableList;

    private ImageView createIcon(String path) {
        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        return imageView;
    }

    @FXML
    public void initialize() {
        teachingAssignmentService = new TeachingAssignmentServiceImpl();
        observableList = FXCollections.observableArrayList();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tableColumnNip.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTeacher().getNip()));
        tableColumnTeacher.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTeacher().getName()));
        tableColumnSubject.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSubject().getName()));
        tableColumnClassroom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassroom().getName()));

        tableColumnAction.setCellFactory(clbck -> new TableCell<>() {
            private final Button buttonEdit = new Button("Edit");
            private final Button buttonDelete = new Button("Hapus");
            private final HBox box = new HBox(5, buttonEdit, buttonDelete);
            {
                buttonEdit.setGraphic(createIcon("/icons/edit.png"));
                buttonDelete.setGraphic(createIcon("/icons/trash.png"));

                buttonEdit.getStyleClass().add("btn-edit");
                buttonDelete.getStyleClass().add("btn-delete");

                buttonEdit.setOnAction(eh -> openForm(getTableView().getItems().get(getIndex())));

                buttonDelete.setOnAction(eh -> {
                    TeachingAssignment ta = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Yakin ingin menghapus pengampu \"" + ta.getTeacher().getName() + " - "
                                    + ta.getSubject().getName() + " (" + ta.getClassroom().getName() + ")\"?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(action -> {
                        if (action == ButtonType.YES) {
                            try {
                                teachingAssignmentService.delete(ta.getId(), AuthContext.getCurrentRole());
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

        int rowsPerPage = 10;
        pagination.setPageCount(pageCount(rowsPerPage));
        pagination.setPageFactory(clbck -> {
            loadPage(clbck, rowsPerPage);
            return new VBox(tableView);
        });

        filterBtn.setOnAction(eh -> filterItems());
        addBtn.setOnAction(eh -> openForm(null));
        refreshBtn.setOnAction(e -> refreshTable());
    }

    private int pageCount(int rowsPerPage) {
        int totalRows = teachingAssignmentService.countTeachingAssignments();
        return Math.max((int) Math.ceil((double) totalRows / rowsPerPage), 1);
    }

    private void filterItems() {
        String keyword = searchField.getText();

        if (keyword == null || keyword.isEmpty()) {
            refreshTable();
        } else {
            observableList.setAll(teachingAssignmentService.getTeachingAssignmentBy(keyword));
            tableView.setItems(observableList);
        }
    }

    private void openForm(TeachingAssignment teachingAssignment) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/TeachingAssignmentForm.fxml"));
            Parent formRoot = loader.load();

            TeachingAssignmentFormController formController = loader.getController();
            if (teachingAssignment != null) {
                formController.setTeachingAssignment(teachingAssignment);
            }
            formController.setOnSaveCallback(this::refreshTable);

            Region mainRoot = (Region) addBtn.getScene().getRoot();
            mainRoot.setEffect(new GaussianBlur(10));

            Stage dialog = new Stage();
            dialog.setTitle(teachingAssignment == null ? "Tambah Pengampu" : "Edit Pengampu");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);

            Scene scene = new Scene(formRoot, 440, 320);
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
        observableList.setAll(teachingAssignmentService.getTeachingAssignments(pageIndex + 1, rowsPerPage));
        tableView.setItems(observableList);
    }

    private void refreshTable() {
        int rowsPerPage = 10;
        pagination.setPageCount(pageCount(rowsPerPage));
        loadPage(pagination.getCurrentPageIndex(), rowsPerPage);
    }
}