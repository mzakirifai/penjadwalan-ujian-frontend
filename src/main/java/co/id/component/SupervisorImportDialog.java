package co.id.component;

import co.id.auth.AuthContext;
import co.id.importer.ExcelGridReader;
import co.id.importer.ImportItem;
import co.id.importer.SupervisorScheduleParser;
import co.id.importer.TeacherNameMatcher;
import co.id.model.SupervisorAssignment;
import co.id.model.Teacher;
import co.id.service.RoomAllocationService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.RoomAllocationServiceImpl;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Impor jadwal pengawas dari file Excel sekolah (menu Penempatan Ruangan > Impor Pengawas).
 *
 * Langkah 1 (jendela ini): nomor pengawas di file dipasangkan ke data Guru. Nama yang sama persis langsung
 * terpasang; nama yang mirip ditebak dan ditandai "cek"; yang tidak ketemu dipilih sendiri.
 * Langkah 2: pratinjau per sesi & ruangan (jendela impor biasa), hanya baris yang lolos pemeriksaan yang disimpan.
 */
public final class SupervisorImportDialog {

    private static final class Row {
        final int number;
        final String fileName;
        final int usage;
        final TeacherNameMatcher.Level level;
        final ObjectProperty<Teacher> teacher = new SimpleObjectProperty<>();

        Row(int number, String fileName, int usage, TeacherNameMatcher.Match match) {
            this.number = number;
            this.fileName = fileName;
            this.usage = usage;
            this.level = match.level();
            this.teacher.set(match.teacher());
        }
    }

    private final Region mainRoot;
    private final Runnable onImported;
    private final File file;
    private final SupervisorScheduleParser.Result parsed;
    private final List<Teacher> teachers;
    private final ObservableList<Row> rows = FXCollections.observableArrayList(r -> new javafx.beans.Observable[]{r.teacher});
    private final Stage stage = new Stage();
    private final Label summary = new Label();

    public static void show(Region mainRoot, Window owner, Runnable onImported) {
        if (!AuthContext.isAdmin()) {
            new Alert(Alert.AlertType.WARNING, "Hanya Admin yang boleh mengimpor jadwal pengawas.").showAndWait();
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih file jadwal pengawas (Excel)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("File Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"));
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        SupervisorScheduleParser.Result parsed;
        try {
            parsed = SupervisorScheduleParser.parse(ExcelGridReader.readAll(file));
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Gagal membaca file: " + ex.getMessage()
                    + "\nJika file sedang dibuka di Excel, tutup dulu lalu coba lagi.").showAndWait();
            return;
        }
        if (parsed == null || parsed.getEntries().isEmpty() || parsed.getLegend().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Format jadwal pengawas tidak dikenali.\n\n"
                    + "File harus berisi:\n"
                    + "- baris judul ruangan (R 1, R 2, ... atau R I, R II, ...),\n"
                    + "- kolom tanggal (mis. \"Senin, 30 November 2026\") dan jam (mis. \"07.00 - 08.30\"),\n"
                    + "- nomor pengawas di kolom tiap ruangan,\n"
                    + "- daftar \"NO\" + \"NAMA PENGAWAS\" untuk arti nomor tersebut.").showAndWait();
            return;
        }
        new SupervisorImportDialog(mainRoot, owner, onImported, file, parsed);
    }

    private SupervisorImportDialog(Region mainRoot, Window owner, Runnable onImported, File file,
                                   SupervisorScheduleParser.Result parsed) {
        this.mainRoot = mainRoot;
        this.onImported = onImported;
        this.file = file;
        this.parsed = parsed;
        this.teachers = new ArrayList<>(new MasterServiceImpl().getAllTeachers());
        this.teachers.sort(Comparator.comparing(t -> t.getName() == null ? "" : t.getName(), String.CASE_INSENSITIVE_ORDER));

        Map<Integer, Integer> usage = new HashMap<>();
        for (SupervisorScheduleParser.Entry e : parsed.getEntries()) {
            usage.merge(e.number(), 1, Integer::sum);
        }
        for (Map.Entry<Integer, String> legend : parsed.getLegend().entrySet()) {
            rows.add(new Row(legend.getKey(), legend.getValue(), usage.getOrDefault(legend.getKey(), 0),
                    TeacherNameMatcher.match(legend.getValue(), teachers)));
        }

        stage.setTitle("Impor Pengawas - Pasangkan Nama");
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        Scene scene = new Scene(buildContent(), 900, 640);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        rows.addListener((javafx.collections.ListChangeListener<Row>) c -> refreshSummary());
        refreshSummary();
        stage.show();
    }

    private VBox buildContent() {
        long sessions = parsed.getEntries().stream().map(e -> e.date() + " " + e.start()).distinct().count();
        Label info = new Label("File: " + file.getName() + "  (sheet \"" + parsed.getSheet().trim() + "\")\n"
                + parsed.getLegend().size() + " pengawas, " + parsed.getEntries().size() + " penugasan di "
                + sessions + " sesi. Pastikan setiap nomor pengawas terpasang ke guru yang benar, "
                + "terutama yang bertanda \"cek\" dan \"pilih\". Ketik nama di kolom Guru untuk mencari.");
        info.setWrapText(true);

        VBox notes = new VBox(2);
        for (String problem : parsed.getProblems()) {
            Label l = new Label("• " + problem);
            l.setWrapText(true);
            l.setStyle("-fx-text-fill: #E65100;");
            notes.getChildren().add(l);
        }

        TableView<Row> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Row, String> noCol = new TableColumn<>("No");
        noCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().number)));
        noCol.setMinWidth(45);
        noCol.setMaxWidth(55);

        TableColumn<Row, String> nameCol = new TableColumn<>("Nama di file");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fileName));
        nameCol.setPrefWidth(230);

        TableColumn<Row, Row> teacherCol = new TableColumn<>("Guru di aplikasi");
        teacherCol.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue()));
        teacherCol.setPrefWidth(300);
        teacherCol.setCellFactory(col -> new TableCell<>() {
            private final LookupBox<Teacher> lookup = new LookupBox<>();
            private boolean updating;

            {
                TableColumn<Teacher, String> n = new TableColumn<>("Nama Guru");
                n.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
                TableColumn<Teacher, String> nip = new TableColumn<>("NIP");
                nip.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNip() == null ? "" : c.getValue().getNip()));
                n.setPrefWidth(280);
                nip.setPrefWidth(140);
                lookup.configure(() -> teachers, List.of(n, nip), Teacher::getName);
                lookup.setPromptText("Ketik nama guru...");
                lookup.setMaxWidth(Double.MAX_VALUE);
                lookup.selectedItemProperty().addListener((o, a, b) -> {
                    Row row = getItem();
                    if (!updating && row != null) {
                        row.teacher.set(b);
                    }
                });
            }

            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                updating = true;
                lookup.setSelectedItem(row.teacher.get());
                updating = false;
                setGraphic(lookup);
            }
        });

        TableColumn<Row, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(""));
        statusCol.setMinWidth(150);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                Row row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                Teacher t = row.teacher.get();
                long sameTeacher = t == null ? 0 : rows.stream().filter(r -> r.teacher.get() != null
                        && r.teacher.get().getId() == t.getId()).count();
                String text;
                String color;
                if (t == null) {
                    text = "Pilih guru";
                    color = "#C62828";
                } else if (sameTeacher > 1) {
                    text = "Guru dipakai nomor lain";
                    color = "#C62828";
                } else if (row.level == TeacherNameMatcher.Level.COCOK && t == initialTeacher(row)) {
                    text = "Cocok";
                    color = "#2E7D32";
                } else if (row.level == TeacherNameMatcher.Level.MIRIP && t == initialTeacher(row)) {
                    text = "Mirip - cek";
                    color = "#E65100";
                } else {
                    text = "Dipilih manual";
                    color = "#1565C0";
                }
                setText(text + "  (" + row.usage + " sesi)");
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
            }
        });
        rows.addListener((javafx.collections.ListChangeListener<Row>) c -> table.refresh());

        table.getColumns().add(noCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(teacherCol);
        table.getColumns().add(statusCol);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button cancel = new Button("Batal");
        cancel.setOnAction(e -> stage.close());
        Button next = new Button("Lanjut ke Pratinjau");
        next.getStyleClass().add("list-menu-top-button");
        next.setOnAction(e -> preview());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, summary, spacer, cancel, next);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, info, notes, table, buttons);
        root.setPadding(new Insets(14));
        return root;
    }

    private final Map<Row, Teacher> initial = new LinkedHashMap<>();

    private Teacher initialTeacher(Row row) {
        return initial.computeIfAbsent(row, r -> TeacherNameMatcher.match(r.fileName, teachers).teacher());
    }

    private void refreshSummary() {
        long missing = rows.stream().filter(r -> r.teacher.get() == null).count();
        long check = rows.stream().filter(r -> r.level == TeacherNameMatcher.Level.MIRIP && r.teacher.get() == initialTeacher(r)).count();
        summary.setText(missing > 0 ? missing + " nomor belum dipasangkan (penugasannya tidak ikut diimpor)"
                : check > 0 ? check + " nama mirip, mohon dicek" : "Semua nomor sudah terpasang");
        summary.setStyle(missing > 0 ? "-fx-text-fill: #C62828;" : check > 0 ? "-fx-text-fill: #E65100;" : "-fx-text-fill: #2E7D32;");
    }

    private void preview() {
        Map<Integer, Teacher> mapping = new HashMap<>();
        for (Row row : rows) {
            if (row.teacher.get() != null) {
                mapping.put(row.number, row.teacher.get());
            }
        }
        RoomAllocationService service = new RoomAllocationServiceImpl();
        List<ImportItem<SupervisorAssignment>> items;
        try {
            items = service.previewSupervisorImport(parsed.getEntries(), parsed.getLegend(), mapping);
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Gagal memeriksa data: " + ex.getMessage()).showAndWait();
            return;
        }
        stage.close();
        ImportPreviewDialog.show(mainRoot, "Impor Pengawas", file.getName(),
                List.of("Tanggal", "Jam", "Ruangan", "No", "Pengawas"), items,
                list -> service.importSupervisors(list, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole()),
                onImported);
    }
}