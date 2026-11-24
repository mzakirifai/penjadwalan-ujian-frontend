package co.id.component;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/**
 * Kotak pilih data dengan daftar pencarian (dipakai untuk pilih jadwal ujian, kelas, siswa, guru, jurusan).
 *
 * Pengguna bisa:
 *  - mengetik di kotak: daftar langsung muncul dan tersaring sesuai ketikan (semua kata harus ada,
 *    huruf besar/kecil diabaikan, mencari di teks yang tampil maupun di semua kolom daftar);
 *  - memakai panah atas/bawah lalu Enter, atau klik baris untuk memilih; Esc untuk batal;
 *  - klik tombol kaca pembesar untuk melihat semua data.
 * Bila ketikan tidak jadi dipilih, kotak kembali menampilkan pilihan sebelumnya.
 */
public class LookupBox<T> extends HBox {
    private final TextField textField = new TextField();
    private final Button searchBtn = new Button("🔍");
    private final Popup popup = new Popup();
    private final TableView<T> tableView = new TableView<>();
    private final Label emptyLabel = new Label("Tidak ada data yang cocok");
    private final ObjectProperty<T> selectedItemProperty = new SimpleObjectProperty<>();
    private Supplier<List<T>> dataSupplier;
    private Function<T, String> displayFormatter = Object::toString;
    private String promptText;

    private FilteredList<T> filtered;
    private boolean settingText;

    public LookupBox() {
        super(0);

        textField.setEditable(true);
        textField.setTooltip(new Tooltip("Ketik untuk mencari, atau klik tombol kaca pembesar untuk melihat semua"));
        this.getChildren().addAll(textField, searchBtn);
        HBox.setHgrow(textField, Priority.ALWAYS); // bila kotak diberi ruang lebih (mis. di dalam tabel), kolom ketik ikut melebar
        textField.setMinWidth(80);

        tableView.setPrefSize(420, 260);
        tableView.setPlaceholder(emptyLabel);
        // fokus tetap di kotak ketik, jadi baris terpilih diberi warna "aktif" supaya jelas terbaca
        tableView.setStyle("-fx-selection-bar-non-focused: #1E88E5;");
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox box = new VBox(tableView);
        box.setStyle("-fx-background-color: white; -fx-border-color: gray;");
        popup.getContent().add(box);
        popup.setAutoHide(true);
        popup.setHideOnEscape(false); // Esc ditangani sendiri supaya teks bisa dikembalikan
        // klik di luar daftar menutup daftar; ketikan dikembalikan kecuali kursor masih di kotak ketik
        popup.setOnAutoHide(e -> {
            if (!textField.isFocused()) {
                restoreText();
            }
        });

        searchBtn.setOnAction(eh -> {
            if (popup.isShowing()) {
                popup.hide();
                restoreText();
            } else {
                openPopup(true);
                textField.requestFocus();
                textField.selectAll();
            }
        });

        // Mengetik = menyaring daftar
        textField.textProperty().addListener((obs, oldText, text) -> {
            if (settingText || isDisabled()) {
                return;
            }
            if (!popup.isShowing()) {
                openPopup(false);
            }
            applyFilter(text);
        });

        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP) {
                if (!popup.isShowing()) {
                    openPopup(true);
                } else {
                    moveSelection(e.getCode() == KeyCode.DOWN ? 1 : -1);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && popup.isShowing()) {
                T item = tableView.getSelectionModel().getSelectedItem();
                if (item == null && filtered != null && filtered.size() == 1) {
                    item = filtered.get(0);
                }
                if (item != null) {
                    choose(item);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                boolean wasShowing = popup.isShowing();
                popup.hide();
                if (restoreText() || wasShowing) {
                    e.consume();
                }
            }
        });

        // Pindah ke kotak lain tanpa memilih: tutup daftar dan kembalikan teks pilihan sebelumnya.
        // (Klik di daftar tidak memindahkan fokus, karena daftar berupa popup.)
        textField.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                Platform.runLater(() -> {
                    if (!textField.isFocused()) {
                        popup.hide();
                        restoreText();
                    }
                });
            }
        });

        tableView.setOnMouseClicked(eh -> {
            if (eh.getButton() != MouseButton.PRIMARY) {
                return;
            }
            T item = tableView.getSelectionModel().getSelectedItem();
            if (item != null) {
                choose(item);
            }
        });
    }

    private void openPopup(boolean showAll) {
        if (dataSupplier == null || getScene() == null) {
            return;
        }
        List<T> items = dataSupplier.get();
        filtered = new FilteredList<>(FXCollections.observableArrayList(items == null ? List.of() : items));
        tableView.setItems(filtered);
        if (showAll) {
            filtered.setPredicate(null);
        } else {
            applyFilter(textField.getText());
        }

        var bounds = this.localToScreen(this.getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        tableView.setPrefWidth(Math.max(420, bounds.getWidth()));
        popup.show(this, bounds.getMinX(), bounds.getMaxY());
        selectFirst();
    }

    private void applyFilter(String text) {
        if (filtered == null) {
            return;
        }
        String[] words = normalize(text).split(" ");
        filtered.setPredicate(item -> {
            String haystack = searchText(item);
            for (String w : words) {
                if (w.isEmpty()) {
                    continue;
                }
                // Angka saja (mis. "2" pada "tkj 2") harus cocok satu angka utuh, bukan bagian dari "UJ251"
                boolean match = w.matches("\\d+")
                        ? java.util.regex.Pattern.compile("(^|[^0-9])" + w + "([^0-9]|$)").matcher(haystack).find()
                        : haystack.contains(w);
                if (!match) {
                    return false;
                }
            }
            return true;
        });
        selectFirst();
    }

    /** Teks yang dicari: teks tampilan + isi semua kolom daftar. */
    private String searchText(T item) {
        StringBuilder sb = new StringBuilder(displayFormatter.apply(item));
        for (TableColumn<T, ?> column : tableView.getColumns()) {
            Object value = column.getCellData(item);
            if (value != null) {
                sb.append(' ').append(value);
            }
        }
        return normalize(sb.toString());
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\s,]+", " ").trim();
    }

    private void selectFirst() {
        if (filtered != null && !filtered.isEmpty()) {
            tableView.getSelectionModel().select(0);
            tableView.scrollTo(0);
        } else {
            tableView.getSelectionModel().clearSelection();
        }
    }

    private void moveSelection(int delta) {
        if (filtered == null || filtered.isEmpty()) {
            return;
        }
        int index = tableView.getSelectionModel().getSelectedIndex() + delta;
        index = Math.max(0, Math.min(filtered.size() - 1, index));
        tableView.getSelectionModel().select(index);
        tableView.scrollTo(Math.max(0, index - 3));
    }

    private void choose(T item) {
        popup.hide();
        setSelectedItem(item);
        textField.positionCaret(textField.getText().length());
    }

    /** Kembalikan teks ke pilihan saat ini (atau kosong bila belum ada pilihan); true bila teks berubah. */
    private boolean restoreText() {
        T current = selectedItemProperty.get();
        String expected = current == null ? "" : displayFormatter.apply(current);
        if (!expected.equals(textField.getText())) {
            setTextSilently(expected);
            return true;
        }
        return false;
    }

    private void setTextSilently(String text) {
        settingText = true;
        try {
            textField.setText(text);
        } finally {
            settingText = false;
        }
    }

    public void configure(Supplier<List<T>> dataSupplier,
        List<TableColumn<T, ?>> columns, Function<T, String> displayFormatter){
        this.dataSupplier = dataSupplier;
        this.displayFormatter = displayFormatter;
        tableView.getColumns().setAll(columns);
    }

    public String getText() {
        return textField.getText();
    }

    public void setPromptText(String text){
        this.promptText = text;
        textField.setPromptText(text);
    }

    public void setFieldWidth(double width) {
        textField.setPrefWidth(width);
    }

    public String getPromptText(){
        return promptText;
    }

    public ObjectProperty<T> selectedItemProperty(){
        return selectedItemProperty;
    }

    public T getSelectedItem(){
        T val = selectedItemProperty.get();
        return val;
    }

    public void setSelectedItem(T item){
        selectedItemProperty.set(item);
        if(item != null){
            setTextSilently(displayFormatter.apply(item));
        }else{
            setTextSilently("");
            textField.setPromptText(promptText);
        }
    }
}