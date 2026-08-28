package co.id.component;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

// Generic EditableCell<T>
public class EditableCell<T> extends TableCell<T, String> {
    private final TextField textField = new TextField();
    private final Function<T, String> function;
    private final Supplier<Integer> editingRowIndexSupplier;

    public EditableCell(Function<T, String> function,
        BiConsumer<T, String> biConsumer,
        Supplier<Integer> editingRowIndexSupplier) {
        this.function = function;
        this.editingRowIndexSupplier = editingRowIndexSupplier;

        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                T item = getTableView().getItems().get(getIndex());
                biConsumer.accept(item, newVal);
            }
        });
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setGraphic(null);
            setText(null);
        } else {
            T rowItem = getTableView().getItems().get(getIndex());
            if (getIndex() == editingRowIndexSupplier.get()) {
                textField.setText(function.apply(rowItem));
                setGraphic(textField);
                setText(null);
            } else {
                setText(function.apply(rowItem));
                setGraphic(null);
            }
        }
    }
}