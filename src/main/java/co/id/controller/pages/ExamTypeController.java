package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.ExamType;
import co.id.service.ExamTypeService;
import co.id.service.impl.ExamTypeServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

/**
 * Menu Master > Jenis Ujian: tambah, ubah, nonaktifkan, dan hapus jenis ujian tanpa mengubah program.
 * Mengganti kode jenis ikut mengubah semua jadwal yang memakainya (diatur database).
 */
public class ExamTypeController {

    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private TableView<ExamType> tableView;
    @FXML private TableColumn<ExamType, Void> tableColumnAction;
    @FXML private TableColumn<ExamType, String> tableColumnOrder;
    @FXML private TableColumn<ExamType, String> tableColumnCode;
    @FXML private TableColumn<ExamType, String> tableColumnName;
    @FXML private TableColumn<ExamType, String> tableColumnStatus;
    @FXML private TableColumn<ExamType, String> tableColumnUsage;

    private final ExamTypeService examTypeService = new ExamTypeServiceImpl();

    @FXML
    public void initialize() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableColumnOrder.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getOrder())));
        tableColumnCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCode()));
        tableColumnName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        tableColumnStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "Aktif" : "Nonaktif"));
        tableColumnStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(empty || item == null ? "" : "Aktif".equals(item)
                        ? "-fx-text-fill: #2E7D32; -fx-font-weight: bold;" : "-fx-text-fill: #757575;");
            }
        });
        tableColumnUsage.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsageCount() + " jadwal"));

        tableColumnAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("Edit");
            private final Button btnDelete = new Button("Hapus");
            private final HBox box = new HBox(5, btnEdit, btnDelete);

            {
                btnEdit.setGraphic(icon("/icons/edit.png"));
                btnDelete.setGraphic(icon("/icons/trash.png"));
                btnEdit.getStyleClass().add("btn-edit");
                btnDelete.getStyleClass().add("btn-delete");
                btnEdit.setOnAction(e -> openForm(getTableView().getItems().get(getIndex())));
                btnDelete.setOnAction(e -> delete(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        // hanya admin yang boleh mengubah; guru hanya melihat
        if (!AuthContext.isAdmin()) {
            addBtn.setVisible(false);
            addBtn.setManaged(false);
            tableColumnAction.setVisible(false);
        }

        addBtn.setOnAction(e -> openForm(null));
        refreshBtn.setOnAction(e -> refreshTable());
        refreshTable();
    }

    private void refreshTable() {
        try {
            tableView.setItems(FXCollections.observableArrayList(examTypeService.getAll()));
        } catch (Exception ex) {
            tableView.setItems(FXCollections.observableArrayList());
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
        }
    }

    private void openForm(ExamType existing) {
        boolean isNew = existing == null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Tambah Jenis Ujian" : "Edit Jenis Ujian");
        dialog.setHeaderText(isNew ? "Tambah jenis ujian baru" : "Ubah jenis ujian " + existing.getCode());
        if (tableView.getScene() != null) {
            dialog.initOwner(tableView.getScene().getWindow());
        }
        ButtonType saveType = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField codeField = new TextField(isNew ? "" : existing.getCode());
        codeField.setPromptText("mis. PTS");
        TextField nameField = new TextField(isNew ? "" : existing.getFullName());
        nameField.setPromptText("mis. Penilaian Tengah Semester");
        nameField.setPrefColumnCount(28);
        int nextOrder = tableView.getItems().stream().mapToInt(ExamType::getOrder).max().orElse(0) + 1;
        TextField orderField = new TextField(String.valueOf(isNew ? nextOrder : existing.getOrder()));
        orderField.setPrefColumnCount(5);
        CheckBox activeBox = new CheckBox("Aktif (muncul di pilihan jadwal baru dan impor)");
        activeBox.setSelected(isNew || existing.isActive());

        Label note = new Label(isNew ? "Kode maks. 20 karakter, otomatis huruf besar. Nama lengkap tampil di judul laporan."
                : existing.getUsageCount() > 0
                    ? "Jenis ini dipakai " + existing.getUsageCount() + " jadwal. Bila kode diganti, semua jadwal itu ikut memakai kode baru."
                    : "Kode maks. 20 karakter, otomatis huruf besar. Nama lengkap tampil di judul laporan.");
        note.setWrapText(true);
        note.setMaxWidth(440);
        note.setStyle("-fx-text-fill: #616161;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Kode"), codeField);
        grid.addRow(1, new Label("Nama Lengkap"), nameField);
        grid.addRow(2, new Label("Urutan"), orderField);
        grid.add(activeBox, 1, 3);
        grid.add(note, 0, 4, 2, 1);
        dialog.getDialogPane().setContent(grid);

        // Simpan divalidasi di service; bila gagal, dialog tetap terbuka supaya isian tidak hilang
        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            ExamType type = new ExamType();
            type.setId(isNew ? 0 : existing.getId());
            type.setCode(codeField.getText());
            type.setFullName(nameField.getText());
            type.setActive(activeBox.isSelected());
            try {
                type.setOrder(Integer.parseInt(orderField.getText().trim()));
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.WARNING, "Urutan harus berupa angka.").showAndWait();
                event.consume();
                return;
            }

            String newCode = codeField.getText() == null ? "" : codeField.getText().trim().toUpperCase();
            if (!isNew && existing.getUsageCount() > 0 && !newCode.equalsIgnoreCase(existing.getCode())) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Kode " + existing.getCode() + " dipakai " + existing.getUsageCount() + " jadwal ujian. "
                                + "Semua jadwal itu akan ikut berganti menjadi " + newCode + ". Lanjutkan?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText(null);
                if (confirm.showAndWait().filter(b -> b == ButtonType.YES).isEmpty()) {
                    event.consume();
                    return;
                }
            }
            try {
                examTypeService.save(type, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                event.consume();
            }
        });

        javafx.application.Platform.runLater(codeField::requestFocus);
        if (dialog.showAndWait().filter(b -> b == saveType).isPresent()) {
            refreshTable();
        }
    }

    private void delete(ExamType type) {
        if (type.getUsageCount() > 0) {
            new Alert(Alert.AlertType.WARNING, "Jenis ujian " + type.getCode() + " masih dipakai " + type.getUsageCount()
                    + " jadwal ujian, jadi tidak bisa dihapus.\n\nKlik Edit lalu hilangkan centang Aktif supaya jenis ini "
                    + "tidak muncul lagi di pilihan jadwal baru (jadwal dan laporan lama tetap aman).").showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Yakin ingin menghapus jenis ujian \"" + type.getCode() + " - " + type.getFullName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Konfirmasi Hapus");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            try {
                examTypeService.delete(type.getId(), AuthContext.getCurrentRole());
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
            refreshTable();
        }
    }

    private static ImageView icon(String path) {
        ImageView view = new ImageView(new Image(ExamTypeController.class.getResourceAsStream(path)));
        view.setFitWidth(16);
        view.setFitHeight(16);
        return view;
    }
}