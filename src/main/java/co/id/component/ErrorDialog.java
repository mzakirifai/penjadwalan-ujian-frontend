package co.id.component;

import co.id.config.AppLog;
import javafx.application.Platform;
import javafx.scene.control.Alert;

/**
 * Pesan error untuk pengguna: dicatat ke log (AppLog) lalu ditampilkan singkat,
 * dengan petunjuk letak file log bila perlu dilaporkan ke admin.
 */
public final class ErrorDialog {

    private ErrorDialog() {
    }

    /**
     * @param where   lokasi kejadian untuk log, mis. "StudentController.openForm"
     * @param message kalimat untuk pengguna, mis. "Form siswa tidak bisa dibuka."
     */
    public static void show(String where, String message, Throwable error) {
        AppLog.error(where, error);
        Runnable show = () -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Terjadi Kesalahan");
            alert.setHeaderText(message);
            String detail = error == null || error.getMessage() == null ? error == null ? "" : error.getClass().getSimpleName() : error.getMessage();
            alert.setContentText((detail.isBlank() ? "" : "Detail: " + detail + "\n\n")
                    + "Catatan lengkap tersimpan di:\n" + AppLog.logFolder());
            alert.showAndWait();
        };
        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
    }
}