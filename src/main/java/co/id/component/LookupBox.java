package co.id.component;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

public class LookupBox<T> extends HBox{
    private final TextField textField = new TextField();
    private final Button searchBtn = new Button("🔍");
    private final Popup popup = new Popup();
    private final TableView<T> tableView = new TableView<>();
    private final ObjectProperty<T> selectedItemProperty = new SimpleObjectProperty<>();
    private Supplier<List<T>> dataSupplier;
    private Function<T, String> displayFormatter = Object::toString;
    private String promptText;

    public LookupBox() {
        super(0);
        
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        
        this.getChildren().addAll(textField, searchBtn);
        
        tableView.setPrefSize(300, 200);
        VBox box = new VBox(tableView);
        box.setStyle("-fx-background-color: white; -fx-border-color: gray;");
        popup.getContent().add(box);
        
        searchBtn.setOnAction(eh -> {
            if (dataSupplier == null) {
                return;
            }
            
            if (!popup.isShowing()) {
                List<T> items = dataSupplier.get();
                tableView.setItems(FXCollections.observableArrayList(items));
                
                double x = this.localToScreen(this.getBoundsInLocal()).getMinX();
                double y = this.localToScreen(this.getBoundsInLocal()).getMaxY();
                
                popup.show(this,x,y);
            }else{
                popup.hide();
            }
        });
        
        tableView.setOnMouseClicked(eh -> {
            T item = tableView.getSelectionModel().getSelectedItem();
            if (item != null) {
                setSelectedItem(item);
                popup.hide();
            }
        });
    }
    
    public void configure(Supplier<List<T>> dataSupplier,
        List<TableColumn<T, ?>> columns, Function<T, String> displayFormatter){
        this.dataSupplier = dataSupplier;
        this.displayFormatter = displayFormatter;
        tableView.getColumns().setAll(columns);
    }

    public String getText() {
        return textField.getText();
    }

    public void setPromptText(String text){
        this.promptText = text;
        textField.setPromptText(text);
    }
    
    public void setFieldWidth(double width) {
        textField.setPrefWidth(width);
    }
    
    public String getPromptText(){
        return promptText;
    }

    public ObjectProperty<T> selectedItemProperty(){
        return selectedItemProperty;
    }

    public T getSelectedItem(){
        T val = selectedItemProperty.get();
        return val;
    }

    public void setSelectedItem(T item){
        selectedItemProperty.set(item);
        if(item != null){
            textField.setText(displayFormatter.apply(item));
        }else{
            textField.clear();
            textField.setPromptText(promptText);
        }
    }
    
    
    
}