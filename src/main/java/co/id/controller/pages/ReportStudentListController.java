package co.id.controller.pages;

import co.id.component.LookupBox;
import co.id.model.Classroom;
import co.id.model.Student;
import co.id.service.MasterService;
import co.id.service.ReportService;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.ReportServiceImpl;
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

public class ReportStudentListController {
    @FXML private LookupBox<Classroom> lookupBoxClassroom;
    @FXML private Button btnViewReport;
    @FXML private StackPane reportPane;

    private MasterService masterService;
    private ReportService reportService;

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
        reportService = new ReportServiceImpl();

        TableColumn<Classroom, String> colClassroom = new TableColumn<>("Kelas");
        colClassroom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));

        lookupBoxClassroom.configure(
            () -> masterService.getAllClassrooms(), List.of(colClassroom), Classroom::getName
        );

        btnViewReport.setOnAction(e -> onViewReport());
    }

    private void onViewReport() {
        Classroom selectedClassroom = lookupBoxClassroom.getSelectedItem();

        if (selectedClassroom == null) {
            new Alert(Alert.AlertType.WARNING, "Pilih kelas terlebih dahulu!", ButtonType.OK).showAndWait();
            return;
        }

        try {
            JasperReport jasperReport = (JasperReport) JRLoader.loadObject(
                getClass().getResourceAsStream("/reports/ReportStudentList.jasper")
            );

            // ambil detail kelas yang lengkap (dengan Major), karena hasil LookupBox
            // (getAllClassrooms) belum JOIN sampai Jurusan
            Classroom classroomDetail = masterService.getByIdClassroom(selectedClassroom.getId());

            List<Student> data = reportService.getStudentListReport(selectedClassroom.getId());

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("CLASSROOM_NAME", classroomDetail.getName());
            parameters.put("MAJOR_NAME", classroomDetail.getMajor() != null ? classroomDetail.getMajor().getName() : "-");

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