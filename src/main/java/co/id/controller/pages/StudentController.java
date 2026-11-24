package co.id.controller.pages;

import co.id.component.BulkDeleteBar;
import co.id.auth.AuthContext;
import co.id.component.ExcelImportActions;
import co.id.component.LookupBox;
import co.id.model.Classroom;
import co.id.model.Student;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
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
import co.id.component.StudentImportDialog;
import co.id.importer.StudentExcelReader;
import java.io.File;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

public class StudentController {
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button allClassBtn;
    @FXML private LookupBox<Classroom> lookupBoxClassroom;
    @FXML private Button importBtn;
    @FXML private Button deleteGroupBtn;
    @FXML private Button addBtn;
    @FXML private TableView<Student> tableView;
    
    @FXML private TableColumn<Student, String> tableColumnNis, tableColumnNisn, tableColumnName, tableColumnClassroom,
            tableColumnGender, tableColumnAdminStatus;
    @FXML private MenuButton adminStatusBtn;
    @FXML private MenuItem adminStatusImportItem, adminStatusTemplateItem;
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
        // "Belum Lunas" (beserta keterangannya) ditulis merah supaya mudah terlihat
        tableColumnAdminStatus.setCellValueFactory(s -> {
            Student st = s.getValue();
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
        pagination.setPageFactory(this::createPage);

        filterBtn.setOnAction(eh -> filterItems());
        addBtn.setOnAction(eh -> openForm(null));
        importBtn.setOnAction(eh -> importFromExcel());

        // Import Excel hanya untuk admin (sama seperti hak akses menyimpan data master)
        if (!AuthContext.isAdmin()) {
            importBtn.setVisible(false);
            importBtn.setManaged(false);
            adminStatusBtn.setVisible(false);
            adminStatusBtn.setManaged(false);
        }
        adminStatusImportItem.setOnAction(eh -> ExcelImportActions.importAdminStatuses(
                (Region) adminStatusBtn.getScene().getRoot(), adminStatusBtn.getScene().getWindow(), this::refreshTable));
        adminStatusTemplateItem.setOnAction(eh -> ExcelImportActions.saveAdminStatusTemplate(adminStatusBtn.getScene().getWindow()));

        // Hapus kelompok (satu kelas sekaligus) hanya untuk admin, sama seperti tombol Hapus per siswa
        if (!AuthContext.isAdmin()) {
            deleteGroupBtn.setVisible(false);
            deleteGroupBtn.setManaged(false);
        }
        deleteGroupBtn.setOnAction(eh -> deleteGroup());
        refreshBtn.setOnAction(e -> refreshTable());

        // Hapus banyak sekaligus (centang + Hapus Terpilih), hanya admin seperti tombol Hapus per siswa
        if (AuthContext.isAdmin()) {
            new BulkDeleteBar<Student>("siswa", s -> s.getId(),
                    s -> s.getNis() + " - " + s.getName() + (s.getClassroom() != null ? " (" + s.getClassroom().getName() + ")" : ""),
                    s -> masterService.deleteStudent(s.getId(), AuthContext.getCurrentRole()))
                    .attachTo(tableView)
                    // "Pilih semua data" mengikuti tampilan: hasil pencarian, kelas yang difilter, atau semua siswa
                    .allItems(() -> searchField.getText() != null && !searchField.getText().isBlank()
                            ? List.copyOf(tableView.getItems())
                            : filterClassroom != null ? List.copyOf(classStudents) : masterService.getAllStudents())
                    .warning("Data peserta ujian dan nilai milik siswa yang dipilih ikut terhapus.")
                    .afterDelete(this::refreshTable)
                    .placeBelow(addBtn.getParent());
        }
    }

    // Hapus seluruh siswa dalam satu kelas sekaligus (mis. siswa yang sudah lulus), tanpa menghapus satu per satu.
    private void deleteGroup() {
        List<Classroom> classrooms = masterService.getAllClassrooms();
        if (classrooms.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Belum ada data kelas.").showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Hapus Kelompok Siswa");
        dialog.setHeaderText("Pilih kelas yang seluruh siswanya akan dihapus");
        dialog.initOwner(deleteGroupBtn.getScene().getWindow());

        ButtonType deleteType = new ButtonType("Hapus", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteType, ButtonType.CANCEL);

        ComboBox<Classroom> classCombo = new ComboBox<>(FXCollections.observableArrayList(classrooms));
        classCombo.setPromptText("Pilih kelas");
        classCombo.setMaxWidth(Double.MAX_VALUE);
        classCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Classroom classroom) {
                return classroom == null ? "" : classroom.getName();
            }

            @Override
            public Classroom fromString(String string) {
                return null;
            }
        });

        Label countLabel = new Label("Pilih kelas untuk melihat jumlah siswanya.");
        countLabel.setWrapText(true);
        Label warningLabel = new Label("Peringatan: data peserta ujian dan nilai milik siswa di kelas ini "
                + "juga akan ikut terhapus. Tindakan ini tidak dapat dibatalkan.");
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-text-fill: #C62828;");

        VBox content = new VBox(10, classCombo, countLabel, warningLabel);
        content.setPadding(new Insets(10, 10, 0, 10));
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);

        Node deleteNode = dialog.getDialogPane().lookupButton(deleteType);
        deleteNode.setDisable(true);
        deleteNode.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");

        // [0] siswa, [1] nilai, [2] kursi peserta ujian yang akan ikut terhapus
        int[] counts = {0, 0, 0};
        classCombo.valueProperty().addListener((obs, oldValue, chosen) -> {
            if (chosen == null) {
                counts[0] = 0;
                deleteNode.setDisable(true);
                return;
            }
            try {
                counts[0] = masterService.getStudentsByClassroom(chosen.getId()).size();
                int[] examData = masterService.countStudentExamData(chosen.getId());
                counts[1] = examData[0];
                counts[2] = examData[1];
            } catch (Exception ex) {
                countLabel.setText("Gagal membaca jumlah data: " + ex.getMessage());
                deleteNode.setDisable(true);
                return;
            }
            countLabel.setText("Kelas " + chosen.getName() + ": " + counts[0] + " siswa"
                    + (counts[1] + counts[2] > 0
                        ? "\nIkut terhapus: " + counts[1] + " data nilai dan " + counts[2] + " kursi peserta ujian."
                        : "\nSiswa di kelas ini belum punya data nilai atau peserta ujian."));
            countLabel.setStyle(counts[1] > 0 ? "-fx-font-weight: bold;" : "");
            deleteNode.setDisable(counts[0] == 0);
        });

        Optional<ButtonType> result = dialog.showAndWait();
        Classroom selected = classCombo.getValue();
        if (result.isEmpty() || result.get() != deleteType || selected == null) {
            return;
        }

        String lostData = counts[1] + counts[2] > 0
                ? "\nIkut terhapus: " + counts[1] + " data nilai dan " + counts[2] + " kursi peserta ujian."
                : "";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Yakin ingin menghapus SEMUA " + counts[0] + " siswa di kelas \""
                + selected.getName() + "\"?" + lostData + "\nTindakan ini tidak dapat dibatalkan.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Konfirmasi Hapus Kelompok");
        confirm.setHeaderText(null);

        confirm.showAndWait().ifPresent(action -> {
            if (action == ButtonType.YES) {
                try {
                    int deleted = masterService.deleteStudentsByClassroom(selected.getId(), AuthContext.getCurrentRole());
                    pagination.setCurrentPageIndex(0);
                    // filter kelas yang sedang aktif adalah kelas yang baru dikosongkan: kembalikan ke Semua Kelas
                    Classroom filtered = lookupBoxClassroom.getSelectedItem();
                    if (filtered != null && filtered.getId() == selected.getId()) {
                        lookupBoxClassroom.setSelectedItem(null);
                    } else {
                        refreshTable();
                    }
                    new Alert(Alert.AlertType.INFORMATION,
                            deleted + " siswa di kelas \"" + selected.getName() + "\" berhasil dihapus.").showAndWait();
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                }
            }
        });
    }
    
    private void importFromExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih file Excel/CSV data siswa");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel / CSV (*.xlsx, *.xls, *.csv)", "*.xlsx", "*.xls", "*.csv", "*.txt"),
                new FileChooser.ExtensionFilter("File Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("File CSV (*.csv, *.txt)", "*.csv", "*.txt"));

        File file = chooser.showOpenDialog(importBtn.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            StudentExcelReader.Result result = StudentExcelReader.read(file);

            if (result.getRows().isEmpty()) {
                new Alert(Alert.AlertType.WARNING,
                        "Tidak ada data siswa yang terbaca.\n"
                        + "Pastikan file punya baris judul kolom yang memuat \"NIS\" dan \"Nama\".").showAndWait();
                return;
            }

            Region mainRoot = (Region) importBtn.getScene().getRoot();
            StudentImportDialog.show(mainRoot, file, result, this::refreshTable);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Gagal membaca file: " + ex.getMessage()
                    + "\nPastikan file berformat Excel (.xlsx/.xls) atau CSV.").showAndWait();
        }
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

        // Kembali ke halaman 1 dengan jumlah halaman yang baru
        resetPagination(Math.max((int) Math.ceil((double) totalRows() / ROWS_PER_PAGE), 1), 0);

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
            co.id.component.ErrorDialog.show("StudentController.openForm", "Form siswa tidak bisa dibuka.", e);
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

        int pageCount = Math.max((int) Math.ceil((double) totalRows() / ROWS_PER_PAGE), 1);
        int pageIndex = Math.min(pagination.getCurrentPageIndex(), pageCount - 1);

        if (pageCount != pagination.getPageCount() || pageIndex != pagination.getCurrentPageIndex()) {
            resetPagination(pageCount, pageIndex);
        } else {
            loadPage(pageIndex, ROWS_PER_PAGE);
        }
    }

    // Isi tabel untuk satu halaman. Dipanggil oleh Pagination.
    private Node createPage(int pageIndex) {
        loadPage(pageIndex, ROWS_PER_PAGE);
        return new VBox(tableView);
    }

    // Mengubah jumlah halaman dan halaman aktif dengan aman.
    // Pagination hanya boleh membuat halaman SATU kali per perubahan. Kalau jumlah halaman dan
    // halaman aktif diubah satu per satu saat page factory masih terpasang, halaman dibuat dua kali
    // dan tabel "dicuri" dari halaman yang sedang tampil, sehingga layar kosong.
    private void resetPagination(int pageCount, int pageIndex) {
        pagination.setPageFactory(null);
        pagination.setPageCount(pageCount);
        pagination.setCurrentPageIndex(pageIndex);
        pagination.setPageFactory(this::createPage);
    }
}