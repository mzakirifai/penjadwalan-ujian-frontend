package co.id.controller.pages;

import co.id.component.LookupBox;
import co.id.model.ExamSchedule;
import co.id.model.ExamScore;
import co.id.service.ExamScheduleService;
import co.id.service.ReportService;
import co.id.service.impl.ExamScheduleServiceImpl;
import co.id.service.impl.ReportServiceImpl;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.StackPane;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import win.zqxu.jrviewer.JRViewerFX;

public class ReportExamResultController {
    @FXML private LookupBox<ExamSchedule> lookupBoxExamSchedule;
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

        btnViewReport.setOnAction(e -> onViewReport());
    }

    private void onViewReport() {
        ExamSchedule selectedSchedule = lookupBoxExamSchedule.getSelectedItem();

        if (selectedSchedule == null) {
            new Alert(Alert.AlertType.WARNING, "Pilih jadwal ujian terlebih dahulu!", ButtonType.OK).showAndWait();
            return;
        }

        try {
            JasperReport jasperReport = (JasperReport) JRLoader.loadObject(
                getClass().getResourceAsStream("/reports/ReportExamResult.jasper")
            );

            List<ExamScore> data = reportService.getExamResultReport(selectedSchedule.getId());

            if (data.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "Belum ada nilai untuk sesi ujian ini.", ButtonType.OK).showAndWait();
                return;
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("SUBJECT_NAME", selectedSchedule.getSubject() != null ? selectedSchedule.getSubject().getName() : "-");
            parameters.put("CLASSROOM_NAME", selectedSchedule.getClassroom() != null ? selectedSchedule.getClassroom().getName() : "-");
            parameters.put("EXAM_DATE", selectedSchedule.getDate() != null
                ? selectedSchedule.getDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "-");
            parameters.put("SEMESTER", selectedSchedule.getSemester() != null ? selectedSchedule.getSemester() : "-");
            parameters.put("ACADEMIC_YEAR", selectedSchedule.getAcademicYear() != null ? selectedSchedule.getAcademicYear() : "-");

            JRDataSource dataSource = new JRBeanCollectionDataSource(data);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            JRViewerFX viewerFX = new JRViewerFX(jasperPrint);
            reportPane.getChildren().setAll(viewerFX);

        } catch (JRException ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Gagal memuat laporan: " + ex.getMessage()).showAndWait();
        }
    }
}