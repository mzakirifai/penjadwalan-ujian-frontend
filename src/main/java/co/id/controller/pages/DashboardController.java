package co.id.controller.pages;

import co.id.model.ExamSchedule;
import co.id.service.ExamParticipantService;
import co.id.service.ExamScheduleService;
import co.id.service.ExamScoreService;
import co.id.service.MasterService;
import co.id.service.impl.ExamParticipantServiceImpl;
import co.id.service.impl.ExamScheduleServiceImpl;
import co.id.service.impl.ExamScoreServiceImpl;
import co.id.service.impl.MasterServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class DashboardController {
    @FXML private Label lblTotalTeacher;
    @FXML private Label lblTotalMajor;
    @FXML private Label lblTotalClassroom;
    @FXML private Label lblTotalStudent;
    @FXML private Label lblTotalSubject;
    @FXML private Label lblTotalRoom;
    @FXML private Label lblTotalExamSchedule;
    @FXML private Label lblTotalExamParticipant;
    @FXML private Label lblTotalExamScore;
    
    @FXML private TableView<ExamSchedule> tableTodaySchedule;
    @FXML private TableColumn<ExamSchedule, String> colTodayKode;
    @FXML private TableColumn<ExamSchedule, String> colTodayJenis;
    @FXML private TableColumn<ExamSchedule, String> colTodayMapel;
    @FXML private TableColumn<ExamSchedule, String> colTodayKelas;
    @FXML private TableColumn<ExamSchedule, String> colTodayRuangan;
    @FXML private TableColumn<ExamSchedule, String> colTodayJamMulai;
    @FXML private TableColumn<ExamSchedule, String> colTodayJamSelesai;
    @FXML private TableColumn<ExamSchedule, String> colTodayPengawas;
    
    private MasterService masterService;
    private ExamScheduleService examScheduleService;
    private ExamParticipantService examParticipantService;
    private ExamScoreService examScoreService;
    
    @FXML
    public void initialize(){
        masterService = new MasterServiceImpl();
        examScheduleService = new ExamScheduleServiceImpl();
        examParticipantService = new ExamParticipantServiceImpl();
        examScoreService = new ExamScoreServiceImpl();

        lblTotalTeacher.setText(String.valueOf(masterService.countTeachers()));
        lblTotalMajor.setText(String.valueOf(masterService.countMajors()));
        lblTotalClassroom.setText(String.valueOf(masterService.countClassrooms()));
        lblTotalStudent.setText(String.valueOf(masterService.countStudents()));
        lblTotalSubject.setText(String.valueOf(masterService.countSubjects()));
        lblTotalRoom.setText(String.valueOf(masterService.countRooms()));
        
        lblTotalExamSchedule.setText(String.valueOf(examScheduleService.countExamSchedules()));
        lblTotalExamParticipant.setText(String.valueOf(examParticipantService.countExamParticipants()));
        lblTotalExamScore.setText(String.valueOf(examScoreService.countExamScores()));
        
        setupTodayScheduleTable();
    }
    
    private void setupTodayScheduleTable() {
        tableTodaySchedule.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colTodayKode.setCellValueFactory(a ->
                new SimpleStringProperty(a.getValue().getCode()));
        colTodayJenis.setCellValueFactory(a ->
                new SimpleStringProperty(a.getValue().getExamType()));
        colTodayMapel.setCellValueFactory(a ->
                new SimpleStringProperty(a.getValue().getSubject() != null ? a.getValue().getSubject().getName() : ""));
        colTodayKelas.setCellValueFactory(a ->
                new SimpleStringProperty(a.getValue().getClassroom() != null ? a.getValue().getClassroom().getName() : ""));
        colTodayRuangan.setCellValueFactory(a ->
                new SimpleStringProperty(a.getValue().getRoom() != null ? a.getValue().getRoom().getName() : ""));
        colTodayJamMulai.setCellValueFactory(a ->
                new SimpleStringProperty(a.getValue().getStartTime() != null ? a.getValue().getStartTime().toString() : ""));
        colTodayJamSelesai.setCellValueFactory(a ->
                new SimpleStringProperty(a.getValue().getEndTime() != null ? a.getValue().getEndTime().toString() : ""));
        colTodayPengawas.setCellValueFactory(a ->
                new SimpleStringProperty(a.getValue().getTeacher() != null ? a.getValue().getTeacher().getName() : ""));

        tableTodaySchedule.getItems().setAll(examScheduleService.getTodayExamSchedules());

        tableTodaySchedule.setFixedCellSize(35);
        tableTodaySchedule.prefHeightProperty().bind(
                tableTodaySchedule.fixedCellSizeProperty().multiply(
                        javafx.beans.binding.Bindings.size(tableTodaySchedule.getItems()).add(1.05)
                )
        );
    }
}
