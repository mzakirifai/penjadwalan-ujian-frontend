package co.id.component;

import co.id.importer.ImportItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Dialog pratinjau import yang bisa dipakai untuk data apa saja (Guru, Mapel, ...).
 * Menampilkan semua baris dari Excel beserta statusnya; hanya baris tanpa error yang diimpor.
 * Tidak ada data yang masuk database sebelum tombol Import ditekan.
 */
public class ImportPreviewDialog<T> {
    private static final String READY = "Siap diimpor";

    private final String title;
    private final String fileName;
    private final List<String> columns;
    private final List<ImportItem<T>> items;
    private final Function<List<T>, Integer> importer;
    private final Runnable onImported;

    private FilteredList<ImportItem<T>> filtered;
    private CheckBox onlyProblemCheck;
    private Button importBtn, cancelBtn;
    private Stage stage;

    private ImportPreviewDialog(String title, String fileName, List<String> columns, List<ImportItem<T>> items,
                                Function<List<T>, Integer> importer, Runnable onImported) {
        this.title = title;
        this.fileName = fileName;
        this.columns = columns;
        this.items = items;
        this.importer = importer;
        this.onImported = onImported;
    }

    /**
     * @param importer menyimpan daftar data yang valid dan mengembalikan jumlah yang tersimpan
     *                 (dijalankan di thread terpisah, boleh melempar exception)
     */
    public static <T> void show(Region mainRoot, String title, String fileName, List<String> columns,
                                List<ImportItem<T>> items, Function<List<T>, Integer> importer, Runnable onImported) {
        new ImportPreviewDialog<>(title, fileName, columns, items, importer, onImported).open(mainRoot);
    }

    private void open(Region mainRoot) {
        long ready = items.stream().filter(ImportItem::isValid).count();
        long skipped = items.size() - ready;

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        Label fileLabel = new Label("File: " + fileName);
        fileLabel.setStyle("-fx-font-weight: bold;");
        Label hint = new Label("Hanya baris berstatus \"" + READY + "\" yang akan disimpan. "
                + "Perbaiki baris bermasalah di Excel lalu impor ulang jika perlu.");
        hint.setWrapText(true);
        VBox top = new VBox(6, fileLabel, hint);
        top.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(top);

        TableView<ImportItem<T>> tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        filtered = new FilteredList<>(FXCollections.observableArrayList(items), r -> true);
        tableView.setItems(filtered);

        TableColumn<ImportItem<T>, String> sheetCol = column("Sheet", 60, i -> i.getSheet());
        sheetCol.setMinWidth(60);
        sheetCol.setMaxWidth(110);
        TableColumn<ImportItem<T>, String> rowCol = column("Baris", 45, i -> String.valueOf(i.getRowNumber()));
        rowCol.setMinWidth(50);
        rowCol.setMaxWidth(60);
        tableView.getColumns().add(sheetCol);
        tableView.getColumns().add(rowCol);
        for (int c = 0; c < columns.size(); c++) {
            final int index = c;
            // lebar awal mengikuti isi terpanjang (judul atau data), supaya kolom pendek seperti "No"
            // tidak memakan tempat kolom nama
            int longest = columns.get(c).length();
            for (ImportItem<T> item : items) {
                if (index < item.getCells().size() && item.getCells().get(index) != null) {
                    longest = Math.max(longest, item.getCells().get(index).length());
                }
            }
            TableColumn<ImportItem<T>, String> dataCol = column(columns.get(c), Math.max(50, Math.min(260, longest * 7 + 16)),
                    i -> index < i.getCells().size() ? i.getCells().get(index) : "");
            dataCol.setMinWidth(Math.max(45, Math.min(160, longest * 7 + 16)));
            tableView.getColumns().add(dataCol);
        }

        TableColumn<ImportItem<T>, String> statusCol = column("Status", 260, i -> i.isValid() ? (i.getWarning() == null ? READY : READY + " (" + i.getWarning() + ")") : i.getError());
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                // alasan yang panjang tetap bisa dibaca utuh dengan mengarahkan mouse ke sel
                setTooltip(empty || item == null ? null : new javafx.scene.control.Tooltip(item));
                if (empty || item == null) {
                    setStyle("");
                } else {
                    setStyle(READY.equals(item) ? "-fx-text-fill: #2e7d32;"
                            : item.startsWith(READY) ? "-fx-text-fill: #e65100;" : "-fx-text-fill: #c62828;");
                }
            }
        });
        statusCol.setMinWidth(260);
        tableView.getColumns().add(statusCol);
        root.setCenter(tableView);

        Label summary = new Label("Siap diimpor: " + ready + "   |   Dilewati: " + skipped);
        summary.setStyle("-fx-font-weight: bold;");

        onlyProblemCheck = new CheckBox("Hanya tampilkan yang bermasalah");
        onlyProblemCheck.setOnAction(eh ->
                filtered.setPredicate(onlyProblemCheck.isSelected() ? i -> !i.isValid() : i -> true));

        importBtn = new Button("Impor");
        importBtn.getStyleClass().add("list-menu-top-button");
        importBtn.setDisable(ready == 0);
        importBtn.setOnAction(eh -> doImport());

        cancelBtn = new Button("Batal");
        cancelBtn.setOnAction(eh -> stage.close());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(12, summary, onlyProblemCheck, spacer, cancelBtn, importBtn);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);

        if (mainRoot != null) {
            mainRoot.setEffect(new GaussianBlur(10));
        }

        stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);

        Scene scene = new Scene(root, 1000, 560);
        scene.getStylesheets().add(getClass().getResource("/css/material.css").toExternalForm());
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.setOnHidden(eh -> {
            if (mainRoot != null) {
                mainRoot.setEffect(null);
            }
        });
        stage.showAndWait();
    }

    private TableColumn<ImportItem<T>, String> column(String name, double width, Function<ImportItem<T>, String> getter) {
        TableColumn<ImportItem<T>, String> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        return column;
    }

    private void doImport() {
        List<T> valid = new ArrayList<>();
        for (ImportItem<T> item : items) {
            if (item.isValid()) {
                valid.add(item.getPayload());
            }
        }

        int skipped = items.size() - valid.size();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                valid.size() + " data akan ditambahkan" + (skipped > 0 ? ", " + skipped + " baris dilewati." : ".")
                        + "\nLanjutkan?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Konfirmasi Impor");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        // simpan di thread terpisah supaya jendela tidak membeku
        importBtn.setDisable(true);
        cancelBtn.setDisable(true);

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return importer.apply(valid);
            }
        };

        task.setOnSucceeded(eh -> {
            new Alert(Alert.AlertType.INFORMATION, task.getValue() + " data berhasil diimpor.").showAndWait();
            if (onImported != null) {
                onImported.run();
            }
            stage.close();
        });

        task.setOnFailed(eh -> {
            Throwable ex = task.getException();
            new Alert(Alert.AlertType.ERROR, ex == null ? "Impor gagal." : ex.getMessage()).showAndWait();
            importBtn.setDisable(false);
            cancelBtn.setDisable(false);
        });

        Thread thread = new Thread(task, "import-data");
        thread.setDaemon(true);
        thread.start();
    }
}