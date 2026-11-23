package co.id.component;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Cell;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Perbaikan tampilan global supaya teks tidak terpotong menjadi "...":
 * 1. Semua popup (Alert, konfirmasi, error, ChoiceDialog, dll.) tingginya mengikuti panjang teks.
 * 2. Label pendek dan tombol tidak boleh mengecil di bawah lebar teksnya; yang mengalah adalah kolom isian.
 * Dipasang sekali di Main.start(); halaman yang dimuat ke area konten diproses lewat {@link #preventShrink}.
 */
public final class UiFix {

    private static final double DIALOG_TEXT_WIDTH = 460;

    private UiFix() {
    }

    public static void install() {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    // perbaikan tampilan tidak boleh sampai membuat aplikasi error
                    try {
                        fixWindow(window);
                    } catch (RuntimeException ex) {
                        System.err.println("UiFix dilewati: " + ex);
                    }
                    window.sceneProperty().addListener((obs, oldScene, scene) -> {
                        try {
                            fixScene(window, scene);
                        } catch (RuntimeException ex) {
                            System.err.println("UiFix dilewati: " + ex);
                        }
                    });
                }
            }
        });
    }

    private static void fixWindow(Window window) {
        fixScene(window, window.getScene());
    }

    private static void fixScene(Window window, Scene scene) {
        // Popup (isi dropdown ComboBox, menu, tooltip) dikelola sendiri oleh JavaFX: jangan diubah
        if (scene == null || scene.getRoot() == null || window instanceof javafx.stage.PopupWindow) {
            return;
        }
        if (scene.getRoot() instanceof DialogPane pane) {
            fixDialog(window, pane);
        } else {
            preventShrink(scene.getRoot());
        }
        scene.rootProperty().addListener((obs, oldRoot, root) -> {
            if (root != null && !(root instanceof DialogPane)) {
                preventShrink(root);
            }
        });
    }

    // Teks bawaan JavaFX (bahasa Inggris) -> bahasa Indonesia
    private static final java.util.Map<String, String> TRANSLATIONS = java.util.Map.ofEntries(
            java.util.Map.entry("Confirmation", "Konfirmasi"),
            java.util.Map.entry("Error", "Kesalahan"),
            java.util.Map.entry("Information", "Informasi"),
            java.util.Map.entry("Warning", "Peringatan"),
            java.util.Map.entry("Message", "Pesan"),
            java.util.Map.entry("Choice Dialog", "Pilihan"),
            java.util.Map.entry("Choose", "Pilih"),
            java.util.Map.entry("Select an option.", "Pilih salah satu."),
            java.util.Map.entry("Text Input Dialog", "Isian"),
            java.util.Map.entry("Yes", "Ya"),
            java.util.Map.entry("No", "Tidak"),
            java.util.Map.entry("Cancel", "Batal"),
            java.util.Map.entry("Close", "Tutup"),
            java.util.Map.entry("Apply", "Terapkan"),
            java.util.Map.entry("Finish", "Selesai"),
            java.util.Map.entry("Next", "Berikutnya"),
            java.util.Map.entry("Previous", "Sebelumnya"));

    private static String translate(String text) {
        return text == null ? null : TRANSLATIONS.getOrDefault(text.trim(), text);
    }

    private static void fixDialog(Window window, DialogPane pane) {
        if (window instanceof Stage stage) {
            stage.setTitle(translate(stage.getTitle()));
        }
        pane.setHeaderText(translate(pane.getHeaderText()));
        for (javafx.scene.control.ButtonType type : pane.getButtonTypes()) {
            Node button = pane.lookupButton(type);
            if (button instanceof javafx.scene.control.Button b) {
                b.setText(translate(b.getText()));
            }
        }
        pane.setMinHeight(Region.USE_PREF_SIZE);
        for (Node node : pane.lookupAll(".content")) {
            if (node instanceof Label label) {
                label.setWrapText(true);
                label.setPrefWidth(DIALOG_TEXT_WIDTH);
                label.setMinHeight(Region.USE_PREF_SIZE);
            }
        }
        if (pane.getContent() != null) {
            preventShrink(pane.getContent());
        }
        if (pane.getHeader() != null) {
            preventShrink(pane.getHeader());
        }
        // ukuran popup dihitung ulang setelah teks dibuat membungkus
        Platform.runLater(() -> {
            if (window instanceof Stage stage && window.isShowing()) {
                double oldHeight = stage.getHeight();
                stage.sizeToScene();
                if (stage.getHeight() > oldHeight) {
                    stage.setY(Math.max(0, stage.getY() - (stage.getHeight() - oldHeight) / 2));
                }
            }
        });
    }

    /** Label (tanpa wrapText) dan tombol di bawah node ini tidak mengecil sampai teksnya terpotong. */
    public static void preventShrink(Node node) {
        // tabel/daftar kosong: tulisan bawaan "No content in table" diganti bahasa Indonesia
        // (lewati yang placeholder-nya terikat ke kontrol lain, mis. daftar di dalam dropdown ComboBox)
        if (node instanceof TableView<?> table && table.getPlaceholder() == null
                && !table.placeholderProperty().isBound()) {
            table.setPlaceholder(new Label("Belum ada data"));
        } else if (node instanceof ListView<?> list && list.getPlaceholder() == null
                && !list.placeholderProperty().isBound()) {
            list.setPlaceholder(new Label("Belum ada data"));
        } else if (node instanceof ComboBox<?> combo && combo.getPlaceholder() == null
                && !combo.placeholderProperty().isBound()) {
            // dropdown kosong tidak lagi tampil sebagai kotak putih kosong
            combo.setPlaceholder(new Label("Belum ada pilihan"));
        }
        if (node == null || node instanceof Cell || node instanceof TableView || node instanceof ListView
                || node instanceof TreeView || node instanceof TreeTableView || node instanceof ComboBoxBase
                || node instanceof ChoiceBox || node instanceof TextInputControl || node instanceof Spinner) {
            return;
        }
        if (node instanceof Labeled labeled) {
            if (!labeled.isWrapText() && labeled.getMinWidth() == Region.USE_COMPUTED_SIZE) {
                labeled.setMinWidth(Region.USE_PREF_SIZE);
            } else if (labeled.isWrapText() && labeled.getMinHeight() == Region.USE_COMPUTED_SIZE) {
                // teks yang membungkus boleh turun baris, tapi tingginya jangan dipotong
                labeled.setMinHeight(Region.USE_PREF_SIZE);
            }
            return;
        }
        if (node instanceof ScrollPane scroll) {
            preventShrink(scroll.getContent());
            return;
        }
        if (node instanceof TabPane tabs) {
            for (Tab tab : tabs.getTabs()) {
                preventShrink(tab.getContent());
            }
            return;
        }
        if (node instanceof TitledPane titled) {
            preventShrink(titled.getContent());
            return;
        }
        if (node instanceof SplitPane split) {
            for (Node item : split.getItems()) {
                preventShrink(item);
            }
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                preventShrink(child);
            }
        }
    }
}