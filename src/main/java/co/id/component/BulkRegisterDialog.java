package co.id.component;

import co.id.model.ExamSchedule;
import co.id.model.Student;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Dialog "Daftarkan Peserta untuk Banyak Jadwal": admin mencentang beberapa jadwal sekaligus, lalu semua siswa
 * kelas jadwal itu yang belum terdaftar didaftarkan. Siswa berstatus administrasi "Belum Lunas" otomatis dilewati
 * (bila ada yang mendapat dispensasi, daftarkan lewat tombol "Pilih & Daftarkan Siswa Kelas Ini" per jadwal).
 */
public final class BulkRegisterDialog {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private BulkRegisterDialog() {
    }

    /** Satu jadwal beserta siswa yang akan didaftarkan bila jadwal ini dicentang. */
    public static final class Plan {
        private final ExamSchedule schedule;
        private final int registered;
        private final int classSize;
        private final List<Student> toRegister;
        private final List<Student> unpaidSkipped;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        public Plan(ExamSchedule schedule, int registered, int classSize, List<Student> toRegister, List<Student> unpaidSkipped) {
            this.schedule = schedule;
            this.registered = registered;
            this.classSize = classSize;
            this.toRegister = toRegister;
            this.unpaidSkipped = unpaidSkipped;
        }

        public ExamSchedule getSchedule() { return schedule; }
        public List<Student> getToRegister() { return toRegister; }
        public List<Student> getUnpaidSkipped() { return unpaidSkipped; }
    }

    /**
     * Hitung rencana pendaftaran tiap jadwal.
     *
     * @param studentsByClass   siswa per id kelas
     * @param registeredBySchedule id siswa yang sudah terdaftar per id jadwal
     */
    public static List<Plan> buildPlans(List<ExamSchedule> schedules, Map<Integer, List<Student>> studentsByClass,
                                        Map<Integer, Set<Integer>> registeredBySchedule) {
        List<Plan> plans = new ArrayList<>();
        for (ExamSchedule schedule : schedules) {
            if (schedule.getClassroom() == null) {
                continue;
            }
            List<Student> classStudents = studentsByClass.getOrDefault(schedule.getClassroom().getId(), List.of());
            Set<Integer> registered = registeredBySchedule.getOrDefault(schedule.getId(), Set.of());
            List<Student> toRegister = new ArrayList<>();
            List<Student> unpaid = new ArrayList<>();
            for (Student student : classStudents) {
                if (registered.contains(student.getId())) {
                    continue;
                }
                if (student.isAdminUnpaid()) {
                    unpaid.add(student);
                } else {
                    toRegister.add(student);
                }
            }
            toRegister.sort(Comparator.comparing(s -> s.getName() == null ? "" : s.getName(), String.CASE_INSENSITIVE_ORDER));
            plans.add(new Plan(schedule, registered.size(), classStudents.size(), toRegister, unpaid));
        }
        plans.sort(Comparator.comparing((Plan p) -> p.schedule.getDate())
                .thenComparing(p -> p.schedule.getStartTime())
                .thenComparing(p -> p.schedule.getClassroom().getName()));
        return plans;
    }

    /** @return rencana jadwal yang dicentang, atau null bila dibatalkan */
    public static List<Plan> show(Region mainRoot, List<Plan> plans) {
        ObservableList<Plan> rows = FXCollections.observableArrayList(plans);
        FilteredList<Plan> filtered = new FilteredList<>(rows, p -> true);

        Label info = new Label("Centang jadwal yang ingin diisi peserta. Semua siswa kelasnya yang belum terdaftar akan didaftarkan."
                + " Siswa berstatus Belum Lunas otomatis dilewati; untuk dispensasi gunakan tombol"
                + " \"Pilih & Daftarkan Siswa Kelas Ini\" pada jadwalnya.");
        info.setWrapText(true);

        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Semua tanggal");
        Button allDates = new Button("Semua Tanggal");
        TextField search = new TextField();
        search.setPromptText("Cari kode/mapel/kelas...");
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox filterBar = new HBox(8, new Label("Tanggal:"), datePicker, allDates, search);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        CheckBox hideComplete = new CheckBox("Sembunyikan jadwal yang tidak ada siswa baru untuk didaftarkan");
        Button checkAll = new Button("Centang Semua");
        Button clearAll = new Button("Kosongkan");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox toolBar = new HBox(8, hideComplete, gap, checkAll, clearAll);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        TableView<Plan> table = new TableView<>(filtered);
        table.setEditable(true);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Plan, Boolean> colPick = new TableColumn<>("Pilih");
        colPick.setCellValueFactory(c -> c.getValue().selected);
        colPick.setCellFactory(CheckBoxTableCell.forTableColumn(colPick));
        colPick.setEditable(true);
        colPick.setPrefWidth(50);

        table.getColumns().add(colPick);
        table.getColumns().add(column("Kode", 70, p -> p.schedule.getCode()));
        table.getColumns().add(column("Tanggal", 90, p -> p.schedule.getDate() != null ? p.schedule.getDate().format(DATE) : "-"));
        table.getColumns().add(column("Jam", 95, p -> p.schedule.getStartTime() != null && p.schedule.getEndTime() != null
                ? p.schedule.getStartTime().format(TIME) + "-" + p.schedule.getEndTime().format(TIME) : "-"));
        table.getColumns().add(column("Mapel", 190, p -> p.schedule.getSubject() != null ? p.schedule.getSubjectLabel() : "-"));
        table.getColumns().add(column("Kelas", 80, p -> p.schedule.getClassroom().getName()));
        table.getColumns().add(column("Terdaftar / Siswa Kelas", 140, p -> p.registered + " / " + p.classSize));
        table.getColumns().add(column("Akan Didaftarkan", 115, p -> String.valueOf(p.toRegister.size())));
        table.getColumns().add(column("Belum Lunas (dilewati)", 140,
                p -> p.unpaidSkipped.isEmpty() ? "-" : String.valueOf(p.unpaidSkipped.size())));

        Label summary = new Label();
        Button cancel = new Button("Batal");
        Button register = new Button("Daftarkan");
        register.getStyleClass().add("list-menu-top-button");

        Runnable refreshSummary = () -> {
            int schedules = 0, students = 0, skipped = 0;
            for (Plan p : rows) {
                if (p.selected.get()) {
                    schedules++;
                    students += p.toRegister.size();
                    skipped += p.unpaidSkipped.size();
                }
            }
            summary.setText("Dipilih: " + schedules + " jadwal  |  " + students + " siswa akan didaftarkan"
                    + (skipped > 0 ? "  |  " + skipped + " belum lunas dilewati" : ""));
            register.setDisable(students == 0);
        };
        rows.forEach(p -> p.selected.addListener((obs, o, n) -> refreshSummary.run()));
        refreshSummary.run();

        Runnable applyFilter = () -> {
            String key = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            filtered.setPredicate(p -> (datePicker.getValue() == null || datePicker.getValue().equals(p.schedule.getDate()))
                    && (!hideComplete.isSelected() || !p.toRegister.isEmpty())
                    && (key.isEmpty() || text(p).contains(key)));
        };
        datePicker.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        search.textProperty().addListener((obs, o, n) -> applyFilter.run());
        hideComplete.selectedProperty().addListener((obs, o, n) -> applyFilter.run());
        allDates.setOnAction(e -> datePicker.setValue(null));
        // tombol hanya berlaku untuk jadwal yang sedang tampil (hasil filter)
        checkAll.setOnAction(e -> filtered.forEach(p -> p.selected.set(!p.toRegister.isEmpty())));
        clearAll.setOnAction(e -> filtered.forEach(p -> p.selected.set(false)));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, summary, spacer, cancel, register);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, info, filterBar, toolBar, table, footer);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("form-background");

        Stage dialog = new Stage();
        dialog.setTitle("Daftarkan Peserta untuk Banyak Jadwal");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        Scene scene = new Scene(root, 1000, 600);
        java.net.URL css = BulkRegisterDialog.class.getResource("/css/material.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.centerOnScreen();

        List<Plan> result = new ArrayList<>();
        boolean[] confirmed = {false};
        cancel.setOnAction(e -> dialog.close());
        register.setOnAction(e -> {
            rows.stream().filter(p -> p.selected.get() && !p.toRegister.isEmpty()).forEach(result::add);
            confirmed[0] = true;
            dialog.close();
        });

        if (mainRoot != null) {
            mainRoot.setEffect(new GaussianBlur(10));
            dialog.setOnHidden(e -> mainRoot.setEffect(null));
        }
        dialog.showAndWait();
        return confirmed[0] ? result : null;
    }

    private static String text(Plan p) {
        return ((p.schedule.getCode() == null ? "" : p.schedule.getCode()) + " "
                + (p.schedule.getSubject() != null ? p.schedule.getSubjectLabel() : "") + " "
                + p.schedule.getClassroom().getName()).toLowerCase();
    }

    private static TableColumn<Plan, String> column(String title, double width, java.util.function.Function<Plan, String> value) {
        TableColumn<Plan, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}