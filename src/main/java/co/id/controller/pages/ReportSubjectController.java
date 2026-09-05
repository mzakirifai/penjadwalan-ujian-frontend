package co.id.controller.pages;

import co.id.component.LookupBox;
import co.id.model.Major;
import co.id.model.Subject;
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

public class ReportSubjectController {
    @FXML private LookupBox<Major> lookupBoxMajor;
    @FXML private Button btnViewReport;
    @FXML private StackPane reportPane;

    private MasterService masterService;
    private ReportService reportService;

    @FXML
    public void initialize() {
        masterService = new MasterServiceImpl();
        reportService = new ReportServiceImpl();
        
        // Konfigurasi Lookup dengan data Employee
        TableColumn<Major, String> colMajor = new TableColumn<>("Major");
        colMajor.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        
        lookupBoxMajor.configure(
            () -> masterService.getAllMajors(), List.of(colMajor), Major::getName
        );
        
        btnViewReport.setOnAction(e -> onViewReport());
    }

    
    private void onViewReport() {
        Major selectedMajor = lookupBoxMajor.getSelectedItem();

        if (selectedMajor == null) {
            new Alert(Alert.AlertType.WARNING, "Pilih jurusan terlebih dahulu!", ButtonType.OK).showAndWait();
            return;
        }

        try {
            JasperReport jasperReport = (JasperReport) JRLoader.loadObject(
                getClass().getResourceAsStream("/reports/ReportSubject.jasper")
            );

            List<Subject> data = reportService.getSubjectReport(selectedMajor.getId());

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("MAJOR_NAME", selectedMajor.getName());

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