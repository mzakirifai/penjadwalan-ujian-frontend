package co.id.controller.layout;

import java.net.URL;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

public class MainLayoutController {
    @FXML private StackPane contentArea;
    @FXML public void initialize(){
        // Daftarkan ke registry
        ControllerRegistry.setMainLayoutController(this);
        
        // Tampilkan Dashboard secara otomatis saat aplikasi pertama kali dibuka
        setContent("/pages/Dashboard.fxml");
    }
    
    public void setContent(String fxmlPath){
        try {
            URL resource = getClass().getResource(fxmlPath);
            
            if (resource == null) {
                throw new IllegalArgumentException("FXML not found: " + fxmlPath);
            }
            
            Node node = FXMLLoader.load(resource);
            contentArea.getChildren().setAll(node);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
