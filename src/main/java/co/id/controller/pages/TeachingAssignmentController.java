package co.id.controller.pages;

import co.id.component.BulkDeleteBar;
import co.id.auth.AuthContext;
import co.id.component.ExcelImportActions;
import co.id.model.Teacher;
import co.id.model.TeachingAssignment;
import co.id.service.TeachingAssignmentService;
import co.id.service.impl.TeachingAssignmentServiceImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Pagination;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Daftar pengampu: satu baris per guru. Klik baris guru untuk membuka/menutup rincian
 * mapel + kelas yang diampu. Beberapa guru boleh terbuka bersamaan.
 */
public class TeachingAssignmentController {
    private static final int ROWS_PER_PAGE = 10;

    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private Button importBtn;
    @FXML private Button templateBtn;
    @FXML private TreeTableView<RowData> tableView;

    @FXML private TreeTableColumn<RowData, String> tableColumnTeacher, tableColumnNip, tableColumnSubject, tableColumnClassroom;
    @FXML private TreeTableColumn<RowData, RowData> tableColumnAction;

    @FXML private Pagination pagination;

    private TeachingAssignmentService teachingAssignmentService;
    private List<TeacherGroup> groups = new ArrayList<>();
    private String currentKeyword = "";

    // id guru yang sedang terbuka; dipertahankan saat tabel dimuat ulang
    private final Set<Integer> expandedTeacherIds = new HashSet<>();

    /** Gabungan semua pengampu milik satu guru. */
    private static class TeacherGroup {
        final Teacher teacher;
        final List<TeachingAssignment> assignments = new ArrayList<>();

        TeacherGroup(Teacher teacher) {
            this.teacher = teacher;
        }

        String subjectSummary() {
            return summarize(assignments.stream().map(a -> a.getSubjectLabel())
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        String classroomSummary() {
            return summarize(assignments.stream().map(a -> a.getClassroom().getName())
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        // Ringkasan singkat: "DDPK, MPKK +1"
        private static String summarize(Set<String> values) {
            List<String> list = new ArrayList<>(values);
            if (list.size() <= 2) {
                return String.join(", ", list);
            }
            return list.get(0) + ", " + list.get(1) + " +" + (list.size() - 2);
        }
    }

    /** Isi satu baris tabel: baris guru (group) atau baris rincian (assignment). */
    private static class RowData {
        final TeacherGroup group;
        final TeachingAssignment assignment;

        private RowData(TeacherGroup group, TeachingAssignment assignment) {
            this.group = group;
            this.assignment = assignment;
        }

        static RowData ofGroup(TeacherGroup group) {
            return new RowData(group, null);
        }

        static RowData ofAssignment(TeachingAssignment assignment) {
            return new RowData(null, assignment);
        }

        boolean isGroup() {
            return group != null;
        }
    }

    private ImageView createIcon(String path) {
        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        return imageView;
    }

    @FXML
    public void initialize() {
        teachingAssignmentService = new TeachingAssignmentServiceImpl();

        tableView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setShowRoot(false);

        tableColumnTeacher.setCellValueFactory(c -> {
            RowData row = c.getValue().getValue();
            return new ReadOnlyStringWrapper(row.isGroup()
                    ? row.group.teacher.getName() + " (" + row.group.assignments.size() + ")"
                    : "");
        });
        tableColumnNip.setCellValueFactory(c -> {
            RowData row = c.getValue().getValue();
            return new ReadOnlyStringWrapper(row.isGroup() ? (row.group.teacher.getNip() != null ? row.group.teacher.getNip() : "-") : "");
        });

        // Baris guru: tampilkan ringkasan hanya saat tertutup; saat terbuka rinciannya sudah tampil di bawah
        tableColumnSubject.setCellValueFactory(c -> {
            TreeItem<RowData> item = c.getValue();
            RowData row = item.getValue();
            if (!row.isGroup()) {
                return new ReadOnlyStringWrapper(row.assignment.getSubjectLabel());
            }
            return Bindings.createStringBinding(
                    () -> item.isExpanded() ? "" : row.group.subjectSummary(), item.expandedProperty());
        });
        tableColumnClassroom.setCellValueFactory(c -> {
            TreeItem<RowData> item = c.getValue();
            RowData row = item.getValue();
            if (!row.isGroup()) {
                return new ReadOnlyStringWrapper(row.assignment.getClassroom().getName());
            }
            return Bindings.createStringBinding(
                    () -> item.isExpanded() ? "" : row.group.classroomSummary(), item.expandedProperty());
        });

        // Isi sel = data barisnya sendiri. Jika isinya teks kosong yang sama di semua baris,
        // JavaFX tidak memanggil updateItem saat data diganti, sehingga tombol lama (milik data lama) bisa tertinggal.
        tableColumnAction.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getValue()));
        tableColumnAction.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(RowData row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(row.isGroup() ? groupActions(row.group) : assignmentActions(row.assignment));
            }
        });

        // Klik baris guru = buka/tutup rincian (guru lain tidak ikut tertutup)
        tableView.setRowFactory(tv -> {
            TreeTableRow<RowData> row = new TreeTableRow<>() {
                @Override
                protected void updateItem(RowData item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("teacher-group-row", "teacher-detail-row");
                    if (!empty && item != null) {
                        getStyleClass().add(item.isGroup() ? "teacher-group-row" : "teacher-detail-row");
                    }
                }
            };
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() != 1 || row.isEmpty() || row.getItem() == null || !row.getItem().isGroup()) {
                    return;
                }
                // panah bawaan sudah menangani toggle-nya sendiri
                if (isDisclosureNode(e.getTarget(), row) || isInsideButton(e.getTarget(), row)) {
                    return;
                }
                TreeItem<RowData> item = row.getTreeItem();
                if (item != null) {
                    item.setExpanded(!item.isExpanded());
                }
            });
            return row;
        });

        pagination.setPageFactory(pageIndex -> {
            showPage(pageIndex);
            return new VBox(tableView);
        });

        filterBtn.setOnAction(eh -> filterItems());
        searchField.setOnAction(eh -> filterItems());
        addBtn.setOnAction(eh -> openForm(null, null));
        refreshBtn.setOnAction(e -> {
            searchField.clear();
            currentKeyword = "";
            reload();
        });

        // Import Excel hanya untuk admin (sama seperti hak akses menyimpan data pengampu)
        if (!AuthContext.isAdmin()) {
            importBtn.setVisible(false);
            importBtn.setManaged(false);
            templateBtn.setVisible(false);
            templateBtn.setManaged(false);
        }
        importBtn.setOnAction(eh -> ExcelImportActions.importTeachingAssignments(
                (Region) importBtn.getScene().getRoot(), importBtn.getScene().getWindow(), this::reload));
        templateBtn.setOnAction(eh -> ExcelImportActions.saveTeachingAssignmentTemplate(templateBtn.getScene().getWindow()));

        // Hapus banyak pengampu sekaligus. Centang baris guru = semua pengampu guru itu.
        if (AuthContext.isAdmin()) {
            new BulkDeleteBar<TeachingAssignment>("pengampu", ta -> ta.getId(),
                    ta -> ta.getTeacher().getName() + " - " + ta.getSubjectLabel() + " (" + ta.getClassroom().getName() + ")",
                    ta -> teachingAssignmentService.delete(ta.getId(), AuthContext.getCurrentRole()))
                    .attachTo(tableView, row -> row.isGroup() ? row.group.assignments : List.of(row.assignment))
                    // semua guru di semua halaman (atau semua hasil pencarian)
                    .allItems(() -> groups.stream().flatMap(g -> g.assignments.stream()).toList())
                    .afterDelete(this::reload)
                    .placeBelow(addBtn.getParent());
        }

        reload();
    }

    private boolean isInsideButton(Object target, Node stopAt) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null && node != stopAt) {
            if (node instanceof javafx.scene.control.ButtonBase) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private boolean isDisclosureNode(Object target, Node stopAt) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null && node != stopAt) {
            if (node.getStyleClass().contains("tree-disclosure-node")) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private HBox groupActions(TeacherGroup group) {
        Button buttonAdd = new Button("Tambah");
        buttonAdd.setGraphic(createIcon("/icons/add.png"));
        buttonAdd.getStyleClass().add("list-menu-top-button");
        buttonAdd.setOnAction(eh -> openForm(null, group.teacher));
        return new HBox(5, buttonAdd);
    }

    private HBox assignmentActions(TeachingAssignment ta) {
        Button buttonEdit = new Button("Edit");
        Button buttonDelete = new Button("Hapus");
        buttonEdit.setGraphic(createIcon("/icons/edit.png"));
        buttonDelete.setGraphic(createIcon("/icons/trash.png"));
        buttonEdit.getStyleClass().add("btn-edit");
        buttonDelete.getStyleClass().add("btn-delete");

        buttonEdit.setOnAction(eh -> openForm(ta, null));

        buttonDelete.setOnAction(eh -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Yakin ingin menghapus pengampu \"" + ta.getTeacher().getName() + " - "
                            + ta.getSubjectLabel() + " (" + ta.getClassroom().getName() + ")\"?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Konfirmasi Hapus");
            confirm.setHeaderText(null);

            confirm.showAndWait().ifPresent(action -> {
                if (action == ButtonType.YES) {
                    try {
                        teachingAssignmentService.delete(ta.getId(), AuthContext.getCurrentRole());
                        reload();
                    } catch (Exception ex) {
                        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
                    }
                }
            });
        });
        return new HBox(5, buttonEdit, buttonDelete);
    }

    private void filterItems() {
        String keyword = searchField.getText();
        currentKeyword = keyword == null ? "" : keyword.trim();
        pagination.setCurrentPageIndex(0);
        reload();
    }

    /** Ambil data dari service, kelompokkan per guru, lalu tampilkan halaman yang aktif. */
    private void reload() {
        List<TeachingAssignment> rows = currentKeyword.isEmpty()
                ? teachingAssignmentService.getAllTeachingAssignments()
                : teachingAssignmentService.getTeachingAssignmentBy(currentKeyword);

        groups = group(rows);

        int pageCount = Math.max((int) Math.ceil((double) groups.size() / ROWS_PER_PAGE), 1);
        pagination.setPageCount(pageCount);

        int pageIndex = Math.min(pagination.getCurrentPageIndex(), pageCount - 1);
        pagination.setCurrentPageIndex(pageIndex);
        showPage(pageIndex);
    }

    private List<TeacherGroup> group(List<TeachingAssignment> rows) {
        Map<Integer, TeacherGroup> map = new LinkedHashMap<>();
        for (TeachingAssignment ta : rows) {
            map.computeIfAbsent(ta.getTeacher().getId(), id -> new TeacherGroup(ta.getTeacher()))
                    .assignments.add(ta);
        }
        return new ArrayList<>(map.values());
    }

    private void showPage(int pageIndex) {
        int from = Math.min(pageIndex * ROWS_PER_PAGE, groups.size());
        int to = Math.min(from + ROWS_PER_PAGE, groups.size());

        TreeItem<RowData> root = new TreeItem<>();
        root.setExpanded(true);

        for (TeacherGroup group : groups.subList(from, to)) {
            int teacherId = group.teacher.getId();
            TreeItem<RowData> groupItem = new TreeItem<>(RowData.ofGroup(group));
            groupItem.setExpanded(expandedTeacherIds.contains(teacherId));
            groupItem.expandedProperty().addListener((obs, was, isNow) -> {
                if (isNow) {
                    expandedTeacherIds.add(teacherId);
                } else {
                    expandedTeacherIds.remove(teacherId);
                }
            });

            for (TeachingAssignment ta : group.assignments) {
                groupItem.getChildren().add(new TreeItem<>(RowData.ofAssignment(ta)));
            }
            root.getChildren().add(groupItem);
        }
        tableView.setRoot(root);
    }

    // ---------- form tambah/edit ----------

    private void openForm(TeachingAssignment teachingAssignment, Teacher presetTeacher) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/TeachingAssignmentForm.fxml"));
            Parent formRoot = loader.load();

            TeachingAssignmentFormController formController = loader.getController();
            if (teachingAssignment != null) {
                formController.setTeachingAssignment(teachingAssignment);
            } else if (presetTeacher != null) {
                formController.setTeacher(presetTeacher);
            }
            formController.setOnSaveCallback(() -> {
                // Buka baris guru yang baru disimpan, apa pun tombol Tambah/Edit yang dipakai
                Teacher saved = formController.getSelectedTeacher();
                if (saved != null) {
                    expandedTeacherIds.add(saved.getId());
                }
                reload();
            });

            Region mainRoot = (Region) addBtn.getScene().getRoot();
            mainRoot.setEffect(new GaussianBlur(10));

            Stage dialog = new Stage();
            dialog.setTitle(teachingAssignment == null ? "Tambah Pengampu" : "Edit Pengampu");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);

            // Mode tambah memuat daftar centang kelas & mapel; tinggi dibatasi agar tombol Simpan tidak terpotong
            double maxHeight = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() - 80;
            Scene scene = teachingAssignment == null
                    ? new Scene(formRoot, 480, Math.min(700, maxHeight))
                    : new Scene(formRoot, 440, Math.min(400, maxHeight));
            scene.getStylesheets().add(getClass().getResource("/css/material.css").toExternalForm());

            dialog.setScene(scene);
            dialog.centerOnScreen();
            dialog.setOnHidden(eh -> mainRoot.setEffect(null));
            dialog.showAndWait();
        } catch (IOException e) {
            co.id.component.ErrorDialog.show("TeachingAssignmentController.openForm", "Form pengampu tidak bisa dibuka.", e);
        }
    }
}