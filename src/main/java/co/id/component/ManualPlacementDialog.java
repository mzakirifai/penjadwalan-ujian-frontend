package co.id.component;

import co.id.model.ExamSchedule;
import co.id.model.ExamSession;
import co.id.model.Room;
import co.id.model.RoomAssignment;
import co.id.model.RoomPlan;
import co.id.model.SeatAssignment;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Dialog penempatan manual: tabel jadwal (kelas + mapel) x ruangan terpilih. Admin mengisi berapa siswa tiap
 * kelas yang duduk di tiap ruangan (boleh satu kelas dipecah, boleh satu ruangan diisi beberapa kelas).
 * Siapa yang duduk di mana diatur aplikasi: siswa urut nama, kursi tiap ruangan disebar seperti mode otomatis.
 */
public final class ManualPlacementDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private ManualPlacementDialog() {
    }

    private static final class Row {
        final ExamSchedule schedule;
        final int size;
        final Map<Integer, IntegerProperty> counts = new LinkedHashMap<>();

        Row(ExamSchedule schedule, int size) {
            this.schedule = schedule;
            this.size = size;
        }

        int placed() {
            return counts.values().stream().mapToInt(IntegerProperty::get).sum();
        }
    }

    /**
     * @param counts    id jadwal -> jumlah siswa
     * @param rooms     ruangan yang dicentang (kolom tabel)
     * @param current   penempatan yang sedang tampil (untuk isian awal), boleh null
     * @return id jadwal -> (id ruangan -> jumlah), atau null bila dibatalkan
     */
    public static Map<Integer, Map<Integer, Integer>> show(Region mainRoot, ExamSession session, Map<Integer, Integer> counts,
                                                          List<Room> rooms, RoomPlan current) {
        List<Room> columns = new ArrayList<>(rooms);
        columns.sort(Comparator.comparing(Room::getName, ManualPlacementDialog::naturalCompare));

        List<Row> rows = new ArrayList<>();
        for (ExamSchedule schedule : session.getSchedules()) {
            int size = counts.getOrDefault(schedule.getId(), 0);
            if (size == 0) {
                continue;
            }
            Row row = new Row(schedule, size);
            for (Room room : columns) {
                row.counts.put(room.getId(), new SimpleIntegerProperty(0));
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Row r) -> r.schedule.getClassroom() != null ? r.schedule.getClassroom().getName() : "",
                ManualPlacementDialog::naturalCompare));
        // isian awal dari penempatan yang sedang tampil (hasil otomatis atau yang tersimpan)
        if (current != null) {
            Map<Integer, Row> byId = new LinkedHashMap<>();
            rows.forEach(r -> byId.put(r.schedule.getId(), r));
            for (RoomAssignment assignment : current.getRooms()) {
                for (SeatAssignment seat : assignment.getSeats()) {
                    Row row = byId.get(seat.getSchedule().getId());
                    if (row != null && row.counts.containsKey(assignment.getRoom().getId())) {
                        IntegerProperty p = row.counts.get(assignment.getRoom().getId());
                        p.set(p.get() + 1);
                    }
                }
            }
        }

        Label info = new Label("Isi berapa siswa tiap kelas yang duduk di tiap ruangan. Satu kelas boleh dipecah ke beberapa "
                + "ruangan, dan satu ruangan boleh berisi beberapa kelas. Siswa diambil urut nama; kursi di tiap ruangan "
                + "disusun selang-seling otomatis supaya siswa sejadwal tidak duduk bersebelahan. Ruangan yang muncul = "
                + "ruangan yang dicentang di halaman Penempatan Ruangan.");
        info.setWrapText(true);
        info.setMinHeight(Region.USE_PREF_SIZE);
        Label sessionLabel = new Label("Sesi: " + session.getDate().format(DATE) + "  " + session.getStartTime().format(TIME)
                + " - " + session.getEndTime().format(TIME));
        sessionLabel.setStyle("-fx-font-weight: bold;");

        TableView<Row> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setEditable(true);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Row, String> colClass = new TableColumn<>("Kelas - Mapel");
        colClass.setCellValueFactory(c -> new SimpleStringProperty(
                (c.getValue().schedule.getClassroom() != null ? c.getValue().schedule.getClassroom().getName() : "-")
                        + " - " + c.getValue().schedule.getSubjectLabel()));
        colClass.setPrefWidth(230);
        TableColumn<Row, String> colSize = new TableColumn<>("Siswa");
        colSize.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().size)));
        colSize.setPrefWidth(55);
        TableColumn<Row, Row> colRest = new TableColumn<>("Sisa");
        colRest.setPrefWidth(60);
        colRest.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue()));
        table.getColumns().add(colClass);
        table.getColumns().add(colSize);

        Label totals = new Label();
        totals.setWrapText(true);
        FlowPane roomTotals = new FlowPane(12, 4);
        Button ok = new Button("Buat Penempatan");
        ok.getStyleClass().add("list-menu-top-button");

        Runnable[] refresh = new Runnable[1];
        for (Room room : columns) {
            TableColumn<Row, Row> col = new TableColumn<>(room.getName() + " (" + room.getCapacity() + ")");
            col.setPrefWidth(80);
            col.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue()));
            col.setCellFactory(column -> new CountCell(room.getId(), () -> refresh[0].run()));
            table.getColumns().add(col);
        }
        colRest.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                int rest = row.size - row.placed();
                setText(String.valueOf(rest));
                setStyle(rest == 0 ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828; -fx-font-weight: bold;");
            }
        });
        table.getColumns().add(colRest);

        refresh[0] = () -> {
            roomTotals.getChildren().clear();
            boolean over = false;
            boolean tooMany = false;
            int unplaced = 0;
            for (Row row : rows) {
                int rest = row.size - row.placed();
                if (rest < 0) {
                    tooMany = true;
                } else {
                    unplaced += rest;
                }
            }
            for (Room room : columns) {
                int filled = rows.stream().mapToInt(r -> r.counts.get(room.getId()).get()).sum();
                Label l = new Label(room.getName() + ": " + filled + "/" + room.getCapacity());
                if (filled > room.getCapacity()) {
                    l.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                    over = true;
                } else if (filled == 0) {
                    l.setStyle("-fx-text-fill: #9e9e9e;");
                }
                roomTotals.getChildren().add(l);
            }
            totals.setText(tooMany ? "Ada kelas yang dibagi melebihi jumlah siswanya (Sisa merah negatif)."
                    : over ? "Ada ruangan melebihi kapasitas."
                    : unplaced > 0 ? unplaced + " siswa belum mendapat ruangan."
                    : "Semua siswa sudah mendapat ruangan.");
            totals.setStyle(tooMany || over ? "-fx-text-fill: #c62828;" : unplaced > 0 ? "-fx-text-fill: #e65100;" : "-fx-text-fill: #2e7d32;");
            ok.setDisable(tooMany || over);
            table.refresh();
        };

        Button clear = new Button("Kosongkan Semua");
        clear.setOnAction(e -> {
            rows.forEach(r -> r.counts.values().forEach(p -> p.set(0)));
            refresh[0].run();
        });
        Label hint = new Label("Tip: ketik angka lalu Tab / klik sel lain; kolom Sisa harus 0 supaya semua siswa kebagian kursi.");
        hint.setStyle("-fx-text-fill: #666;");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox toolBar = new HBox(8, hint, gap, clear);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Button cancel = new Button("Batal");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, totals, spacer, cancel, ok);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, info, sessionLabel, toolBar, table, new Label("Isi ruangan (terisi/kapasitas):"), roomTotals, footer);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("form-background");

        Stage dialog = new Stage();
        dialog.setTitle("Penempatan Manual");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        Scene scene = new Scene(root, Math.min(1300, 420 + 85 * columns.size()), 640);
        java.net.URL css = ManualPlacementDialog.class.getResource("/css/material.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.centerOnScreen();
        refresh[0].run();

        Map<Integer, Map<Integer, Integer>> result = new LinkedHashMap<>();
        boolean[] confirmed = {false};
        cancel.setOnAction(e -> dialog.close());
        ok.setOnAction(e -> {
            int unplaced = rows.stream().mapToInt(r -> Math.max(0, r.size - r.placed())).sum();
            if (unplaced > 0) {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, unplaced + " siswa belum mendapat ruangan dan tidak akan "
                        + "punya kursi. Tetap lanjut?", ButtonType.YES, ButtonType.NO);
                a.setHeaderText(null);
                if (a.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                    return;
                }
            }
            for (Row row : rows) {
                Map<Integer, Integer> perRoom = new LinkedHashMap<>();
                row.counts.forEach((roomId, p) -> {
                    if (p.get() > 0) {
                        perRoom.put(roomId, p.get());
                    }
                });
                result.put(row.schedule.getId(), perRoom);
            }
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

    /** Sel angka yang langsung tersimpan saat diketik (tanpa harus menekan Enter). */
    private static final class CountCell extends TableCell<Row, Row> {
        private final int roomId;
        private final Runnable onChange;
        private final TextField field = new TextField();
        private Row bound;

        CountCell(int roomId, Runnable onChange) {
            this.roomId = roomId;
            this.onChange = onChange;
            field.setPrefColumnCount(3);
            field.setAlignment(Pos.CENTER_RIGHT);
            field.textProperty().addListener((obs, oldText, text) -> {
                if (bound == null) {
                    return;
                }
                String digits = text.replaceAll("[^0-9]", "");
                if (!digits.equals(text)) {
                    field.setText(digits);
                    return;
                }
                int value = digits.isEmpty() ? 0 : Math.min(999, Integer.parseInt(digits));
                if (bound.counts.get(roomId).get() != value) {
                    bound.counts.get(roomId).set(value);
                    onChange.run();
                }
            });
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            bound = null;
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            int value = row.counts.get(roomId).get();
            String text = value == 0 ? "" : String.valueOf(value);
            if (!field.getText().equals(text)) {
                field.setText(text);
            }
            bound = row;
            setGraphic(field);
        }
    }

    /** "R 2" sebelum "R 10". */
    public static int naturalCompare(String a, String b) {
        String[] pa = (a == null ? "" : a).split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        String[] pb = (b == null ? "" : b).split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            int c;
            if (pa[i].matches("\\d+") && pb[i].matches("\\d+")) {
                c = new java.math.BigInteger(pa[i]).compareTo(new java.math.BigInteger(pb[i]));
            } else {
                c = pa[i].compareToIgnoreCase(pb[i]);
            }
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }

    // ------------------------------------------------------------------ pilih sesi tujuan

    /** Satu sesi tujuan pada dialog "Terapkan ke Sesi Lain". */
    public static final class Target {
        final ExamSession session;
        final boolean hasPlacement;
        final BooleanProperty selected = new SimpleBooleanProperty(true);

        public Target(ExamSession session, boolean hasPlacement) {
            this.session = session;
            this.hasPlacement = hasPlacement;
        }
    }

    /** @return sesi yang dipilih, atau null bila dibatalkan */
    public static List<ExamSession> pickSessions(Region mainRoot, String sourceLabel, List<Target> targets) {
        Label info = new Label("Susunan kelas -> ruangan dari sesi " + sourceLabel + " akan disalin ke sesi yang dicentang "
                + "lalu langsung disimpan. Jumlah siswa menyesuaikan kelas di sesi tujuan; kursi disusun ulang; pengawas "
                + "sesi tujuan yang sudah ada dipertahankan. Kelas yang tidak ada di susunan sumber tidak ditempatkan.");
        info.setWrapText(true);
        info.setMinHeight(Region.USE_PREF_SIZE);

        TableView<Target> table = new TableView<>(FXCollections.observableArrayList(targets));
        table.setEditable(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        TableColumn<Target, Boolean> colPick = new TableColumn<>("Pilih");
        colPick.setCellValueFactory(c -> c.getValue().selected);
        colPick.setCellFactory(CheckBoxTableCell.forTableColumn(colPick));
        colPick.setEditable(true);
        colPick.setPrefWidth(50);
        TableColumn<Target, String> colSession = new TableColumn<>("Sesi");
        colSession.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().session.getDate().format(DATE) + "  "
                + c.getValue().session.getStartTime().format(TIME) + " - " + c.getValue().session.getEndTime().format(TIME)));
        colSession.setPrefWidth(200);
        TableColumn<Target, String> colClasses = new TableColumn<>("Kelas");
        colClasses.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().session.getSchedules().size())
                + " jadwal"));
        colClasses.setPrefWidth(80);
        TableColumn<Target, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().hasPlacement
                ? "Sudah ditempatkan (akan DITIMPA)" : "Belum ditempatkan"));
        colStatus.setPrefWidth(270);
        table.getColumns().add(colPick);
        table.getColumns().add(colSession);
        table.getColumns().add(colClasses);
        table.getColumns().add(colStatus);

        Button checkAll = new Button("Centang Semua");
        Button onlyEmpty = new Button("Hanya yang Belum Ditempatkan");
        Button clearAll = new Button("Kosongkan");
        checkAll.setOnAction(e -> targets.forEach(t -> t.selected.set(true)));
        onlyEmpty.setOnAction(e -> targets.forEach(t -> t.selected.set(!t.hasPlacement)));
        clearAll.setOnAction(e -> targets.forEach(t -> t.selected.set(false)));
        HBox toolBar = new HBox(8, checkAll, onlyEmpty, clearAll);

        Button cancel = new Button("Batal");
        Button apply = new Button("Terapkan & Simpan");
        apply.getStyleClass().add("list-menu-top-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, spacer, cancel, apply);

        VBox root = new VBox(10, info, toolBar, table, footer);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("form-background");
        Stage dialog = new Stage();
        dialog.setTitle("Terapkan Susunan ke Sesi Lain");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        Scene scene = new Scene(root, 700, 540);
        java.net.URL css = ManualPlacementDialog.class.getResource("/css/material.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.centerOnScreen();

        List<ExamSession> result = new ArrayList<>();
        boolean[] confirmed = {false};
        cancel.setOnAction(e -> dialog.close());
        apply.setOnAction(e -> {
            targets.stream().filter(t -> t.selected.get()).forEach(t -> result.add(t.session));
            if (result.isEmpty()) {
                return;
            }
            long overwrite = targets.stream().filter(t -> t.selected.get() && t.hasPlacement).count();
            if (overwrite > 0) {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, overwrite + " sesi yang dicentang sudah punya penempatan "
                        + "dan akan ditimpa. Lanjutkan?", ButtonType.YES, ButtonType.NO);
                a.setHeaderText(null);
                if (a.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                    result.clear();
                    return;
                }
            }
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
}