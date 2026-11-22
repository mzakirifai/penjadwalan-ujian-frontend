package co.id.component;

import co.id.model.Student;
import java.util.ArrayList;
import java.util.List;
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
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
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
 * Dialog "Pilih Peserta Ujian": admin mencentang siswa kelas yang ikut ujian.
 * Otomatis: siswa berstatus administrasi "Belum Lunas" tidak dicentang.
 * Manual: admin tetap bisa mencentang / menghilangkan centang siapa pun (mis. dispensasi).
 */
public final class ParticipantPickerDialog {

    private ParticipantPickerDialog() {
    }

    private static final class Row {
        private final Student student;
        private final BooleanProperty selected;

        private Row(Student student) {
            this.student = student;
            this.selected = new SimpleBooleanProperty(!student.isAdminUnpaid());
        }
    }

    /**
     * @param title      judul jadwal, mis. "UJ025 - Matematika | Kelas: X TKJ 1"
     * @param candidates siswa kelas yang belum terdaftar di jadwal ini
     * @param registered jumlah siswa yang sudah terdaftar (hanya informasi)
     * @return siswa yang dicentang, atau null bila dibatalkan
     */
    public static List<Student> show(Region mainRoot, String title, List<Student> candidates, int registered) {
        ObservableList<Row> rows = FXCollections.observableArrayList();
        for (Student student : candidates) {
            rows.add(new Row(student));
        }
        long unpaid = rows.stream().filter(r -> r.student.isAdminUnpaid()).count();

        Label header = new Label("Jadwal: " + title);
        header.setStyle("-fx-font-weight: bold;");
        Label info = new Label("Sudah terdaftar: " + registered + " siswa. Siswa berstatus Belum Lunas otomatis tidak dicentang;"
                + " centang manual bila mendapat dispensasi.");
        info.setWrapText(true);

        TextField search = new TextField();
        search.setPromptText("Cari nama/NIS...");
        HBox.setHgrow(search, Priority.ALWAYS);
        Button checkAll = new Button("Centang Semua");
        Button clearAll = new Button("Kosongkan");
        Button byStatus = new Button("Sesuai Status Administrasi");
        HBox tools = new HBox(8, search, checkAll, clearAll, byStatus);
        tools.setAlignment(Pos.CENTER_LEFT);

        FilteredList<Row> filtered = new FilteredList<>(rows, r -> true);
        TableView<Row> table = new TableView<>(filtered);
        table.setEditable(true);
        table.setPrefHeight(380);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Row, Boolean> colPick = new TableColumn<>("Ikut");
        colPick.setCellValueFactory(c -> c.getValue().selected);
        colPick.setCellFactory(CheckBoxTableCell.forTableColumn(colPick));
        colPick.setEditable(true);
        colPick.setPrefWidth(55);

        TableColumn<Row, String> colNis = new TableColumn<>("NIS");
        colNis.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().student.getNis()));
        colNis.setPrefWidth(110);

        TableColumn<Row, String> colName = new TableColumn<>("Nama Siswa");
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().student.getName()));
        colName.setPrefWidth(240);

        TableColumn<Row, String> colAdmin = new TableColumn<>("Administrasi");
        colAdmin.setCellValueFactory(c -> new SimpleStringProperty(adminLabel(c.getValue().student)));
        colAdmin.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(!empty && item != null && item.startsWith(Student.ADMIN_UNPAID)
                        ? "-fx-text-fill: #c62828; -fx-font-weight: bold;" : "");
            }
        });
        colAdmin.setPrefWidth(220);

        table.getColumns().add(colPick);
        table.getColumns().add(colNis);
        table.getColumns().add(colName);
        table.getColumns().add(colAdmin);

        Label count = new Label();
        Runnable updateCount = () -> {
            long chosen = rows.stream().filter(r -> r.selected.get()).count();
            long unpaidChosen = rows.stream().filter(r -> r.selected.get() && r.student.isAdminUnpaid()).count();
            count.setText("Dipilih: " + chosen + " dari " + rows.size() + " siswa"
                    + (unpaid > 0 ? "  |  Belum lunas: " + unpaid + " (dicentang manual: " + unpaidChosen + ")" : ""));
        };
        rows.forEach(r -> r.selected.addListener((obs, o, n) -> updateCount.run()));
        updateCount.run();

        search.textProperty().addListener((obs, o, text) -> {
            String key = text == null ? "" : text.trim().toLowerCase();
            filtered.setPredicate(r -> key.isEmpty()
                    || (r.student.getName() != null && r.student.getName().toLowerCase().contains(key))
                    || (r.student.getNis() != null && r.student.getNis().toLowerCase().contains(key)));
        });
        // tombol hanya berlaku untuk baris yang sedang tampil (hasil pencarian)
        checkAll.setOnAction(e -> filtered.forEach(r -> r.selected.set(true)));
        clearAll.setOnAction(e -> filtered.forEach(r -> r.selected.set(false)));
        byStatus.setOnAction(e -> filtered.forEach(r -> r.selected.set(!r.student.isAdminUnpaid())));

        Button cancel = new Button("Batal");
        Button register = new Button("Daftarkan");
        register.getStyleClass().add("list-menu-top-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, count, spacer, cancel, register);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, header, info, tools, table, footer);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("form-background");

        Stage dialog = new Stage();
        dialog.setTitle("Pilih Peserta Ujian");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        Scene scene = new Scene(root, 700, 560);
        java.net.URL css = ParticipantPickerDialog.class.getResource("/css/material.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        dialog.setScene(scene);
        dialog.centerOnScreen();

        List<Student> result = new ArrayList<>();
        boolean[] confirmed = {false};
        cancel.setOnAction(e -> dialog.close());
        register.setOnAction(e -> {
            rows.stream().filter(r -> r.selected.get()).forEach(r -> result.add(r.student));
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

    static String adminLabel(Student student) {
        String status = student.getAdminStatus() == null ? Student.ADMIN_PAID : student.getAdminStatus();
        if (student.getAdminNote() != null && !student.getAdminNote().isBlank()) {
            status += " (" + student.getAdminNote() + ")";
        }
        return status;
    }
}