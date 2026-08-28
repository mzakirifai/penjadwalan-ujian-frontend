package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.EditableCell;
import co.id.model.Major;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class MajorController {
    private MasterService masterService;
    private ObservableList<Major> observableList;

    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private TableView<Major> tableView;
    @FXML private TableColumn<Major, String> tableColumnCode, tableColumnName, tableColumnAbbreviation;
    @FXML private TableColumn<Major, Void> tableColumnAction;
    @FXML private Pagination pagination;

    private int editingRowIndex = -1;

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
        observableList = FXCollections.observableArrayList();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tableColumnCode.setCellValueFactory(m ->
                new SimpleStringProperty(m.getValue().getCode() != null ? m.getValue().getCode() : "-"));

        tableColumnName.setCellFactory(tc ->
            new EditableCell<Major>(Major::getName, Major::setName, () -> editingRowIndex));

        tableColumnAbbreviation.setCellFactory(tc ->
            new EditableCell<Major>(Major::getAbbreviation, Major::setAbbreviation, () -> editingRowIndex));

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
                    btnDelete.setVisible(false);
                    btnDelete.setManaged(false);
                }

                btnEdit.setOnAction(e -> {
                    editingRowIndex = getIndex();
                    tableView.refresh();
                });

                btnDelete.setOnAction(e -> {
                    Major major = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Yakin ingin menghapus jurusan \"" + major.getName() + "\"?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            try {
                                masterService.deleteMajor(major.getId());
                                refreshTable();
                            } catch (Exception ex) {
                                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                            }
                        }
                    });
                });

                btnSave.setOnAction(e -> {
                    Major major = getTableView().getItems().get(getIndex());

                    if (major.getName() == null || major.getName().isBlank()) {
                        new Alert(Alert.AlertType.WARNING, "Nama Jurusan wajib diisi").showAndWait();
                        return;
                    }

                    try {
                        masterService.saveOrUpdateMajor(major, AuthContext.getCurrentUsername());
                    } catch (Exception ex) {
                        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                        return;
                    }

                    new Alert(Alert.AlertType.INFORMATION,
                            major.getId() == 0 ? "Data berhasil disimpan" : "Data berhasil diperbarui",
                            ButtonType.OK).showAndWait();

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

        int totalRows = masterService.countMajors();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        pagination.setPageFactory(pageIndex -> {
            loadPage(pageIndex, rowsPerPage);
            return new VBox(tableView);
        });

        addBtn.setOnAction(e -> addRow());
        filterBtn.setOnAction(e -> filterItems());
        refreshBtn.setOnAction(e -> refreshTable());
    }

    private ImageView createIcon(String path) {
        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        return imageView;
    }

    private void filterItems() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.isEmpty()) {
            refreshTable();
        } else {
            observableList.setAll(masterService.getMajorBy(keyword));
            tableView.setItems(observableList);
        }
    }

    private void loadPage(int pageIndex, int rowsPerPage) {
        observableList.setAll(masterService.getMajors(pageIndex + 1, rowsPerPage));
        tableView.setItems(observableList);
    }

    private void refreshTable() {
        int totalRows = masterService.countMajors();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        loadPage(pagination.getCurrentPageIndex(), rowsPerPage);
    }

    private void addRow() {
        if (editingRowIndex != -1) {
            new Alert(Alert.AlertType.WARNING,
                    "Selesaikan dulu baris yang sedang diedit sebelum menambah baris baru.",
                    ButtonType.OK).showAndWait();
            return;
        }

        Major newMajor = new Major();
        observableList.add(0, newMajor);
        editingRowIndex = 0;
        tableView.refresh();
        tableView.scrollTo(0);
    }
}