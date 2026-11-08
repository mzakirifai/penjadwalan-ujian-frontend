package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.LookupBox;
import co.id.model.Classroom;
import co.id.model.Student;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
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

public class StudentController {
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button allClassBtn;
    @FXML private LookupBox<Classroom> lookupBoxClassroom;
    @FXML private Button addBtn;
    @FXML private TableView<Student> tableView;
    
    @FXML private TableColumn<Student, String> tableColumnNis, tableColumnNisn, tableColumnName, tableColumnClassroom,
            tableColumnGender;
    @FXML private TableColumn<Student, Void> tableColumnAction;

    @FXML private Pagination pagination;
    
    private static final int ROWS_PER_PAGE = 10;

    private MasterService masterService;
    private ObservableList<Student> observableList;

    // Filter kelas: null berarti semua kelas (default)
    private Classroom filterClassroom;
    private List<Student> classStudents = List.of();
    
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

        tableColumnNis.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getNis()));
        tableColumnNisn.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getNisn()));
        tableColumnName.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getName()));
        tableColumnClassroom.setCellValueFactory(s ->
                new SimpleStringProperty(s.getValue().getClassroom() != null ? s.getValue().getClassroom().getName() : "-"));
        tableColumnGender.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().getGender()));

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
                    Student student = getTableView().getItems().get(getIndex());
                    openForm(student);
                });

                buttonDelete.setOnAction(eh -> {
                    Student student = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Yakin ingin menghapus data siswa \"" + student.getName() + "\"?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(action -> {
                        if (action == ButtonType.YES) {
                            try {
                                masterService.deleteStudent(student.getId(), AuthContext.getCurrentRole());
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

        TableColumn<Classroom, String> colClassName = new TableColumn<>("Kelas");
        colClassName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        lookupBoxClassroom.configure(
                () -> masterService.getAllClassrooms(), List.of(colClassName), Classroom::getName
        );
        lookupBoxClassroom.setFieldWidth(150);
        lookupBoxClassroom.selectedItemProperty().addListener((obs, oldVal, newVal) -> applyClassFilter(newVal));
        allClassBtn.setOnAction(eh -> lookupBoxClassroom.setSelectedItem(null));

        int pageCount = (int) Math.ceil((double) totalRows() / ROWS_PER_PAGE);

        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setPageFactory(clbck -> {
            loadPage(clbck, ROWS_PER_PAGE);
            return new VBox(tableView);
        });

        filterBtn.setOnAction(eh -> filterItems());
        addBtn.setOnAction(eh -> openForm(null));
        refreshBtn.setOnAction(e -> refreshTable());
    }
    
    private void filterItems() {
        String keyword = searchField.getText();

        if (keyword == null || keyword.isBlank()) {
            refreshTable();
        } else {
            List<Student> result = masterService.getStudentBy(keyword.trim());

            if (filterClassroom != null) {
                result = result.stream()
                        .filter(s -> s.getClassroom() != null && s.getClassroom().getId() == filterClassroom.getId())
                        .collect(Collectors.toList());
            }

            observableList.setAll(result);
            tableView.setItems(observableList);
        }
    }

    // Dipanggil saat kelas dipilih di filter (null = tampilkan semua kelas)
    private void applyClassFilter(Classroom classroom) {
        filterClassroom = classroom;
        classStudents = classroom == null ? List.of() : masterService.getStudentsByClassroom(classroom.getId());

        pagination.setCurrentPageIndex(0);
        refreshTable();

        String keyword = searchField.getText();
        if (keyword != null && !keyword.isBlank()) {
            filterItems();
        }
    }

    private int totalRows() {
        return filterClassroom == null ? masterService.countStudents() : classStudents.size();
    }
     
    private void openForm(Student student) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/StudentForm.fxml"));
            Parent formRoot = loader.load();

            StudentFormController formController = loader.getController();

            if (student != null) {
                formController.setStudent(student);
            }

            formController.setOnSaveCallback(this::refreshTable);

            Region mainRoot = (Region) addBtn.getScene().getRoot();
            mainRoot.setEffect(new GaussianBlur(10));

            Stage dialog = new Stage();
            dialog.setTitle(student == null ? "Tambah Siswa" : "Edit Siswa");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);

            Scene scene = new Scene(formRoot, 420, 460);
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
        if (filterClassroom == null) {
            observableList.setAll(masterService.getStudents(pageIndex + 1, rowsPerPage));
        } else {
            int from = Math.min(pageIndex * rowsPerPage, classStudents.size());
            int to = Math.min(from + rowsPerPage, classStudents.size());
            observableList.setAll(classStudents.subList(from, to));
        }
        tableView.setItems(observableList);
    }
    
    private void refreshTable() {
        if (filterClassroom != null) {
            classStudents = masterService.getStudentsByClassroom(filterClassroom.getId());
        }

        int pageCount = (int) Math.ceil((double) totalRows() / ROWS_PER_PAGE);
        pagination.setPageCount(Math.max(pageCount, 1));
        loadPage(pagination.getCurrentPageIndex(), ROWS_PER_PAGE);
    }
}
