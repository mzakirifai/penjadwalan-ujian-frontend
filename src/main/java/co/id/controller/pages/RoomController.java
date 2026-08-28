package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.EditableCell;
import co.id.model.Room;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
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

public class RoomController {
    private MasterService masterService;
    private ObservableList<Room> observableList;
    
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private TableView<Room> tableView;
    @FXML private TableColumn<Room, String> tableColumnCode;
    @FXML private TableColumn<Room, String> tableColumnName;
    @FXML private TableColumn<Room, String> tableColumnFloor;
    @FXML private TableColumn<Room, String> tableColumnCapacity;
    @FXML private TableColumn<Room, Void> tableColumnAction;
    @FXML private Pagination pagination;

    private int editingRowIndex = -1;
    
    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
        observableList = FXCollections.observableArrayList();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tableColumnCode.setCellValueFactory(r ->
            new javafx.beans.property.SimpleStringProperty(
                    r.getValue().getCode() != null ? r.getValue().getCode() : "-"));

        tableColumnName.setCellFactory(tc ->
            new EditableCell<Room>(Room::getName, Room::setName, () -> editingRowIndex));

        tableColumnFloor.setCellFactory(tc ->
            new EditableCell<Room>(
                    room -> String.valueOf(room.getFloor()),
                    (room, value) -> room.setFloor(parseIntSafely(value, room.getFloor())),
                    () -> editingRowIndex
            ));

        tableColumnCapacity.setCellFactory(tc ->
            new EditableCell<Room>(
                    room -> String.valueOf(room.getCapacity()),
                    (room, value) -> room.setCapacity(parseIntSafely(value, room.getCapacity())),
                    () -> editingRowIndex
            ));

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
                    Room room = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Yakin ingin menghapus ruangan \"" + room.getName() + "\"?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            try {
                                masterService.deleteRoom(room.getId());
                                refreshTable();
                            } catch (Exception ex) {
                                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                            }
                        }
                    });
                });

                btnSave.setOnAction(e -> {
                    Room room = getTableView().getItems().get(getIndex());

                    if (room.getName() == null || room.getName().isBlank()) {
                        new Alert(Alert.AlertType.WARNING, "Nama Ruangan wajib diisi").showAndWait();
                        return;
                    }
                    if (room.getCapacity() <= 0) {
                        new Alert(Alert.AlertType.WARNING, "Kapasitas harus lebih dari 0").showAndWait();
                        return;
                    }

                    try {
                        masterService.saveOrUpdateRoom(room, AuthContext.getCurrentUsername());
                    } catch (Exception ex) {
                        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                        return;
                    }

                    new Alert(Alert.AlertType.INFORMATION,
                            room.getId() == 0 ? "Data berhasil disimpan" : "Data berhasil diperbarui",
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

        int totalRows = masterService.countRooms();
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
    
    private int parseIntSafely(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
    
    private void filterItems() {
        String keyword = searchField.getText();
        if (keyword == null || keyword.isEmpty()) {
            refreshTable();
        } else {
            observableList.setAll(masterService.getRoomsBy(keyword));
            tableView.setItems(observableList);
        }
    }
    
    private void loadPage(int pageIndex, int rowsPerPage) {
        observableList.setAll(masterService.getRooms(pageIndex + 1, rowsPerPage));
        tableView.setItems(observableList);
    }
    
    private void refreshTable() {
        int totalRows = masterService.countRooms();
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

        Room newRoom = new Room();
        observableList.add(0, newRoom);
        editingRowIndex = 0;
        tableView.refresh();
        tableView.scrollTo(0);
    }
}
