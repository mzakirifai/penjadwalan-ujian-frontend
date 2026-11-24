package co.id.component;

import co.id.auth.AuthContext;
import co.id.export.TeacherAccountExcelWriter;
import co.id.model.Teacher;
import co.id.model.User;
import co.id.service.MasterService;
import co.id.service.TeacherAccountGenerator;
import co.id.service.UserService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.UserServiceImpl;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Buat akun login untuk banyak guru sekaligus (menu Guru > Buat Akun Guru).
 *
 * Hanya guru yang belum punya akun yang ditampilkan. Username diusulkan dari nama depan dan bisa diubah,
 * password awal dibuat acak (atau sama untuk semua). Setelah dibuat, daftar username + password disimpan
 * ke file Excel untuk dibagikan, karena password tidak bisa dilihat lagi dari aplikasi.
 */
public final class TeacherAccountDialog {

    private static final class Row {
        final Teacher teacher;
        final BooleanProperty selected = new SimpleBooleanProperty(true);
        final StringProperty username = new SimpleStringProperty();
        String password;

        Row(Teacher teacher, String username) {
            this.teacher = teacher;
            this.username.set(username);
        }
    }

    private final UserService userService = new UserServiceImpl();
    private final MasterService masterService = new MasterServiceImpl();
    private final ObservableList<Row> rows = FXCollections.observableArrayList(
            r -> new javafx.beans.Observable[]{r.selected, r.username});
    private final Set<String> existingUsernames = new HashSet<>();
    private final Stage stage = new Stage();
    private final Label summary = new Label();
    private final Button createBtn = new Button();
    private final RadioButton randomPassword = new RadioButton("Password acak, berbeda untuk tiap guru (disarankan)");
    private final RadioButton samePassword = new RadioButton("Password sama untuk semua:");
    private final TextField samePasswordField = new TextField();
    private final Runnable onCreated;

    public static void show(Window owner, Runnable onCreated) {
        if (!AuthContext.isAdmin()) {
            new Alert(Alert.AlertType.WARNING, "Hanya Admin yang boleh membuat akun guru.").showAndWait();
            return;
        }
        try {
            new TeacherAccountDialog(owner, onCreated);
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, "Gagal memuat data guru: " + ex.getMessage()).showAndWait();
        }
    }

    private TeacherAccountDialog(Window owner, Runnable onCreated) {
        this.onCreated = onCreated;

        Set<Integer> teachersWithAccount = new HashSet<>();
        // getUsers (bukan getAllUsers) supaya relasi ke data guru ikut terbaca
        for (User user : userService.getUsers(1, Math.max(1, userService.countUsers()))) {
            existingUsernames.add(user.getUsername().toLowerCase(Locale.ROOT));
            if (user.getTeacher() != null) {
                teachersWithAccount.add(user.getTeacher().getId());
            }
        }
        List<Teacher> teachers = new ArrayList<>(masterService.getAllTeachers());
        teachers.removeIf(t -> teachersWithAccount.contains(t.getId()));
        teachers.sort(Comparator.comparing(t -> t.getName() == null ? "" : t.getName(), String.CASE_INSENSITIVE_ORDER));

        if (teachers.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Semua guru sudah punya akun login.").showAndWait();
            return;
        }

        Set<String> taken = new HashSet<>(existingUsernames);
        for (Teacher teacher : teachers) {
            rows.add(new Row(teacher, TeacherAccountGenerator.suggestUsername(teacher, taken)));
        }

        stage.setTitle("Buat Akun Guru");
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setScene(new Scene(buildContent(teachersWithAccount.size()), 760, 600));
        if (owner != null && owner.getScene() != null) {
            stage.getScene().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        rows.addListener((javafx.collections.ListChangeListener<Row>) c -> refreshSummary());
        refreshSummary();
        stage.show();
    }

    private VBox buildContent(int alreadyHaveAccount) {
        Label info = new Label(rows.size() + " guru belum punya akun"
                + (alreadyHaveAccount > 0 ? " (" + alreadyHaveAccount + " guru lain sudah punya akun, tidak ditampilkan)" : "")
                + ". Centang guru yang akan dibuatkan akun. Username bisa diubah dengan klik dua kali.");
        info.setWrapText(true);

        TableView<Row> table = new TableView<>(rows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Row, Boolean> checkCol = new TableColumn<>();
        checkCol.setCellValueFactory(c -> c.getValue().selected);
        checkCol.setCellFactory(CheckBoxTableCell.forTableColumn(checkCol));
        checkCol.setEditable(true);
        checkCol.setSortable(false);
        checkCol.setMinWidth(40);
        checkCol.setMaxWidth(40);
        CheckBox all = new CheckBox();
        all.setSelected(true);
        all.setOnAction(e -> rows.forEach(r -> r.selected.set(all.isSelected())));
        checkCol.setGraphic(all);

        TableColumn<Row, String> nameCol = new TableColumn<>("Nama Guru");
        nameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().teacher.getName()));
        nameCol.setPrefWidth(300);

        TableColumn<Row, String> userCol = new TableColumn<>("Username");
        userCol.setCellValueFactory(c -> c.getValue().username);
        userCol.setCellFactory(TextFieldTableCell.forTableColumn());
        userCol.setOnEditCommit(e -> e.getRowValue().username.set(
                e.getNewValue() == null ? "" : e.getNewValue().trim().toLowerCase(Locale.ROOT)));
        userCol.setEditable(true);
        userCol.setPrefWidth(170);

        TableColumn<Row, String> statusCol = new TableColumn<>("Keterangan");
        statusCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(""));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                Row row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                String problem = row.selected.get() ? problemOf(row) : null;
                setText(!row.selected.get() ? "Tidak dibuat" : problem == null ? "Siap" : problem);
                setStyle(problem != null ? "-fx-text-fill: #C62828;" : row.selected.get() ? "-fx-text-fill: #2E7D32;" : "-fx-text-fill: #757575;");
            }
        });
        statusCol.setPrefWidth(220);
        rows.addListener((javafx.collections.ListChangeListener<Row>) c -> table.refresh());

        table.getColumns().add(checkCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(userCol);
        table.getColumns().add(statusCol);
        VBox.setVgrow(table, Priority.ALWAYS);

        ToggleGroup group = new ToggleGroup();
        randomPassword.setToggleGroup(group);
        samePassword.setToggleGroup(group);
        randomPassword.setSelected(true);
        samePasswordField.setPromptText("min. 6 karakter");
        samePasswordField.setPrefWidth(180);
        samePasswordField.disableProperty().bind(samePassword.selectedProperty().not());
        samePasswordField.textProperty().addListener((o, a, b) -> refreshSummary());
        group.selectedToggleProperty().addListener((o, a, b) -> refreshSummary());
        HBox sameBox = new HBox(8, samePassword, samePasswordField);
        sameBox.setAlignment(Pos.CENTER_LEFT);
        Label passNote = new Label("Daftar username dan password akan disimpan ke file Excel setelah akun dibuat. "
                + "Simpan file itu baik-baik: password tidak bisa dilihat lagi dari aplikasi (hanya bisa di-reset).");
        passNote.setWrapText(true);
        passNote.setStyle("-fx-text-fill: #757575;");

        createBtn.getStyleClass().add("list-menu-top-button");
        createBtn.setOnAction(e -> create());
        Button cancelBtn = new Button("Batal");
        cancelBtn.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, summary, spacer, cancelBtn, createBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, info, table, new Label("Password awal:"), randomPassword, sameBox, passNote, buttons);
        root.setPadding(new Insets(14));
        return root;
    }

    /** Masalah username satu baris, atau null bila aman. */
    private String problemOf(Row row) {
        String u = row.username.get() == null ? "" : row.username.get();
        if (!u.matches(TeacherAccountGenerator.USERNAME_PATTERN)) {
            return "Hanya huruf kecil, angka, . _ (3-50)";
        }
        if (existingUsernames.contains(u)) {
            return "Sudah dipakai akun lain";
        }
        long same = rows.stream().filter(r -> r.selected.get() && u.equals(r.username.get())).count();
        return same > 1 ? "Kembar di daftar ini" : null;
    }

    private List<Row> chosen() {
        return rows.stream().filter(r -> r.selected.get()).toList();
    }

    private void refreshSummary() {
        List<Row> chosen = chosen();
        long problems = chosen.stream().filter(r -> problemOf(r) != null).count();
        boolean passwordOk = randomPassword.isSelected()
                || (samePasswordField.getText() != null && samePasswordField.getText().length() >= 6);
        summary.setText(chosen.size() + " guru dipilih" + (problems > 0 ? ", " + problems + " username perlu diperbaiki" : "")
                + (passwordOk ? "" : ", password minimal 6 karakter"));
        summary.setStyle(problems > 0 || !passwordOk ? "-fx-text-fill: #C62828;" : "");
        createBtn.setText("Buat " + chosen.size() + " Akun");
        createBtn.setDisable(chosen.isEmpty() || problems > 0 || !passwordOk);
    }

    private void create() {
        List<Row> chosen = chosen();
        String same = samePassword.isSelected() ? samePasswordField.getText() : null;
        for (Row row : chosen) {
            row.password = same != null ? same : TeacherAccountGenerator.randomPassword();
        }

        Map<Row, String> failures = new HashMap<>();
        List<Row> created = new ArrayList<>();
        String currentUser = AuthContext.getCurrentUsername();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i < chosen.size(); i++) {
                    Row row = chosen.get(i);
                    updateMessage("Membuat akun " + (i + 1) + " dari " + chosen.size() + ": " + row.teacher.getName());
                    try {
                        User user = new User();
                        user.setUsername(row.username.get());
                        user.setFullName(row.teacher.getName());
                        user.setRole("guru");
                        user.setTeacher(row.teacher);
                        userService.register(user, row.password, currentUser);
                        created.add(row);
                    } catch (Exception ex) {
                        failures.put(row, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                    }
                    updateProgress(i + 1, chosen.size());
                }
                return null;
            }
        };

        Stage progressStage = progressStage(task);
        task.setOnSucceeded(e -> {
            progressStage.close();
            finish(created, failures);
        });
        task.setOnFailed(e -> {
            progressStage.close();
            finish(created, failures);
        });
        progressStage.show();
        Thread thread = new Thread(task, "buat-akun-guru");
        thread.setDaemon(true);
        thread.start();
    }

    private Stage progressStage(Task<?> task) {
        Label status = new Label();
        status.setWrapText(true);
        status.setPrefWidth(380);
        status.textProperty().bind(task.messageProperty());
        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(380);
        bar.progressProperty().bind(task.progressProperty());
        VBox box = new VBox(10, new Label("Sedang membuat akun guru, mohon tunggu..."), bar, status);
        box.setPadding(new Insets(16));
        Stage s = new Stage(StageStyle.UTILITY);
        s.setTitle("Membuat Akun");
        s.initModality(Modality.APPLICATION_MODAL);
        s.initOwner(stage);
        s.setResizable(false);
        s.setOnCloseRequest(e -> e.consume());
        s.setScene(new Scene(box));
        return s;
    }

    private void finish(List<Row> created, Map<Row, String> failures) {
        if (!created.isEmpty()) {
            saveExcel(created);
        }

        StringBuilder text = new StringBuilder("Akun berhasil dibuat: " + created.size() + " guru.");
        if (!failures.isEmpty()) {
            text.append("\nGagal: ").append(failures.size()).append(" guru (alasan di bawah).");
        }
        Alert alert = new Alert(failures.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        alert.setTitle("Hasil Buat Akun");
        alert.setHeaderText(null);
        alert.initOwner(stage);
        Label label = new Label(text.toString());
        label.setWrapText(true);
        VBox content = new VBox(8, label);
        if (!failures.isEmpty()) {
            List<String> lines = new ArrayList<>();
            failures.forEach((row, reason) -> lines.add(row.teacher.getName() + " (" + row.username.get() + ") — " + reason));
            TextArea detail = new TextArea(String.join("\n", lines));
            detail.setEditable(false);
            detail.setWrapText(true);
            detail.setPrefRowCount(Math.min(8, lines.size() + 1));
            content.getChildren().add(detail);
        }
        content.setPrefWidth(460);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();

        stage.close();
        if (onCreated != null && !created.isEmpty()) {
            onCreated.run();
        }
    }

    /** Wajib disimpan: password awal tidak bisa ditampilkan lagi setelah jendela ini ditutup. */
    private void saveExcel(List<Row> created) {
        List<TeacherAccountExcelWriter.Account> accounts = created.stream()
                .map(r -> new TeacherAccountExcelWriter.Account(r.teacher.getName(), r.teacher.getNip(),
                        r.username.get(), r.password))
                .toList();
        while (true) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Simpan daftar akun guru (username & password)");
            chooser.setInitialFileName("akun-guru-" + java.time.LocalDate.now() + ".xlsx");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("File Excel (*.xlsx)", "*.xlsx"));
            File file = chooser.showSaveDialog(stage);
            if (file != null) {
                try {
                    TeacherAccountExcelWriter.write(file, accounts);
                    return;
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, "Gagal menyimpan file: " + ex.getMessage()
                            + "\nJika file sedang dibuka di Excel, tutup dulu.").showAndWait();
                    continue;
                }
            }
            Alert warn = new Alert(Alert.AlertType.WARNING,
                    "Daftar password belum disimpan. Jika dilewati, password awal " + accounts.size()
                            + " guru tidak bisa dilihat lagi dan harus di-reset satu per satu di menu Manajemen Pengguna.\n\n"
                            + "Simpan sekarang?", ButtonType.YES, ButtonType.NO);
            warn.initOwner(stage);
            if (warn.showAndWait().filter(b -> b == ButtonType.NO).isPresent()) {
                return;
            }
        }
    }
}