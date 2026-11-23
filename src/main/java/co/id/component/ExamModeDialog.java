package co.id.component;

import co.id.model.ExamSchedule;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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
import javafx.scene.control.ComboBox;
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
 * Dialog "Mode Ujian": admin memilih banyak jadwal (mis. semua jadwal kelas XII pada AAS Ganjil) lalu
 * menjadikannya Online (tanpa ruangan & pengawas) atau Offline (di ruangan, ikut Penempatan Ruangan).
 */
public final class ExamModeDialog {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String ALL_PERIODS = "Semua periode";
    private static final String ALL_GRADES = "Semua tingkat";

    /** Hasil dialog: mode tujuan + jadwal yang dicentang. */
    public static final class Result {
        private final String mode;
        private final List<ExamSchedule> schedules;

        Result(String mode, List<ExamSchedule> schedules) {
            this.mode = mode;
            this.schedules = schedules;
        }

        public String getMode() { return mode; }
        public List<ExamSchedule> getSchedules() { return schedules; }
    }

    private static final class Row {
        final ExamSchedule schedule;
        final BooleanProperty selected = new SimpleBooleanProperty(false);

        Row(ExamSchedule schedule) {
            this.schedule = schedule;
        }
    }

    private ExamModeDialog() {
    }

    /** @return mode + jadwal yang dipilih, atau null bila dibatalkan */
    public static Result show(Region mainRoot, List<ExamSchedule> schedules) {
        List<ExamSchedule> sorted = new ArrayList<>(schedules);
        sorted.removeIf(s -> s.getClassroom() == null || s.getDate() == null);
        sorted.sort(Comparator.comparing(ExamSchedule::getDate)
                .thenComparing(ExamSchedule::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.getClassroom().getName()));
        ObservableList<Row> rows = FXCollections.observableArrayList();
        sorted.forEach(s -> rows.add(new Row(s)));
        FilteredList<Row> filtered = new FilteredList<>(rows, r -> true);

        Label info = new Label("Pilih jadwal lalu klik \"Jadikan Online\" atau \"Jadikan Offline\". Ujian Online tidak memakai "
                + "ruangan dan pengawas, jadi tidak muncul di Penempatan Ruangan. Jadwal yang sudah ditempatkan lalu "
                + "dijadikan Online akan dikosongkan ruangan, kursi, dan pengawasnya.");
        info.setWrapText(true);
        info.setMinHeight(Region.USE_PREF_SIZE);

        Set<String> periods = new LinkedHashSet<>();
        sorted.forEach(s -> periods.add(period(s)));
        ComboBox<String> periodBox = new ComboBox<>();
        periodBox.getItems().add(ALL_PERIODS);
        periodBox.getItems().addAll(periods);
        periodBox.setValue(periods.size() == 1 ? periods.iterator().next() : ALL_PERIODS);

        ComboBox<String> gradeBox = new ComboBox<>(FXCollections.observableArrayList(ALL_GRADES, "X", "XI", "XII"));
        gradeBox.setValue(ALL_GRADES);

        TextField search = new TextField();
        search.setPromptText("Cari kode/mapel/kelas...");
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox filterBar = new HBox(8, new Label("Periode:"), periodBox, new Label("Tingkat:"), gradeBox, search);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        Button checkAll = new Button("Centang Semua");
        Button clearAll = new Button("Kosongkan");
        Label hint = new Label("Centang Semua hanya mencentang jadwal yang sedang tampil (hasil filter).");
        hint.setStyle("-fx-text-fill: #666;");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox toolBar = new HBox(8, hint, gap, checkAll, clearAll);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        TableView<Row> table = new TableView<>(filtered);
        table.setEditable(true);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Row, Boolean> colPick = new TableColumn<>("Pilih");
        colPick.setCellValueFactory(c -> c.getValue().selected);
        colPick.setCellFactory(CheckBoxTableCell.forTableColumn(colPick));
        colPick.setEditable(true);
        colPick.setPrefWidth(50);
        table.getColumns().add(colPick);
        table.getColumns().add(column("Kode", 70, s -> s.getCode()));
        table.getColumns().add(column("Periode", 175, ExamModeDialog::period));
        table.getColumns().add(column("Tanggal", 90, s -> s.getDate().format(DATE)));
        table.getColumns().add(column("Jam", 95, s -> s.getStartTime() != null && s.getEndTime() != null
                ? s.getStartTime().format(TIME) + "-" + s.getEndTime().format(TIME) : "-"));
        table.getColumns().add(column("Mapel", 220, s -> s.getSubject() != null ? s.getSubjectLabel() : "-"));
        table.getColumns().add(column("Kelas", 90, s -> s.getClassroom().getName()));
        TableColumn<Row, String> colMode = column("Mode", 80, ExamSchedule::getMode);
        colMode.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(!empty && ExamSchedule.MODE_ONLINE.equals(item) ? "-fx-text-fill: #1565c0; -fx-font-weight: bold;" : "");
            }
        });
        table.getColumns().add(colMode);

        Label summary = new Label();
        Button cancel = new Button("Batal");
        Button toOffline = new Button("Jadikan Offline");
        Button toOnline = new Button("Jadikan Online");
        toOnline.getStyleClass().add("list-menu-top-button");

        Runnable refreshSummary = () -> {
            long selected = rows.stream().filter(r -> r.selected.get()).count();
            long online = rows.stream().filter(r -> r.selected.get() && r.schedule.isOnline()).count();
            summary.setText("Dipilih: " + selected + " jadwal  (" + online + " Online, " + (selected - online) + " Offline)");
            toOnline.setDisable(selected - online == 0);
            toOffline.setDisable(online == 0);
        };
        rows.forEach(r -> r.selected.addListener((obs, o, n) -> refreshSummary.run()));
        refreshSummary.run();

        Runnable applyFilter = () -> {
            String key = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            String p = periodBox.getValue();
            String g = gradeBox.getValue();
            filtered.setPredicate(r -> (p == null || ALL_PERIODS.equals(p) || p.equals(period(r.schedule)))
                    && (g == null || ALL_GRADES.equals(g) || g.equals(grade(r.schedule)))
                    && (key.isEmpty() || text(r.schedule).contains(key)));
        };
        periodBox.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        gradeBox.valueProperty().addListener((obs, o, n) -> applyFilter.run());
        search.textProperty().addListener((obs, o, n) -> applyFilter.run());
        applyFilter.run();
        checkAll.setOnAction(e -> filtered.forEach(r -> r.selected.set(true)));
        clearAll.setOnAction(e -> rows.forEach(r -> r.selected.set(false)));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, summary, spacer, cancel, toOffline, toOnline);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, info, filterBar, toolBar, table, footer);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("form-background");

        Stage dialog = new Stage();
        dialog.setTitle("Mode Ujian (Offline / Online)");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        Scene scene = new Scene(root, 1000, 600);
        java.net.URL css = ExamModeDialog.class.getResource("/css/material.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.centerOnScreen();

        Result[] result = {null};
        cancel.setOnAction(e -> dialog.close());
        toOnline.setOnAction(e -> {
            result[0] = new Result(ExamSchedule.MODE_ONLINE, pick(rows, false));
            dialog.close();
        });
        toOffline.setOnAction(e -> {
            result[0] = new Result(ExamSchedule.MODE_OFFLINE, pick(rows, true));
            dialog.close();
        });

        if (mainRoot != null) {
            mainRoot.setEffect(new GaussianBlur(10));
            dialog.setOnHidden(e -> mainRoot.setEffect(null));
        }
        dialog.showAndWait();
        return result[0];
    }

    /** Jadwal tercentang yang mode-nya memang akan berubah. */
    private static List<ExamSchedule> pick(List<Row> rows, boolean currentlyOnline) {
        List<ExamSchedule> list = new ArrayList<>();
        rows.stream().filter(r -> r.selected.get() && r.schedule.isOnline() == currentlyOnline)
                .forEach(r -> list.add(r.schedule));
        return list;
    }

    private static String period(ExamSchedule s) {
        return s.getExamType() + " " + (s.getSemester() == null ? "" : s.getSemester()) + " "
                + (s.getAcademicYear() == null ? "" : s.getAcademicYear());
    }

    private static String grade(ExamSchedule s) {
        if (s.getClassroom().getGrade() != null && !s.getClassroom().getGrade().isBlank()) {
            return s.getClassroom().getGrade();
        }
        String name = s.getClassroom().getName() == null ? "" : s.getClassroom().getName().trim();
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    private static String text(ExamSchedule s) {
        return ((s.getCode() == null ? "" : s.getCode()) + " "
                + (s.getSubject() != null ? s.getSubjectLabel() : "") + " "
                + s.getClassroom().getName()).toLowerCase();
    }

    private static TableColumn<Row, String> column(String title, double width,
                                                    java.util.function.Function<ExamSchedule, String> value) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue().schedule)));
        column.setPrefWidth(width);
        return column;
    }
}