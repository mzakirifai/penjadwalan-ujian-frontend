package co.id.controller.layout;

import co.id.auth.AuthContext;
import java.util.List;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class SidebarController {
    @FXML private BorderPane sidebar;
    @FXML private Label brandLabel;
    
    @FXML private Button dashboardBtn;
    @FXML private Button masterMenu;
    @FXML private VBox masterMenuBox;
    @FXML private Button transactionMenu;
    @FXML private VBox transactionMenuBox;
    @FXML private Button reportMenu;
    @FXML private VBox reportMenuBox;
    
    @FXML private Button userManagementBtn;
    @FXML private Button settingsBtn;
    @FXML private Button helpBtn;
    @FXML private Button aboutBtn;
    
    private List<Button> topLevelButtons;
    
    private boolean masterExpanded = false;
    private boolean transactionExpanded = false;
    private boolean reportExpanded = false;
    private boolean collapsed = false;
    
    @FXML
    public void initialize() {
        ControllerRegistry.setSidebarController(this);

        topLevelButtons = List.of(
                dashboardBtn, masterMenu, transactionMenu, reportMenu,
                userManagementBtn, settingsBtn, helpBtn, aboutBtn
        );
        
        for (Button btn : topLevelButtons) {
            btn.setUserData(btn.getText());
        }

        masterExpanded = false;
        masterMenuBox.setVisible(false);
        masterMenuBox.setManaged(false);
        masterMenu.setText("Master ▸");

        transactionExpanded = false;
        transactionMenuBox.setVisible(false);
        transactionMenuBox.setManaged(false);
        transactionMenu.setText("Transaksi ▸");

        reportExpanded = false;
        reportMenuBox.setVisible(false);
        reportMenuBox.setManaged(false);
        reportMenu.setText("Laporan ▸");

        applyRoleBasedAccess();
    }
    
    public void toggleSidebar() {
        if (collapsed) {
            sidebar.setPrefWidth(240);
            sidebar.setMinWidth(240);
            sidebar.setMaxWidth(240);

            collapsed = false;
            brandLabel.setText("Penjadwalan Ujian");

            for (Button btn : topLevelButtons) {
                btn.getStyleClass().remove("menu-parent-collapsed");
                btn.getStyleClass().add("menu-parent");

                if (btn == masterMenu) {
                    btn.setText(masterExpanded ? "Master ▿" : "Master ▸");
                } else if (btn == transactionMenu) {
                    btn.setText(transactionExpanded ? "Transaksi ▿" : "Transaksi ▸");
                } else if (btn == reportMenu) {
                    btn.setText(reportExpanded ? "Laporan ▿" : "Laporan ▸");
                } else {
                    Object originalText = btn.getUserData();
                    if (originalText instanceof String text) {
                        btn.setText(text);
                    }
                }
            }
        } else {
            masterExpanded = false;
            masterMenuBox.setVisible(false);
            masterMenuBox.setManaged(false);
            masterMenu.setText("Master ▸");

            transactionExpanded = false;
            transactionMenuBox.setVisible(false);
            transactionMenuBox.setManaged(false);
            transactionMenu.setText("Transaksi ▸");

            reportExpanded = false;
            reportMenuBox.setVisible(false);
            reportMenuBox.setManaged(false);
            reportMenu.setText("Laporan ▸");

            sidebar.setPrefWidth(60);
            sidebar.setMinWidth(60);
            sidebar.setMaxWidth(60);

            collapsed = true;
            brandLabel.setText("");

            for (Button btn : topLevelButtons) {
                btn.setText("");
                btn.getStyleClass().remove("menu-parent");
                btn.getStyleClass().add("menu-parent-collapsed");
            }
        }
    }
    
    @FXML
    private void toggleMasterMenu() {
        if (collapsed) {
            showFlyoutMenu(masterMenu,
                    new String[]{"Guru", "Jurusan", "Kelas", "Siswa", "Mapel", "Ruangan"},
                    new Runnable[]{
                            this::handleTeacherClick,
                            this::handleMajorClick,
                            this::handleClassroomClick,
                            this::handleStudentClick,
                            this::handleSubjectClick,
                            this::handleRoomClick
                    });
            return;
        }
        masterExpanded = !masterExpanded;
        masterMenuBox.setVisible(masterExpanded);
        masterMenuBox.setManaged(masterExpanded);
        masterMenu.setText(masterExpanded ? "Master ▿" : "Master ▸");
    }
    
    @FXML
    private void toggleTransactionMenu() {
        if (collapsed) {
            showFlyoutMenu(transactionMenu,
                    new String[]{"Jadwal Ujian", "Peserta Ujian", "Nilai Ujian"},
                    new Runnable[]{
                            this::handleExamScheduleClick,
                            this::handleExamParticipantClick,
                            this::handleExamScoreClick
                    });
            return;
        }
        transactionExpanded = !transactionExpanded;
        transactionMenuBox.setVisible(transactionExpanded);
        transactionMenuBox.setManaged(transactionExpanded);
        transactionMenu.setText(transactionExpanded ? "Transaksi ▿" : "Transaksi ▸");
    }
    
    @FXML
    private void toggleReportMenu() {
        if (collapsed) {
            showFlyoutMenu(reportMenu,
                    new String[]{"Data Jurusan", "Data Kelas", "Data Ruangan", "Mapel per Jurusan",
                            "Daftar Siswa", "Data Guru", "Jadwal Ujian", "Kartu Peserta",
                            "Hasil Ujian", "Rekap Nilai Kelas", "Daftar Hadir Peserta", "Jadwal Mengawas Guru"},
                    new Runnable[]{
                            this::handleReportMajorClick,
                            this::handleReportClassroomClick,
                            this::handleReportRoomClick,
                            this::handleReportSubjectClick,
                            this::handleReportStudentListClick,
                            this::handleReportTeacherClick,
                            this::handleReportExamScheduleClick,
                            this::handleReportParticipantCardClick,
                            this::handleReportExamResultClick,
                            this::handleReportClassScoreClick,
                            this::handleReportAttendanceListClick,
                            this::handleReportTeacherScheduleClick
                    });
            return;
        }
        reportExpanded = !reportExpanded;
        reportMenuBox.setVisible(reportExpanded);
        reportMenuBox.setManaged(reportExpanded);
        reportMenu.setText(reportExpanded ? "Laporan ▿" : "Laporan ▸");
    }
    
    @FXML private void handleDashboardClick() { loadPage("/pages/Dashboard.fxml"); }

    @FXML private void handleTeacherClick() { loadPage("/pages/Teacher.fxml"); }
    @FXML private void handleMajorClick() { loadPage("/pages/Major.fxml"); }
    @FXML private void handleClassroomClick() { loadPage("/pages/Classroom.fxml"); }
    @FXML private void handleStudentClick() { loadPage("/pages/Student.fxml"); }
    @FXML private void handleSubjectClick() { loadPage("/pages/Subject.fxml"); }
    @FXML private void handleRoomClick() { loadPage("/pages/Room.fxml"); }
    
    @FXML private void handleExamScheduleClick() { loadPage("/pages/ExamSchedule.fxml"); }
    @FXML private void handleExamParticipantClick() { loadPage("/pages/ExamParticipant.fxml"); }
    @FXML private void handleExamScoreClick() { loadPage("/pages/ExamScore.fxml"); }

    @FXML private void handleReportMajorClick() { loadPage("/pages/ReportMajor.fxml"); }
    @FXML private void handleReportClassroomClick() { loadPage("/pages/ReportClassroom.fxml"); }
    @FXML private void handleReportRoomClick() { loadPage("/pages/ReportRoom.fxml"); }
    @FXML private void handleReportSubjectClick() { loadPage("/pages/ReportSubject.fxml"); }
    @FXML private void handleReportStudentListClick() { loadPage("/pages/ReportStudentList.fxml"); }
    @FXML private void handleReportTeacherClick() { loadPage("/pages/ReportTeacher.fxml"); }
    @FXML private void handleReportExamScheduleClick() { loadPage("/pages/ReportExamSchedule.fxml"); }
    @FXML private void handleReportParticipantCardClick() { loadPage("/pages/ReportParticipantCard.fxml"); }
    @FXML private void handleReportExamResultClick() { loadPage("/pages/ReportExamResult.fxml"); }
    @FXML private void handleReportClassScoreClick() { loadPage("/pages/ReportClassScore.fxml"); }
    @FXML private void handleReportAttendanceListClick() { loadPage("/pages/ReportAttendanceList.fxml"); }
    @FXML private void handleReportTeacherScheduleClick() { loadPage("/pages/ReportTeacherSchedule.fxml"); }
    
    @FXML private void handleUserManagementClick() { loadPage("/pages/UserManagement.fxml"); }
    @FXML private void handleSettingsClick() { loadPage("/pages/Settings.fxml"); }
    @FXML private void handleHelpClick() { loadPage("/pages/Help.fxml"); }
    @FXML private void handleAboutClick() { loadPage("/pages/About.fxml"); }
    
    private void loadPage(String fxmlPath) {
        MainLayoutController main = ControllerRegistry.getMainLayoutController();
        if (main != null) {
            main.setContent(fxmlPath);
        }
    }
    
    private void showFlyoutMenu(Button anchor, String[] labels, Runnable[] actions) {
        ContextMenu flyout = new ContextMenu();

        for (int i = 0; i < labels.length; i++) {
            MenuItem item = new MenuItem(labels[i]);
            Runnable action = actions[i];
            item.setOnAction(eh -> action.run());
            flyout.getItems().add(item);
        }

        flyout.show(anchor, Side.RIGHT, 0, 0);
    }
    
    private void applyRoleBasedAccess() {
        if (AuthContext.isAdmin()) {
            return;
        }

        // GURU: sembunyikan menu Master dan User Management.
        // Menu Transaksi tetap terlihat (Jadwal & Peserta view-only, Nilai bisa diinput,
        // pembatasan detail dilakukan di masing-masing Controller/Service).
        hideNode(masterMenu);
        hideNode(masterMenuBox);
        hideNode(userManagementBtn);
    }
    
    private void hideNode(javafx.scene.Node node) {
        if (node != null) {
            node.setVisible(false);
            node.setManaged(false);
        }
    }
}