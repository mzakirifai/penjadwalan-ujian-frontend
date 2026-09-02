package co.id.controller.pages;

import co.id.auth.AuthContext;
import co.id.model.User;
import co.id.service.UserService;
import co.id.service.impl.UserServiceImpl;
import java.io.IOException;
import java.util.Optional;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Pagination;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class UserManagementController {
    @FXML private TextField searchField;
    @FXML private Button filterBtn;
    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private TableView<User> tableView;
    
    @FXML private TableColumn<User, String> tableColumnUsername, tableColumnName, tableColumnRole; 
    @FXML private TableColumn<User, Void> tableColumnAction;  
    
    @FXML private Pagination pagination;
    
    private UserService userService;
    private ObservableList<User> observableList;
    
    private ImageView createIcon(String path) {
        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));
        imageView.setFitWidth(16);
        imageView.setFitHeight(16);
        return imageView;
    }
    
    @FXML
    public void initialize(){
        userService = new UserServiceImpl();
        observableList = FXCollections.observableArrayList();
        
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        tableColumnUsername.setCellValueFactory(u -> new SimpleStringProperty(u.getValue().getUsername()));
        tableColumnName.setCellValueFactory(u -> new SimpleStringProperty(resolveDisplayName(u.getValue())));
        tableColumnRole.setCellValueFactory(u -> new SimpleStringProperty(
                "admin".equalsIgnoreCase(u.getValue().getRole()) ? "Admin" : "Guru"));
        
        tableColumnAction.setCellFactory(clbck -> new TableCell<>(){
            private final Button buttonEdit = new Button("Edit");
            private final Button buttonResetPassword = new Button("Reset Password");
            private final Button buttonDelete = new Button("Hapus");
            private final HBox box = new HBox(5, buttonEdit, buttonResetPassword, buttonDelete);
            {
                buttonEdit.setGraphic(createIcon("/icons/edit.png"));
                buttonDelete.setGraphic(createIcon("/icons/trash.png"));
                
                buttonEdit.getStyleClass().add("btn-edit");
                buttonResetPassword.getStyleClass().add("btn-duplicate");
                buttonDelete.getStyleClass().add("btn-delete");
                
                buttonEdit.setOnAction(eh -> {
                    User user = getTableView().getItems().get(getIndex());
                    openForm(user);
                });
                
                buttonResetPassword.setOnAction(eh -> {
                    User user = getTableView().getItems().get(getIndex());
                    openResetPasswordDialog(user);
                });

                buttonDelete.setOnAction(eh -> {
                    User user = getTableView().getItems().get(getIndex());

                    User currentUser = AuthContext.getCurrentUser();
                    if (currentUser != null && currentUser.getId() == user.getId()) {
                        new Alert(AlertType.WARNING, "Anda tidak bisa menghapus akun Anda sendiri yang sedang login").showAndWait();
                        return;
                    }

                    if ("admin".equalsIgnoreCase(user.getRole())) {
                        long totalAdmin = userService.getAllUsers().stream()
                                .filter(u -> "admin".equalsIgnoreCase(u.getRole()))
                                .count();

                        if (totalAdmin <= 1) {
                            new Alert(AlertType.WARNING, "Tidak bisa menghapus Admin terakhir. Sistem harus punya minimal 1 Admin.").showAndWait();
                            return;
                        }
                    }

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Yakin ingin menghapus akun \"" + user.getUsername() + "\"?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Konfirmasi Hapus");
                    confirm.setHeaderText(null);

                    confirm.showAndWait().ifPresent(action -> {
                        if (action == ButtonType.YES) {
                            try {
                                userService.delete(user.getId());
                                refreshTable();
                            } catch (Exception ex) {
                                new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
                            }
                        }
                    });
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty){
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        
        int totalRows = userService.countUsers();
        int rowsPerPage = 10;
        int pageCount = (int)Math.ceil((double) totalRows / rowsPerPage);
        
        pagination.setPageCount(pageCount);
        pagination.setPageFactory(clbck -> {
            loadPage(clbck, rowsPerPage);
            return new VBox(tableView);
        });
        
        filterBtn.setOnAction(eh -> filterItems());
        addBtn.setOnAction(eh -> openForm(null));
        refreshBtn.setOnAction(e -> refreshTable());
    }   
    
    private String resolveDisplayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getTeacher() != null && user.getTeacher().getName() != null) {
            return user.getTeacher().getName();
        }
        return "-";
    }
    
    private void filterItems() {
        String keyword = searchField.getText();

        if (keyword == null || keyword.isEmpty()) {
            refreshTable();
        } else {
            observableList.setAll(userService.getByKeyword(keyword));
            tableView.setItems(observableList);
        }
    }
    
    private void openForm(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/UserManagementForm.fxml"));
            Parent formRoot = loader.load();

            UserManagementFormController formController = loader.getController();

            if (user != null) {
                formController.setUser(user);
            }

            formController.setOnSaveCallback(this::refreshTable);

            Region mainRoot = (Region) addBtn.getScene().getRoot();
            mainRoot.setEffect(new GaussianBlur(10));

            Stage dialog = new Stage();
            dialog.setTitle(user == null ? "Tambah User" : "Edit User");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UTILITY);

            Scene scene = new Scene(formRoot, 420, 420);
            scene.getStylesheets().add(getClass().getResource("/css/material.css").toExternalForm());

            dialog.setScene(scene);
            dialog.centerOnScreen();

            dialog.setOnHidden(eh -> mainRoot.setEffect(null));

            dialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void openResetPasswordDialog(User user) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Reset password untuk \"" + user.getUsername() + "\"");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password baru (min. 6 karakter)");

        VBox content = new VBox(10, passwordField);
        content.setStyle("-fx-padding: 15;");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return passwordField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPassword -> {
            if (newPassword == null || newPassword.isBlank()) {
                new Alert(AlertType.WARNING, "Password baru tidak boleh kosong").showAndWait();
                return;
            }

            try {
                userService.resetPassword(user.getId(), newPassword, AuthContext.getCurrentUsername());
                new Alert(AlertType.INFORMATION, "Password berhasil direset").showAndWait();
            } catch (Exception ex) {
                new Alert(AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });
    }
    
    private void loadPage(int pageIndex, int rowsPerPage) {
        observableList.setAll(userService.getUsers(pageIndex + 1, rowsPerPage));
        tableView.setItems(observableList);
    }
    
    private void refreshTable() {
        int totalRows = userService.countUsers();
        int rowsPerPage = 10;
        int pageCount = (int) Math.ceil((double) totalRows / rowsPerPage);
        pagination.setPageCount(Math.max(pageCount, 1));
        loadPage(pagination.getCurrentPageIndex(), rowsPerPage);
    }
}
