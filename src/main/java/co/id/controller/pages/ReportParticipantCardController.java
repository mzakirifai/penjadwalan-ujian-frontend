package co.id.controller.pages;

import co.id.component.LookupBox;
import co.id.model.Classroom;
import co.id.model.Student;
import co.id.model.report.ParticipantCardReportItem;
import co.id.service.MasterService;
import co.id.service.ReportService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.ReportServiceImpl;
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

public class ReportParticipantCardController {
    @FXML private LookupBox<Student> lookupBoxStudent;
    @FXML private ComboBox<String> comboExamType;
    @FXML private ComboBox<String> comboSemester;
    @FXML private TextField textFieldAcademicYear;
    @FXML private Button btnViewReport;
    @FXML private StackPane reportPane;

    private MasterService masterService;
    private ReportService reportService;

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
        reportService = new ReportServiceImpl();

        TableColumn<Student, String> colNis = new TableColumn<>("NIS");
        colNis.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNis()));

        TableColumn<Student, String> colName = new TableColumn<>("Nama");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));

        lookupBoxStudent.configure(
            () -> masterService.getAllStudents(), List.of(colNis, colName), Student::getName
        );

        comboExamType.setItems(FXCollections.observableArrayList("UTS", "UAS", "UKK", "PRAKTIK"));
        comboSemester.setItems(FXCollections.observableArrayList("Ganjil", "Genap"));

        btnViewReport.setOnAction(e -> onViewReport());
    }

    private void onViewReport() {
        Student selectedStudent = lookupBoxStudent.getSelectedItem();
        String examType = comboExamType.getValue();
        String semester = comboSemester.getValue();
        String academicYear = textFieldAcademicYear.getText();

        if (selectedStudent == null || examType == null || semester == null || academicYear == null || academicYear.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Siswa, Jenis Ujian, Semester, dan Tahun Akademik wajib diisi!", ButtonType.OK).showAndWait();
            return;
        }

        try {
            JasperReport jasperReport = (JasperReport) JRLoader.loadObject(
                getClass().getResourceAsStream("/reports/ReportParticipantCard.jasper")
            );

            List<ParticipantCardReportItem> data = reportService.getParticipantCardReport(
                selectedStudent.getId(), examType, semester, academicYear
            );

            if (data.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "Siswa ini tidak terdaftar di sesi ujian manapun pada periode ini.", ButtonType.OK).showAndWait();
                return;
            }

            // ambil detail kelas lengkap (dengan Major), karena getAllStudents belum JOIN sampai Jurusan
            Student studentDetail = masterService.getByIdStudent(selectedStudent.getId());
            Classroom classroomDetail = studentDetail.getClassroom() != null
                ? masterService.getByIdClassroom(studentDetail.getClassroom().getId()) : null;

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("STUDENT_NAME", studentDetail.getName());
            parameters.put("STUDENT_NIS", studentDetail.getNis());
            parameters.put("CLASSROOM_NAME", classroomDetail != null ? classroomDetail.getName() : "-");
            parameters.put("MAJOR_NAME", classroomDetail != null && classroomDetail.getMajor() != null
                ? classroomDetail.getMajor().getName() : "-");
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