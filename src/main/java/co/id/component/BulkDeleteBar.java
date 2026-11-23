package co.id.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Hapus banyak data sekaligus: kolom centang di tabel + baris "n data dipilih | Hapus Terpilih".
 *
 * Pilihan disimpan per id, jadi tetap ada saat pindah halaman. Setiap data dihapus lewat method hapus
 * yang sama dengan tombol Hapus per baris, sehingga aturan bisnis (hak akses, data yang masih dipakai)
 * tetap berlaku. Data yang gagal dihapus tidak menghentikan proses; semuanya dilaporkan di akhir.
 */
public final class BulkDeleteBar<T> {

    @FunctionalInterface
    public interface Deleter<T> {
        void delete(T item) throws Exception;
    }

    /** Lebih dari batas ini, pengguna harus mengetik HAPUS supaya tidak terhapus karena salah klik. */
    public static final int TYPE_CONFIRM_LIMIT = 20;
    private static final String CONFIRM_WORD = "HAPUS";

    private final String entity;
    private final Function<T, Object> keyOf;
    private final Function<T, String> labelOf;
    private final Deleter<T> deleter;

    private final Map<Object, T> selected = new LinkedHashMap<>();
    private Predicate<T> selectable = item -> true;
    private Supplier<List<T>> allItems;
    private Supplier<List<T>> visibleItems = List::of;
    private Function<List<T>, Map<Object, String>> precheck;
    private String warning;
    private Runnable afterDelete = () -> { };
    private Runnable refreshView = () -> { };

    private final HBox bar = new HBox(10);
    private final Label countLabel = new Label();
    private final Hyperlink selectAllLink = new Hyperlink("Pilih semua data");
    private final Hyperlink clearLink = new Hyperlink("Batal pilih");
    private final Button deleteBtn = new Button("Hapus Terpilih");
    private final List<CheckBox> headerBoxes = new ArrayList<>();
    private boolean updatingHeader;

    /**
     * @param entity  nama data untuk pesan, mis. "siswa", "jadwal ujian"
     * @param keyOf   id unik data (dipakai untuk mengingat pilihan antar halaman)
     * @param labelOf teks singkat yang tampil di daftar konfirmasi dan laporan
     * @param deleter cara menghapus satu data (pakai method service yang sama dengan tombol Hapus)
     */
    public BulkDeleteBar(String entity, Function<T, Object> keyOf, Function<T, String> labelOf, Deleter<T> deleter) {
        this.entity = entity;
        this.keyOf = keyOf;
        this.labelOf = labelOf;
        this.deleter = deleter;
        buildBar();
    }

    // ---------- pengaturan ----------

    /** Baris yang boleh dicentang (mis. nilai yang belum diisi tidak bisa dihapus). */
    public BulkDeleteBar<T> selectable(Predicate<T> predicate) {
        this.selectable = predicate;
        return this;
    }

    /** Sumber untuk link "Pilih semua data" (semua halaman). Tanpa ini link disembunyikan. */
    public BulkDeleteBar<T> allItems(Supplier<List<T>> supplier) {
        this.allItems = supplier;
        selectAllLink.setVisible(supplier != null);
        selectAllLink.setManaged(supplier != null);
        return this;
    }

    /** Pemeriksaan sebelum menghapus: kembalikan id data yang dilewati beserta alasannya. */
    public BulkDeleteBar<T> precheck(Function<List<T>, Map<Object, String>> check) {
        this.precheck = check;
        return this;
    }

    /** Catatan merah di dialog konfirmasi, mis. data apa saja yang ikut terhapus. */
    public BulkDeleteBar<T> warning(String text) {
        this.warning = text;
        return this;
    }

    /** Dipanggil setelah proses hapus selesai (biasanya muat ulang tabel). */
    public BulkDeleteBar<T> afterDelete(Runnable action) {
        this.afterDelete = action;
        return this;
    }

    /** Taruh baris "n data dipilih | Hapus Terpilih" tepat di bawah node ini (mis. toolbar). */
    public BulkDeleteBar<T> placeBelow(Node node) {
        Pane parent = (Pane) node.getParent();
        parent.getChildren().add(parent.getChildren().indexOf(node) + 1, bar);
        return this;
    }

    /** Pilihan dikosongkan, mis. saat jadwal ujian yang ditampilkan diganti. */
    public void clearSelection() {
        selected.clear();
        changed();
    }

    // ---------- kolom centang ----------

    /** Tambah kolom centang di paling kiri tabel biasa. */
    public BulkDeleteBar<T> attachTo(TableView<T> table) {
        TableColumn<T, T> column = new TableColumn<>();
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(c -> new TableCell<>() {
            private final CheckBox box = new CheckBox();
            {
                setAlignment(Pos.CENTER);
                box.setOnAction(e -> {
                    T item = getItem();
                    if (item != null) {
                        setSelected(List.of(item), box.isSelected());
                    }
                });
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                box.setSelected(selected.containsKey(keyOf.apply(item)));
                box.setDisable(!selectable.test(item));
                setGraphic(box);
                updateHeader();
            }
        });
        styleColumn(column);
        table.getColumns().add(0, column);

        visibleItems = () -> new ArrayList<>(table.getItems());
        refreshView = table::refresh;
        changed();
        return this;
    }

    /**
     * Tambah kolom centang di tabel bertingkat (TreeTableView).
     * Baris induk mencentang semua anaknya sekaligus; tampil setengah tercentang jika hanya sebagian terpilih.
     *
     * @param itemsOfRow data yang diwakili satu baris (baris induk = semua anaknya, baris anak = dirinya)
     */
    public <R> BulkDeleteBar<T> attachTo(TreeTableView<R> tree, Function<R, List<T>> itemsOfRow) {
        TreeTableColumn<R, R> column = new TreeTableColumn<>();
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getValue()));
        column.setCellFactory(c -> new TreeTableCell<>() {
            private final CheckBox box = new CheckBox();
            {
                setAlignment(Pos.CENTER);
                box.setOnAction(e -> {
                    R row = getItem();
                    if (row != null) {
                        List<T> items = selectableOf(itemsOfRow.apply(row));
                        setSelected(items, !allSelected(items));
                    }
                });
            }

            @Override
            protected void updateItem(R row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                List<T> items = selectableOf(itemsOfRow.apply(row));
                long chosen = items.stream().filter(i -> selected.containsKey(keyOf.apply(i))).count();
                box.setIndeterminate(chosen > 0 && chosen < items.size());
                box.setSelected(!items.isEmpty() && chosen == items.size());
                box.setDisable(items.isEmpty());
                setGraphic(box);
                updateHeader();
            }
        });
        column.setSortable(false);
        column.setReorderable(false);
        column.setResizable(false);
        column.setPrefWidth(40);
        column.setMinWidth(40);
        column.setMaxWidth(40);
        column.setGraphic(newHeaderBox());
        // panah buka/tutup tetap di kolom data pertama, bukan di kolom centang
        TreeTableColumn<R, ?> treeColumn = tree.getTreeColumn() != null ? tree.getTreeColumn()
                : tree.getColumns().isEmpty() ? null : tree.getColumns().get(0);
        tree.getColumns().add(0, column);
        if (treeColumn != null) {
            tree.setTreeColumn(treeColumn);
        }

        visibleItems = () -> {
            Map<Object, T> items = new LinkedHashMap<>();
            TreeItem<R> root = tree.getRoot();
            if (root != null) {
                for (TreeItem<R> child : root.getChildren()) {
                    for (T item : itemsOfRow.apply(child.getValue())) {
                        items.putIfAbsent(keyOf.apply(item), item);
                    }
                }
            }
            return new ArrayList<>(items.values());
        };
        refreshView = tree::refresh;
        changed();
        return this;
    }

    private void styleColumn(TableColumn<T, T> column) {
        column.setSortable(false);
        column.setReorderable(false);
        column.setResizable(false);
        column.setPrefWidth(40);
        column.setMinWidth(40);
        column.setMaxWidth(40);
        column.setGraphic(newHeaderBox());
    }

    // Centang di judul kolom: pilih / batal pilih semua baris yang sedang tampil
    private CheckBox newHeaderBox() {
        CheckBox header = new CheckBox();
        header.setTooltip(new Tooltip("Pilih / batal pilih semua baris yang tampil"));
        header.setOnAction(e -> {
            if (updatingHeader) {
                return;
            }
            List<T> items = selectableOf(visibleItems.get());
            setSelected(items, !allSelected(items));
        });
        headerBoxes.add(header);
        return header;
    }

    private void updateHeader() {
        List<T> items = selectableOf(visibleItems.get());
        long chosen = items.stream().filter(i -> selected.containsKey(keyOf.apply(i))).count();
        updatingHeader = true;
        for (CheckBox header : headerBoxes) {
            header.setIndeterminate(chosen > 0 && chosen < items.size());
            header.setSelected(!items.isEmpty() && chosen == items.size());
            header.setDisable(items.isEmpty());
        }
        updatingHeader = false;
    }

    // ---------- pilihan ----------

    private List<T> selectableOf(List<T> items) {
        return items.stream().filter(selectable).toList();
    }

    private boolean allSelected(List<T> items) {
        return !items.isEmpty() && items.stream().allMatch(i -> selected.containsKey(keyOf.apply(i)));
    }

    private void setSelected(List<T> items, boolean select) {
        for (T item : items) {
            if (select) {
                selected.put(keyOf.apply(item), item);
            } else {
                selected.remove(keyOf.apply(item));
            }
        }
        changed();
    }

    private void changed() {
        int count = selected.size();
        countLabel.setText(count == 0
                ? "Centang kotak di kiri tabel untuk memilih " + entity + " yang akan dihapus."
                : count + " data " + entity + " dipilih");
        countLabel.setStyle(count == 0 ? "-fx-text-fill: #757575;" : "-fx-font-weight: bold;");
        clearLink.setVisible(count > 0);
        clearLink.setManaged(count > 0);
        deleteBtn.setDisable(count == 0);
        deleteBtn.setText(count == 0 ? "Hapus Terpilih" : "Hapus Terpilih (" + count + ")");
        refreshView.run();
        updateHeader();
    }

    private void buildBar() {
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setStyle("-fx-background-color: #F5F7FA; -fx-background-radius: 4;");

        selectAllLink.setOnAction(e -> {
            try {
                setSelected(selectableOf(allItems.get()), true);
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Gagal mengambil data: " + ex.getMessage()).showAndWait();
            }
        });
        selectAllLink.setVisible(false);
        selectAllLink.setManaged(false);
        clearLink.setOnAction(e -> clearSelection());

        deleteBtn.getStyleClass().add("btn-delete");
        try {
            javafx.scene.image.ImageView icon = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(BulkDeleteBar.class.getResourceAsStream("/icons/trash.png")));
            icon.setFitWidth(16);
            icon.setFitHeight(16);
            deleteBtn.setGraphic(icon);
        } catch (Exception ignored) {
            // ikon hanya hiasan
        }
        deleteBtn.setOnAction(e -> deleteSelected());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(countLabel, selectAllLink, clearLink, spacer, deleteBtn);
        changed();
    }

    // ---------- proses hapus ----------

    private void deleteSelected() {
        List<T> chosen = new ArrayList<>(selected.values());
        if (chosen.isEmpty()) {
            return;
        }
        Map<Object, String> skipped = precheck == null ? Map.of() : precheck.apply(chosen);
        List<T> toDelete = chosen.stream().filter(i -> !skipped.containsKey(keyOf.apply(i))).toList();
        List<String> skippedLines = chosen.stream()
                .filter(i -> skipped.containsKey(keyOf.apply(i)))
                .map(i -> labelOf.apply(i) + " — " + skipped.get(keyOf.apply(i)))
                .toList();

        if (toDelete.isEmpty()) {
            showReport(0, skippedLines);
            return;
        }
        if (!confirm(toDelete, skippedLines)) {
            return;
        }
        run(toDelete, skippedLines);
    }

    private boolean confirm(List<T> items, List<String> skippedLines) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Konfirmasi Hapus");
        dialog.setHeaderText("Hapus " + items.size() + " data " + entity + "?");
        dialog.initOwner(ownerWindow());

        ButtonType deleteType = new ButtonType("Hapus " + items.size() + " Data", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteType, ButtonType.CANCEL);

        ListView<String> list = new ListView<>();
        for (int i = 0; i < items.size(); i++) {
            list.getItems().add((i + 1) + ". " + labelOf.apply(items.get(i)));
        }
        list.setPrefHeight(Math.min(220, 26 * items.size() + 4));

        VBox content = new VBox(8, new Label("Data yang akan dihapus:"), list);
        if (!skippedLines.isEmpty()) {
            Label skippedLabel = new Label(skippedLines.size() + " data lainnya tidak ikut dihapus:\n"
                    + String.join("\n", skippedLines));
            skippedLabel.setWrapText(true);
            content.getChildren().add(skippedLabel);
        }
        Label warn = new Label((warning == null ? "" : warning + "\n") + "Tindakan ini tidak dapat dibatalkan.");
        warn.setWrapText(true);
        warn.setStyle("-fx-text-fill: #C62828;");
        content.getChildren().add(warn);

        Node deleteNode = dialog.getDialogPane().lookupButton(deleteType);
        deleteNode.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");

        if (items.size() > TYPE_CONFIRM_LIMIT) {
            Label typeLabel = new Label("Karena datanya lebih dari " + TYPE_CONFIRM_LIMIT
                    + ", ketik " + CONFIRM_WORD + " untuk melanjutkan:");
            typeLabel.setWrapText(true);
            TextField typeField = new TextField();
            typeField.setPromptText(CONFIRM_WORD);
            deleteNode.setDisable(true);
            typeField.textProperty().addListener((obs, old, text) ->
                    deleteNode.setDisable(!CONFIRM_WORD.equalsIgnoreCase(text == null ? "" : text.trim())));
            content.getChildren().addAll(typeLabel, typeField);
            javafx.application.Platform.runLater(typeField::requestFocus);
        }

        content.setPadding(new Insets(10, 10, 0, 10));
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);

        return dialog.showAndWait().filter(b -> b == deleteType).isPresent();
    }

    private void run(List<T> items, List<String> skippedLines) {
        List<String> problems = new ArrayList<>(skippedLines);
        int[] deleted = {0};

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i < items.size(); i++) {
                    T item = items.get(i);
                    updateMessage("Menghapus " + (i + 1) + " dari " + items.size() + ": " + labelOf.apply(item));
                    try {
                        deleter.delete(item);
                        deleted[0]++;
                    } catch (Exception ex) {
                        problems.add(labelOf.apply(item) + " — " + reason(ex));
                    }
                    updateProgress(i + 1, items.size());
                }
                return null;
            }
        };

        Label status = new Label("Menyiapkan...");
        status.setWrapText(true);
        status.setPrefWidth(380);
        status.textProperty().bind(task.messageProperty());
        ProgressBar progress = new ProgressBar();
        progress.setPrefWidth(380);
        progress.progressProperty().bind(task.progressProperty());

        VBox box = new VBox(10, new Label("Sedang menghapus data " + entity + ", mohon tunggu..."), progress, status);
        box.setPadding(new Insets(16));
        Stage stage = new Stage(StageStyle.UTILITY);
        stage.setTitle("Menghapus Data");
        stage.initModality(Modality.APPLICATION_MODAL);
        Window owner = ownerWindow();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> e.consume());
        stage.setScene(new Scene(box));

        Runnable finish = () -> {
            stage.close();
            selected.clear();
            changed();
            try {
                afterDelete.run();
            } finally {
                showReport(deleted[0], problems);
            }
        };
        task.setOnSucceeded(e -> finish.run());
        task.setOnFailed(e -> {
            problems.add("Proses berhenti: " + reason(task.getException()));
            finish.run();
        });

        Thread thread = new Thread(task, "hapus-banyak");
        thread.setDaemon(true);
        stage.show();
        thread.start();
    }

    private void showReport(int deleted, List<String> problems) {
        Alert alert = new Alert(problems.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        alert.setTitle("Hasil Hapus");
        alert.setHeaderText(null);
        alert.initOwner(ownerWindow());

        Label summary = new Label("Berhasil dihapus: " + deleted + " data " + entity + "."
                + (problems.isEmpty() ? "" : "\nTidak terhapus: " + problems.size() + " data (alasan di bawah)."));
        summary.setWrapText(true);
        VBox content = new VBox(8, summary);
        if (!problems.isEmpty()) {
            TextArea detail = new TextArea(String.join("\n", problems));
            detail.setEditable(false);
            detail.setWrapText(true);
            detail.setPrefRowCount(Math.min(10, problems.size() + 1));
            content.getChildren().add(detail);
        }
        content.setPrefWidth(480);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    // Pesan yang mudah dipahami; pesan bawaan database diringkas
    private static String reason(Throwable ex) {
        Throwable t = ex;
        String message = t.getMessage();
        while ((message == null || message.isBlank()) && t.getCause() != null) {
            t = t.getCause();
            message = t.getMessage();
        }
        if (message == null || message.isBlank()) {
            return "gagal dihapus (" + ex.getClass().getSimpleName() + ")";
        }
        if (message.toLowerCase().contains("foreign key constraint")) {
            return "masih dipakai oleh data lain";
        }
        if (message.toLowerCase().contains("communications link failure")) {
            return "koneksi ke server database terputus";
        }
        return message.replaceFirst("^Gagal menghapus data: ", "");
    }

    private Window ownerWindow() {
        return bar.getScene() == null ? null : bar.getScene().getWindow();
    }
}