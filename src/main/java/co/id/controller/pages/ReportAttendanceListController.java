package co.id.controller.pages;

import co.id.component.LookupBox;
import co.id.model.ExamParticipant;
import co.id.model.ExamSchedule;
import co.id.service.ExamScheduleService;
import co.id.service.ReportService;
import co.id.service.impl.ExamScheduleServiceImpl;
import co.id.service.impl.ReportServiceImpl;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import win.zqxu.jrviewer.JRViewerFX;

public class ReportAttendanceListController {
    @FXML private LookupBox<ExamSchedule> lookupBoxExamSchedule;
    @FXML private ComboBox<String> comboExamType;
    @FXML private ComboBox<String> comboSemester;
    @FXML private TextField textFieldAcademicYear;
    @FXML private Button btnViewReport;
    @FXML private StackPane reportPane;

    private ExamScheduleService examScheduleService;
    private ReportService reportService;

    @FXML
    public void initialize() {
        examScheduleService = new ExamScheduleServiceImpl();
        reportService = new ReportServiceImpl();

        TableColumn<ExamSchedule, String> colSubject = new TableColumn<>("Mapel");
        colSubject.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getSubject() != null ? d.getValue().getSubject().getName() : ""));

        TableColumn<ExamSchedule, String> colClassroom = new TableColumn<>("Kelas");
        colClassroom.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getClassroom() != null ? d.getValue().getClassroom().getName() : ""));

        TableColumn<ExamSchedule, String> colDate = new TableColumn<>("Tanggal");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getDate() != null ? d.getValue().getDate().toString() : ""));

        lookupBoxExamSchedule.configure(
            () -> examScheduleService.getAllExamSchedulesDetailed(),
            List.of(colSubject, colClassroom, colDate),
            ExamSchedule::getCode
        );
        
        comboExamType.setItems(FXCollections.observableArrayList("UTS", "UAS", "UKK", "PRAKTIK"));
        comboSemester.setItems(FXCollections.observableArrayList("Ganjil", "Genap"));

        btnViewReport.setOnAction(e -> onViewReport());
    }

    private void onViewReport() {
        ExamSchedule selectedSchedule = lookupBoxExamSchedule.getSelectedItem();
        String examType = comboExamType.getValue();
        String semester = comboSemester.getValue();
        String academicYear = textFieldAcademicYear.getText();
        
        if (selectedSchedule == null) {
            new Alert(Alert.AlertType.WARNING, "Jadwal Ujian, Jenis Ujian, Semester, dan Tahun Akademik wajib diisi!", ButtonType.OK).showAndWait();
            return;
        }

        try {
            JasperReport jasperReport = (JasperReport) JRLoader.loadObject(
                getClass().getResourceAsStream("/reports/ReportAttendanceList.jasper")
            );

            List<ExamParticipant> data = reportService.getAttendanceListReport(selectedSchedule.getId());

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("SUBJECT_NAME", selectedSchedule.getSubject() != null ? selectedSchedule.getSubject().getName() : "-");
            parameters.put("CLASSROOM_NAME", selectedSchedule.getClassroom() != null ? selectedSchedule.getClassroom().getName() : "-");
            parameters.put("ROOM_NAME", selectedSchedule.getRoom() != null ? selectedSchedule.getRoom().getName() : "-");
            parameters.put("TEACHER_NAME", selectedSchedule.getTeacher() != null ? selectedSchedule.getTeacher().getName() : "-");
            parameters.put("TEACHER_NIP", selectedSchedule.getTeacher() != null ? selectedSchedule.getTeacher().getNip() : "-");
            parameters.put("EXAM_DATE", selectedSchedule.getDate() != null
                ? selectedSchedule.getDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "-");
            parameters.put("EXAM_TIME", (selectedSchedule.getStartTime() != null ? selectedSchedule.getStartTime().toString() : "-")
                + " - " + (selectedSchedule.getEndTime() != null ? selectedSchedule.getEndTime().toString() : "-"));
            parameters.put("EXAM_TYPE_LABEL", toExamTypeLabel(examType));
            parameters.put("SEMESTER", semester.toUpperCase());
            parameters.put("ACADEMIC_YEAR", academicYear);
            
            JRDataSource dataSource = new JRBeanCollectionDataSource(data);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            JRViewerFX viewerFX = new JRViewerFX(jasperPrint);
            reportPane.getChildren().setAll(viewerFX);

        } catch (JRException ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Gagal memuat laporan: " + ex.getMessage()).showAndWait();
        }
    }
    
    private String toExamTypeLabel(String examType) {
        return switch (examType) {
            case "UTS" -> "UJIAN TENGAH SEMESTER";
            case "UAS" -> "UJIAN AKHIR SEMESTER";
            case "UKK" -> "UJIAN KENAIKAN KELAS";
            case "PRAKTIK" -> "UJIAN PRAKTIK";
            default -> examType;
        };
    }
}