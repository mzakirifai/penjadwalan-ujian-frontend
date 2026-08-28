package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.Subject;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
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

public class SubjectController {
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private TableView<Subject> tableView;
    
    @FXML private TableColumn<Subject, String> tableColumnCode, tableColumnName, tableColumnGrade,
            tableColumnType, tableColumnKkm, tableColumnMajor;
    @FXML private TableColumn<Subject, Void> tableColumnAction;

    @FXML private Pagination pagination;
    
    private MasterService masterService;
    private ObservableList<Subject> observableList;
    
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

        tableColumnCode.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getCode()));
        tableColumnName.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getName()));
        tableColumnGrade.setCellValueFactory(s ->
                new SimpleStringProperty(s.getValue().getGrade() != null ? s.getValue().getGrade() : "Semua"));
        tableColumnType.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getType()));
        tableColumnKkm.setCellValueFactory(s -> new SimpleStringProperty(String.valueOf(s.getValue().getPassingGrade())));
        tableColumnMajor.setCellValueFactory(s ->
                new SimpleStringProperty(s.getValue().getMajor() != null ? s.getValue().getMajor().getName() : "-"));

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
                    Subject subject = getTableView().getItems().get(getIndex());
                    openForm(subject);
                });

                buttonDelete.setOnAction(eh -> {
                    Subject subject = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Yakin ingin menghapus mapel \"" + subject.getName() + "\"?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(action -> {
                        if (action == ButtonType.YES) {
                            try {
                                masterService.deleteSubject(subject.getId());
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

        int totalRows = masterService.countSubjects();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);

        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setPageFactory(clbck -> {
            loadPage(clbck, rowsPerPage);
            return new VBox(tableView);
        });

        filterBtn.setOnAction(eh -> filterItems());
        addBtn.setOnAction(eh -> openForm(null));
        refreshBtn.setOnAction(e -> refreshTable());
    }
    
    private void filterItems() {
        String keyword = searchField.getText();

        if (keyword == null || keyword.isEmpty()) {
            refreshTable();
        } else {
            observableList.setAll(masterService.getSubjectBy(keyword));
            tableView.setItems(observableList);
        }
    }
    
    private void openForm(Subject subject) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/SubjectForm.fxml"));
            Parent formRoot = loader.load();

            SubjectFormController formController = loader.getController();

            if (subject != null) {
                formController.setSubject(subject);
            }

            formController.setOnSaveCallback(this::refreshTable);

            Region mainRoot = (Region) addBtn.getScene().getRoot();
            mainRoot.setEffect(new GaussianBlur(10));

            Stage dialog = new Stage();
            dialog.setTitle(subject == null ? "Tambah Mapel" : "Edit Mapel");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);

            Scene scene = new Scene(formRoot, 420, 400);
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
        observableList.setAll(masterService.getSubjects(pageIndex + 1, rowsPerPage));
        tableView.setItems(observableList);
    }
    
    private void refreshTable() {
        int totalRows = masterService.countSubjects();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        loadPage(pagination.getCurrentPageIndex(), rowsPerPage);
    }
}
