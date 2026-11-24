package co.id.component;

import co.id.config.AppLog;
import co.id.model.SchoolSettings;
import co.id.service.SchoolSettingsService;
import co.id.service.impl.SchoolSettingsServiceImpl;
import java.util.Map;

/**
 * Parameter laporan yang sama untuk semua laporan: data kepala sekolah diambil dari menu Settings,
 * bukan ditulis langsung di file .jrxml, sehingga laporan ikut berubah bila kepala sekolah berganti.
 */
public final class ReportParameters {

    private static final SchoolSettingsService SETTINGS = new SchoolSettingsServiceImpl();

    private ReportParameters() {
    }

    /** Tambahkan PRINCIPAL_NAME dan PRINCIPAL_NIP ke parameter laporan, lalu kembalikan map yang sama. */
    public static Map<String, Object> withSchool(Map<String, Object> parameters) {
        try {
            SchoolSettings settings = SETTINGS.getSettings();
            if (settings != null) {
                parameters.put("PRINCIPAL_NAME", settings.getPrincipalName());
                parameters.put("PRINCIPAL_NIP", settings.getPrincipalNip());
            }
        } catch (Exception exception) {
            // Pengaturan gagal dibaca: laporan tetap dicetak, tanda tangan kepala sekolah dikosongkan
            AppLog.warn("ReportParameters.withSchool", exception);
        }
        return parameters;
    }
}