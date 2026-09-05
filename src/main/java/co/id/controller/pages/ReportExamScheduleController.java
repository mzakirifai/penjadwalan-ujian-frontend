package co.id.controller.pages;

import co.id.model.ExamSchedule;
import co.id.service.ReportService;
import co.id.service.impl.ReportServiceImpl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
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

public class ReportExamScheduleController {
    @FXML private ComboBox<String> comboExamType;
    @FXML private ComboBox<String> comboSemester;
    @FXML private TextField textFieldAcademicYear;
    @FXML private Button btnViewReport;
    @FXML private StackPane reportPane;

    private ReportService reportService;

    @FXML
    public void initialize() {
        reportService = new ReportServiceImpl();

        comboExamType.setItems(FXCollections.observableArrayList("UTS", "UAS", "UKK", "PRAKTIK"));
        comboSemester.setItems(FXCollections.observableArrayList("Ganjil", "Genap"));

        btnViewReport.setOnAction(e -> onViewReport());
    }

    private void onViewReport() {
        String examType = comboExamType.getValue();
        String semester = comboSemester.getValue();
        String academicYear = textFieldAcademicYear.getText();

        if (examType == null || semester == null || academicYear == null || academicYear.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Jenis Ujian, Semester, dan Tahun Akademik wajib diisi!", ButtonType.OK).showAndWait();
            return;
        }

        try {
            JasperReport jasperReport = (JasperReport) JRLoader.loadObject(
                getClass().getResourceAsStream("/reports/ReportExamSchedule.jasper")
            );

            List<ExamSchedule> data = reportService.getExamScheduleReport(examType, semester, academicYear);

            if (data.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "Tidak ada jadwal ujian untuk periode ini.", ButtonType.OK).showAndWait();
                return;
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("EXAM_TYPE", examType);
            parameters.put("SEMESTER", semester);
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
}