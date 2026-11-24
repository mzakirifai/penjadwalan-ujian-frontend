package co.id.component;

import co.id.auth.AuthContext;
import co.id.importer.StudentExcelReader;
import co.id.importer.StudentImportRow;
import co.id.model.Classroom;
import co.id.model.Student;
import co.id.service.MasterService;
import co.id.service.impl.MasterServiceImpl;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

/**
 * Dialog pratinjau import siswa dari Excel.
 *
 * Alur: pengguna memilih file -> data dibaca -> dialog ini menampilkan hasilnya beserta status tiap baris
 * dan pemetaan kelas (nama kelas di Excel -> kelas di database) -> pengguna menekan "Import".
 * Tidak ada data yang masuk database sebelum tombol Import ditekan.
 */
public class StudentImportDialog {
    private static final String READY = "Siap diimpor";

    /** Satu baris di tabel pratinjau. */
    private static class RowView {
        final StudentImportRow row;
        final StringProperty status = new SimpleStringProperty();
        Classroom classroom;

        RowView(StudentImportRow row) {
            this.row = row;
        }

        boolean isReady() {
            return READY.equals(status.get());
        }
    }

    private final MasterService masterService = new MasterServiceImpl();
    private final StudentExcelReader.Result data;
    private final File file;
    private final Runnable onImported;

    private final List<Classroom> classrooms;
    private final Set<String> existingNis;
    private final Set<String> existingNisn;
    private final List<RowView> rowViews = new ArrayList<>();
    private final Map<String, Classroom> classMapping = new LinkedHashMap<>(); // kunci: nama kelas di Excel

    private TableView<RowView> tableView;
    private FilteredList<RowView> filtered;
    private CheckBox onlyProblemCheck;
    private Label summaryLabel;
    private Button importBtn, cancelBtn;
    private Stage stage;

    private StudentImportDialog(File file, StudentExcelReader.Result data, Runnable onImported) {
        this.file = file;
        this.data = data;
        this.onImported = onImported;
        this.classrooms = masterService.getAllClassrooms();
        this.existingNis = masterService.getAllStudentNis();
        this.existingNisn = masterService.getAllStudentNisn();

        for (StudentImportRow row : data.getRows()) {
            rowViews.add(new RowView(row));
            classMapping.putIfAbsent(row.getFullClassName(), null);
        }

        // tebak kelas tujuan otomatis
        for (String key : classMapping.keySet()) {
            classMapping.put(key, guessClassroom(key));
        }
    }

    /** Menampilkan dialog. mainRoot dipakai untuk efek blur latar belakang (boleh null). */
    public static void show(Region mainRoot, File file, StudentExcelReader.Result data, Runnable onImported) {
        new StudentImportDialog(file, data, onImported).open(mainRoot);
    }

    // ------------------------------------------------------------------ UI

    private void open(Region mainRoot) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        // ---- atas: ringkasan + pemetaan kelas
        Label title = new Label("File: " + file.getName() + "   |   Sheet dibaca: " + String.join(", ", data.getSheetsRead()));
        title.setStyle("-fx-font-weight: bold;");

        Label hint = new Label("Pilih kelas tujuan untuk setiap kelas di Excel. Kelas tujuan harus sudah ada di Master > Kelas. NISN wajib 10 digit angka.");
        hint.setWrapText(true);

        VBox top = new VBox(8, title, hint, buildMappingPane());
        top.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(top);

        // ---- tengah: tabel pratinjau
        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        filtered = new FilteredList<>(FXCollections.observableArrayList(rowViews), r -> true);
        tableView.setItems(filtered);

        tableView.getColumns().add(textColumn("Sheet", 50, r -> r.row.getSheetName()));
        tableView.getColumns().add(textColumn("Baris", 50, r -> String.valueOf(r.row.getRowNumber())));
        tableView.getColumns().add(textColumn("NIS", 70, r -> r.row.getNis()));
        tableView.getColumns().add(textColumn("NISN", 90, r -> r.row.getNisn()));
        tableView.getColumns().add(textColumn("Nama", 200, r -> r.row.getName()));
        tableView.getColumns().add(textColumn("Jenis Kelamin", 90, r -> r.row.getGender() == null ? "-" : r.row.getGender()));
        tableView.getColumns().add(textColumn("Kelas di Excel", 90, r -> r.row.getFullClassName()));
        tableView.getColumns().add(textColumn("Kelas Tujuan", 100, r -> r.classroom == null ? "-" : r.classroom.getName()));

        TableColumn<RowView, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(160);
        statusCol.setCellValueFactory(c -> c.getValue().status);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                if (empty || item == null) {
                    setStyle("");
                } else if (READY.equals(item)) {
                    setStyle("-fx-text-fill: #2e7d32;");
                } else {
                    setStyle("-fx-text-fill: #c62828;");
                }
            }
        });
        tableView.getColumns().add(statusCol);
        root.setCenter(tableView);

        // ---- bawah: filter + tombol
        summaryLabel = new Label();
        summaryLabel.setStyle("-fx-font-weight: bold;");

        onlyProblemCheck = new CheckBox("Hanya tampilkan yang bermasalah");
        onlyProblemCheck.setOnAction(eh -> applyFilter());

        importBtn = new Button("Impor");
        importBtn.getStyleClass().add("list-menu-top-button");
        importBtn.setOnAction(eh -> doImport());

        cancelBtn = new Button("Batal");
        cancelBtn.setOnAction(eh -> stage.close());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bottom = new HBox(12, summaryLabel, onlyProblemCheck, spacer, cancelBtn, importBtn);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);

        recompute();

        if (mainRoot != null) {
            mainRoot.setEffect(new GaussianBlur(10));
        }

        stage = new Stage();
        stage.setTitle("Impor Siswa dari Excel");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);

        Scene scene = new Scene(root, 900, 620);
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

    private ScrollPane buildMappingPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(6));

        // hitung jumlah siswa per kelas Excel
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RowView rv : rowViews) {
            counts.merge(rv.row.getFullClassName(), 1, Integer::sum);
        }

        int index = 0;
        for (String key : classMapping.keySet()) {
            String label = (key.isEmpty() ? "(kolom Kelas kosong)" : key) + "  (" + counts.get(key) + " siswa)";

            ComboBox<Classroom> combo = new ComboBox<>(FXCollections.observableArrayList(classrooms));
            combo.setPromptText("- pilih kelas -");
            combo.setPrefWidth(220);
            combo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Classroom c) {
                    return c == null ? "" : c.getName();
                }

                @Override
                public Classroom fromString(String s) {
                    return null;
                }
            });
            combo.setValue(classMapping.get(key));
            combo.setOnAction(eh -> {
                classMapping.put(key, combo.getValue());
                recompute();
            });

            int col = (index % 2) * 2;
            int row = index / 2;
            grid.add(new Label(label), col, row);
            grid.add(combo, col + 1, row);
            index++;
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(150, 36 + ((index + 1) / 2) * 34));
        scrollPane.setMaxHeight(170);
        return scrollPane;
    }

    private TableColumn<RowView, String> textColumn(String title, double width, java.util.function.Function<RowView, String> getter) {
        TableColumn<RowView, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        return column;
    }

    // ------------------------------------------------------------------ logika

    /** Mencari kelas di database yang cocok dengan nama kelas dari Excel. */
    private Classroom guessClassroom(String excelClassName) {
        String key = normalize(excelClassName);
        if (key.isEmpty()) {
            return null;
        }

        // 1) nama sama persis, mis. "X TKJ 1" = "X TKJ 1"
        for (Classroom c : classrooms) {
            if (normalize(c.getName()).equals(key)) {
                return c;
            }
        }

        // 2) hanya ada satu kelas yang diawali nama itu, mis. "X DKV" -> "X DKV 1"
        Classroom found = null;
        for (Classroom c : classrooms) {
            if (normalize(c.getName()).startsWith(key + " ")) {
                if (found != null) {
                    return null; // lebih dari satu kandidat, biar pengguna yang memilih
                }
                found = c;
            }
        }
        return found;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /** Menghitung ulang status semua baris (dipanggil saat pemetaan kelas berubah). */
    private void recompute() {
        Set<String> seenNis = new HashSet<>();
        Set<String> seenNisn = new HashSet<>();
        int ready = 0;

        for (RowView rv : rowViews) {
            StudentImportRow row = rv.row;
            rv.classroom = classMapping.get(row.getFullClassName());

            String status;
            if (row.getNis().isEmpty()) {
                status = "NIS kosong";
            } else if (row.getName().isEmpty()) {
                status = "Nama kosong";
            } else if (row.getNisn().isEmpty()) {
                status = "NISN kosong";
            } else if (!row.getNisn().matches("\\d{10}")) {
                status = "NISN harus 10 digit angka";
            } else if (row.getNis().length() > 20) {
                status = "NIS lebih dari 20 karakter";
            } else if (row.getName().length() > 100) {
                status = "Nama lebih dari 100 karakter";
            } else if (existingNis.contains(row.getNis())) {
                status = "NIS sudah terdaftar";
            } else if (existingNisn.contains(row.getNisn())) {
                status = "NISN sudah terdaftar";
            } else if (!seenNis.add(row.getNis())) {
                status = "NIS ganda di file";
            } else if (!seenNisn.add(row.getNisn())) {
                status = "NISN ganda di file";
            } else if (rv.classroom == null) {
                status = row.getFullClassName().isEmpty() ? "Kolom Kelas kosong" : "Kelas belum dipilih";
            } else {
                status = READY;
            }

            rv.status.set(status);
            if (rv.isReady()) {
                ready++;
            }
        }

        int skipped = rowViews.size() - ready;
        summaryLabel.setText("Siap diimpor: " + ready + "   |   Dilewati: " + skipped);
        importBtn.setDisable(ready == 0);
        applyFilter();
        tableView.refresh();
    }

    private void applyFilter() {
        filtered.setPredicate(onlyProblemCheck.isSelected() ? rv -> !rv.isReady() : rv -> true);
    }

    private void doImport() {
        List<Student> students = new ArrayList<>();
        for (RowView rv : rowViews) {
            if (!rv.isReady()) {
                continue;
            }
            Student s = new Student();
            s.setNis(rv.row.getNis());
            s.setNisn(rv.row.getNisn());
            s.setName(rv.row.getName());
            s.setGender(rv.row.getGender());
            s.setClassroom(rv.classroom);
            students.add(s);
        }

        int skipped = rowViews.size() - students.size();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                students.size() + " siswa akan ditambahkan" + (skipped > 0 ? ", " + skipped + " baris dilewati." : ".")
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
                return masterService.importStudents(students, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole());
            }
        };

        task.setOnSucceeded(eh -> {
            new Alert(Alert.AlertType.INFORMATION, task.getValue() + " siswa berhasil diimpor.").showAndWait();
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

        Thread thread = new Thread(task, "import-siswa");
        thread.setDaemon(true);
        thread.start();
    }
}