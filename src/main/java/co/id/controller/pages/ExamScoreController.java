package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.component.EditableCell;
import co.id.component.LookupBox;
import co.id.model.ExamParticipant;
import co.id.model.ExamSchedule;
import co.id.model.ExamScore;
import co.id.model.Student;
import co.id.service.ExamParticipantService;
import co.id.service.ExamScheduleService;
import co.id.service.ExamScoreService;
import co.id.service.impl.ExamParticipantServiceImpl;
import co.id.service.impl.ExamScheduleServiceImpl;
import co.id.service.impl.ExamScoreServiceImpl;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public class ExamScoreController {
    @FXML private LookupBox<ExamSchedule> lookupBoxExamSchedule;
    @FXML private Label lblInfo;
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private TableView<ExamScore> tableView;

    @FXML private TableColumn<ExamScore, String> tableColumnNis, tableColumnName, tableColumnScore, tableColumnStatus;
    @FXML private TableColumn<ExamScore, Void> tableColumnAction;

    private ExamScoreService examScoreService;
    private ExamParticipantService examParticipantService;
    private ExamScheduleService examScheduleService;
    private ObservableList<ExamScore> observableList;

    private ExamSchedule selectedExamSchedule;
    private int editingRowIndex = -1;

    private ImageView createIcon(String path) {
        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        return imageView;
    }

    @FXML
    public void initialize() {
        examScoreService = new ExamScoreServiceImpl();
        examParticipantService = new ExamParticipantServiceImpl();
        examScheduleService = new ExamScheduleServiceImpl();
        observableList = FXCollections.observableArrayList();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ExamSchedule, String> colScheduleLabel = new TableColumn<>("Jadwal Ujian");
        colScheduleLabel.setCellValueFactory(e -> new SimpleStringProperty(scheduleLabel(e.getValue())));
        lookupBoxExamSchedule.configure(
                () -> examScheduleService.getAllExamSchedulesDetailed(),
                List.of(colScheduleLabel),
                this::scheduleLabel
        );
        lookupBoxExamSchedule.setFieldWidth(350);

        lookupBoxExamSchedule.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedExamSchedule = newVal;
            editingRowIndex = -1;
            refreshTable();
        });

        tableColumnNis.setCellValueFactory(s ->
                new SimpleStringProperty(s.getValue().getStudent() != null ? s.getValue().getStudent().getNis() : "-"));
        tableColumnName.setCellValueFactory(s ->
                new SimpleStringProperty(s.getValue().getStudent() != null ? s.getValue().getStudent().getName() : "-"));

        tableColumnScore.setCellFactory(tc -> new EditableCell<ExamScore>(
                es -> es.getScore() > 0 ? String.valueOf(es.getScore()) : "",
                (es, val) -> {
                    try {
                        es.setScore(Integer.parseInt(val.trim()));
                    } catch (Exception ex) {
                        es.setScore(0);
                    }
                },
                () -> editingRowIndex
        ));

        tableColumnStatus.setCellValueFactory(s ->
                new SimpleStringProperty(s.getValue().getId() == 0 ? "Belum dinilai" : "Sudah dinilai"));

        tableColumnAction.setCellFactory(params -> new TableCell<>() {
            private final Button btnEdit = new Button("Edit");
            private final Button btnDelete = new Button("Hapus");
            private final Button btnSave = new Button("Simpan");
            private final Button btnCancel = new Button("Batal");
            private final HBox boxView = new HBox(5, btnEdit, btnDelete);
            private final HBox boxEdit = new HBox(5, btnSave, btnCancel);

            {
                btnEdit.setGraphic(createIcon("/icons/edit.png"));
                btnDelete.setGraphic(createIcon("/icons/trash.png"));
                btnSave.setGraphic(createIcon("/icons/save.png"));
                btnCancel.setGraphic(createIcon("/icons/cancel.png"));

                btnEdit.getStyleClass().add("btn-edit");
                btnDelete.getStyleClass().add("btn-delete");
                btnSave.getStyleClass().add("btn-save");
                btnCancel.getStyleClass().add("btn-cancel");

                btnEdit.setOnAction(e -> {
                    if (!canEditCurrentSchedule()) {
                        new Alert(AlertType.WARNING, "Anda hanya bisa menginput nilai untuk ujian yang Anda awasi sendiri.").showAndWait();
                        return;
                    }
                    editingRowIndex = getIndex();
                    tableView.refresh();
                });

                btnDelete.setOnAction(e -> {
                    ExamScore examScore = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Yakin ingin menghapus nilai \"" + examScore.getStudent().getName() + "\"?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.YES) {
                            try {
                                examScoreService.delete(examScore.getId(), AuthContext.getCurrentRole());
                                refreshTable();
                            } catch (Exception ex) {
                                new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
                            }
                        }
                    });
                });

                btnSave.setOnAction(e -> {
                    ExamScore examScore = getTableView().getItems().get(getIndex());

                    if (examScore.getScore() <= 0) {
                        new Alert(AlertType.WARNING, "Isi nilai terlebih dahulu (0-100)").showAndWait();
                        return;
                    }

                    try {
                        Integer currentTeacherId = AuthContext.getCurrentUser().getTeacher() != null
                                ? AuthContext.getCurrentUser().getTeacher().getId()
                                : null;

                        examScoreService.save(examScore, AuthContext.getCurrentUsername(),
                                AuthContext.getCurrentRole(), currentTeacherId);
                    } catch (Exception ex) {
                        new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
                        return;
                    }

                    editingRowIndex = -1;
                    refreshTable();
                });

                btnCancel.setOnAction(e -> {
                    editingRowIndex = -1;
                    refreshTable();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                boolean isEditable = canEditCurrentSchedule();

                if (!isEditable) {
                    setGraphic(null);
                    return;
                }

                btnDelete.setVisible(AuthContext.isAdmin());
                btnDelete.setManaged(AuthContext.isAdmin());

                setGraphic(getIndex() == editingRowIndex ? boxEdit : boxView);
            }
        });

        filterBtn.setOnAction(e -> filterScores());
    }

    private String scheduleLabel(ExamSchedule es) {
        return es.getCode() + " - "
                + (es.getSubject() != null ? es.getSubject().getName() : "-")
                + " - " + (es.getClassroom() != null ? es.getClassroom().getName() : "-");
    }

    private boolean canEditCurrentSchedule() {
        if (selectedExamSchedule == null) {
            return false;
        }
        if (AuthContext.isAdmin()) {
            return true;
        }
        if (AuthContext.getCurrentUser().getTeacher() == null || selectedExamSchedule.getTeacher() == null) {
            return false;
        }
        return AuthContext.getCurrentUser().getTeacher().getId() == selectedExamSchedule.getTeacher().getId();
    }

    private void refreshTable() {
        tableView.setItems(FXCollections.observableArrayList());

        if (selectedExamSchedule == null) {
            lblInfo.setText("");
            return;
        }

        List<ExamParticipant> participants = examParticipantService.getByExamSchedule(selectedExamSchedule.getId());
        List<ExamScore> existingScores = examScoreService.getByExamSchedule(selectedExamSchedule.getId());

        List<ExamScore> rows = participants.stream()
                .map(p -> {
                    Student student = p.getStudent();
                    return existingScores.stream()
                            .filter(es -> es.getStudent() != null && es.getStudent().getId() == student.getId())
                            .findFirst()
                            .orElseGet(() -> {
                                ExamScore newScore = new ExamScore();
                                newScore.setExamSchedule(selectedExamSchedule);
                                newScore.setStudent(student);
                                newScore.setScore(0);
                                return newScore;
                            });
                })
                .toList();

        observableList.setAll(rows);
        tableView.setItems(observableList);

        long alreadyScored = rows.stream().filter(r -> r.getId() != 0).count();
        lblInfo.setText("Sudah dinilai: " + alreadyScored + " / " + rows.size() + " peserta");
    }

    private void filterScores() {
        if (selectedExamSchedule == null) {
            return;
        }

        String keyword = searchField.getText();

        if (keyword == null || keyword.isBlank()) {
            refreshTable();
            return;
        }

        String lowerKeyword = keyword.toLowerCase();
        ObservableList<ExamScore> filtered = FXCollections.observableArrayList(
                observableList.stream()
                        .filter(es -> (es.getStudent().getName() != null && es.getStudent().getName().toLowerCase().contains(lowerKeyword))
                                || (es.getStudent().getNis() != null && es.getStudent().getNis().toLowerCase().contains(lowerKeyword)))
                        .toList()
        );
        tableView.setItems(filtered);
    }
}