package co.id.component;

import co.id.auth.AuthContext;
import co.id.importer.ExcelGridReader;
import co.id.importer.ExcelTemplateWriter;
import co.id.importer.ImportItem;
import co.id.importer.TableParser;
import co.id.importer.ScoreImportParser;
import co.id.importer.TeacherImportParser;
import co.id.model.ExamParticipant;
import co.id.model.ExamSchedule;
import co.id.model.ExamScore;
import co.id.model.Teacher;
import co.id.service.ExamParticipantService;
import co.id.service.ExamScoreService;
import co.id.service.MasterService;
import co.id.service.impl.ExamParticipantServiceImpl;
import co.id.service.impl.ExamScoreServiceImpl;
import co.id.service.impl.MasterServiceImpl;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.control.Alert;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Aksi tombol "Import Excel" dan "Template" untuk halaman Guru dan Mapel.
 * Controller cukup memanggil method di sini.
 */
public class ExcelImportActions {

    // ------------------------------------------------------------------ Guru

    public static void importTeachers(Region mainRoot, Window owner, Runnable onImported) {
        File file = chooseExcel(owner, "Pilih file Excel data guru");
        if (file == null) {
            return;
        }

        try {
            List<TableParser.Row> rows = readRows(file, TeacherImportParser.ALIASES, TeacherImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                noData("NIP dan Nama");
                return;
            }

            MasterService service = new MasterServiceImpl();
            List<ImportItem<Teacher>> items = TeacherImportParser.parse(rows, service.getAllTeachers());

            ImportPreviewDialog.show(mainRoot, "Import Guru dari Excel", file.getName(),
                    TeacherImportParser.COLUMNS, items,
                    list -> service.importTeachers(list, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole()),
                    onImported);
        } catch (Exception ex) {
            readError(ex);
        }
    }

    public static void saveTeacherTemplate(Window owner) {
        List<String> notes = List.of(
                "PETUNJUK PENGISIAN TEMPLATE GURU",
                "Isi data di sheet pertama, mulai dari baris 2. Jangan mengubah judul kolom di baris 1.",
                "Kolom wajib: NIP, Nama, Jenis Kelamin. Kolom lain boleh kosong.",
                "Jenis Kelamin diisi L atau P (boleh juga Laki-laki / Perempuan).",
                "NIP tidak boleh sama dengan NIP guru yang sudah ada di aplikasi.",
                "Kolom sudah berformat Teks supaya NIP yang panjang dan nol di depan No. HP tidak berubah.",
                "Jika menyalin data dari file lain, tempel sebagai teks (Paste Special > Values), jangan sebagai angka.",
                "Contoh baris: 199001012020011001 | Nama Guru Contoh, S.Pd. | P | 081234567890 | contoh@email.com | Jl. Contoh No. 1");

        saveTemplate(owner, "template-import-guru.xlsx", "Guru", TeacherImportParser.COLUMNS, notes);
    }

    // ------------------------------------------------------------------ Nilai ujian

    public static void importScores(Region mainRoot, Window owner, ExamSchedule schedule, Runnable onImported) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih file nilai (Excel atau CSV)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Excel / CSV (*.xlsx, *.xls, *.csv)", "*.xlsx", "*.xls", "*.csv", "*.txt"));
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }

        try {
            List<TableParser.Row> rows = readRows(file, ScoreImportParser.ALIASES, ScoreImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                noData("Nilai");
                return;
            }

            ExamParticipantService participantService = new ExamParticipantServiceImpl();
            ExamScoreService scoreService = new ExamScoreServiceImpl();
            List<ExamParticipant> participants = participantService.getByExamSchedule(schedule.getId());
            List<ExamScore> existing = scoreService.getByExamSchedule(schedule.getId());

            List<ImportItem<ExamScore>> items = ScoreImportParser.parse(rows, schedule, participants, existing);

            Integer teacherId = AuthContext.getCurrentUser().getTeacher() != null
                    ? AuthContext.getCurrentUser().getTeacher().getId() : null;

            ImportPreviewDialog.show(mainRoot, "Import Nilai: " + scheduleTitle(schedule), file.getName(),
                    ScoreImportParser.COLUMNS, items,
                    list -> scoreService.importScores(list, AuthContext.getCurrentUsername(),
                            AuthContext.getCurrentRole(), teacherId),
                    onImported);
        } catch (Exception ex) {
            readError(ex);
        }
    }

    /** Template berisi daftar peserta ujian (NIS + Nama) dan nilai yang sudah ada, tinggal mengisi kolom Nilai. */
    public static void saveScoreTemplate(Window owner, ExamSchedule schedule) {
        List<ExamParticipant> participants = new ExamParticipantServiceImpl().getByExamSchedule(schedule.getId());
        Map<Integer, Integer> existing = new java.util.HashMap<>();
        for (ExamScore es : new ExamScoreServiceImpl().getByExamSchedule(schedule.getId())) {
            if (es.getStudent() != null) {
                existing.put(es.getStudent().getId(), es.getScore());
            }
        }

        List<List<String>> rows = new ArrayList<>();
        participants.stream()
                .sorted(java.util.Comparator.comparing(p -> p.getStudent().getName() == null ? "" : p.getStudent().getName()))
                .forEach(p -> rows.add(List.of(
                        p.getStudent().getNis() == null ? "" : p.getStudent().getNis(),
                        p.getStudent().getName() == null ? "" : p.getStudent().getName(),
                        existing.containsKey(p.getStudent().getId()) ? String.valueOf(existing.get(p.getStudent().getId())) : "")));

        List<String> notes = List.of(
                "PETUNJUK PENGISIAN TEMPLATE NILAI",
                "Template ini khusus untuk ujian: " + scheduleTitle(schedule),
                "Isi kolom Nilai di sheet pertama. Jangan mengubah judul kolom di baris 1.",
                "Siswa dikenali dari kolom NIS. Kolom Nama hanya pembanding, boleh dihapus.",
                "File hasil scanner (CSV atau Excel) juga bisa langsung diimpor selama ada kolom NIS (atau NISN / No. Peserta) dan Nilai.",
                "Nilai berupa angka 0 sampai 100. Nilai desimal (85,5) dibulatkan ke bilangan bulat.",
                "Siswa yang bukan peserta ujian ini tidak akan diimpor. Nilai yang sudah ada akan ditimpa nilai baru.",
                "Kolom sudah berformat Teks supaya NIS tidak berubah bentuk.");

        saveTemplate(owner, "template-nilai-" + (schedule.getCode() == null ? "ujian" : schedule.getCode()) + ".xlsx",
                "Nilai", List.of("NIS", "Nama", "Nilai"), notes, rows);
    }

    private static String scheduleTitle(ExamSchedule schedule) {
        return schedule.getCode() + " - " + schedule.getSubjectLabel()
                + (schedule.getClassroom() != null ? " - " + schedule.getClassroom().getName() : "");
    }

    // ------------------------------------------------------------------ helper

    private static File chooseExcel(Window owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("File Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"));
        return chooser.showOpenDialog(owner);
    }

    /** Membaca semua sheet dan menggabungkan baris data dari sheet yang punya header yang dikenali. */
    private static List<TableParser.Row> readRows(File file, Map<String, List<String>> aliases, List<String> required,
                                                  Set<String> contextFields) throws Exception {
        List<TableParser.Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<List<String>>> sheet : ExcelGridReader.readAll(file).entrySet()) {
            rows.addAll(TableParser.parse(sheet.getKey(), sheet.getValue(), aliases, required, contextFields));
        }
        return rows;
    }

    private static void saveTemplate(Window owner, String defaultName, String sheetName,
                                     List<String> headers, List<String> notes) {
        saveTemplate(owner, defaultName, sheetName, headers, notes, List.of());
    }

    private static void saveTemplate(Window owner, String defaultName, String sheetName,
                                     List<String> headers, List<String> notes, List<List<String>> rows) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Simpan template Excel");
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("File Excel (*.xlsx)", "*.xlsx"));

        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }

        try {
            ExcelTemplateWriter.write(file, sheetName, headers, notes, rows);
            new Alert(Alert.AlertType.INFORMATION, "Template tersimpan di:\n" + file.getAbsolutePath()).showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Gagal menyimpan template: " + ex.getMessage()
                    + "\nJika file sedang dibuka di Excel, tutup dulu lalu coba lagi.").showAndWait();
        }
    }

    private static void noData(String requiredHeaders) {
        new Alert(Alert.AlertType.WARNING,
                "Tidak ada data yang terbaca.\n"
                + "Pastikan ada baris judul kolom yang memuat: " + requiredHeaders + ".\n"
                + "Anda bisa memakai tombol Template untuk mendapatkan format yang benar.").showAndWait();
    }

    private static void readError(Exception ex) {
        new Alert(Alert.AlertType.ERROR, "Gagal membaca file Excel: " + ex.getMessage()
                + "\nJika file sedang dibuka di Excel, tutup dulu lalu coba lagi.").showAndWait();
    }
}