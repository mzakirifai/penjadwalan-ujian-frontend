package co.id.component;

import co.id.auth.AuthContext;
import co.id.importer.ClassroomImportParser;
import co.id.importer.CsvTemplateWriter;
import co.id.importer.ExcelGridReader;
import co.id.importer.RoomImportParser;
import co.id.importer.ExcelTemplateWriter;
import co.id.importer.ImportItem;
import co.id.importer.TableParser;
import co.id.importer.AdminStatusImportParser;
import co.id.importer.ExamScheduleImportParser;
import co.id.importer.ScoreImportParser;
import co.id.importer.SubjectImportParser;
import co.id.importer.TeachingAssignmentImportParser;
import co.id.importer.TeacherImportParser;
import co.id.model.Classroom;
import co.id.model.ExamParticipant;
import co.id.model.ExamSchedule;
import co.id.model.ExamScore;
import co.id.model.Student;
import co.id.model.Room;
import co.id.model.Subject;
import co.id.model.Teacher;
import co.id.model.TeachingAssignment;
import co.id.service.ExamParticipantService;
import co.id.service.ExamScheduleService;
import co.id.service.ExamScoreService;
import co.id.service.MasterService;
import co.id.service.TeachingAssignmentService;
import co.id.service.impl.ExamParticipantServiceImpl;
import co.id.service.impl.ExamScheduleServiceImpl;
import co.id.service.impl.ExamScoreServiceImpl;
import co.id.service.impl.MasterServiceImpl;
import co.id.service.impl.TeachingAssignmentServiceImpl;
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
 * Aksi tombol "Impor Excel" dan "Template" untuk halaman Kelas, Ruangan, Guru, Mapel, Pengampu,
 * Jadwal Ujian, Status Administrasi, dan Nilai. Semua impor menerima file Excel (.xlsx/.xls) maupun CSV.
 * Controller cukup memanggil method di sini.
 */
public class ExcelImportActions {

    // ------------------------------------------------------------------ Kelas

    public static void importClassrooms(Region mainRoot, Window owner, Runnable onImported) {
        File file = chooseExcel(owner, "Pilih file Excel/CSV data kelas");
        if (file == null) {
            return;
        }

        try {
            // Cara 1: kolom Tingkat + Jurusan (+ Rombel). Cara 2: hanya kolom Nama Kelas (mis. "X TKJ 1").
            List<TableParser.Row> rows = readRows(file, ClassroomImportParser.ALIASES, ClassroomImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                rows = readRows(file, ClassroomImportParser.ALIASES, ClassroomImportParser.REQUIRED_BY_NAME, Set.of());
            }
            if (rows.isEmpty()) {
                noData("Tingkat dan Jurusan (atau Nama Kelas)");
                return;
            }

            MasterService service = new MasterServiceImpl();
            List<ImportItem<Classroom>> items = ClassroomImportParser.parse(rows, service.getAllClassrooms(), service.getAllMajors());

            ImportPreviewDialog.show(mainRoot, "Impor Kelas", file.getName(),
                    ClassroomImportParser.COLUMNS, items,
                    list -> service.importClassrooms(list, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole()),
                    onImported);
        } catch (Exception ex) {
            readError(ex);
        }
    }

    public static void saveClassroomTemplate(Window owner) {
        List<String> notes = List.of(
                "PETUNJUK PENGISIAN TEMPLATE KELAS",
                "Isi data Jurusan lebih dulu. Isi di sheet pertama mulai baris 2. Satu baris = satu kelas.",
                "Tingkat diisi X, XI, atau XII. Jurusan diisi singkatan (mis. TKJ) atau nama lengkap sesuai menu Jurusan.",
                "Rombel diisi satu angka/huruf (mis. 1, 2). Kosongkan bila jurusan itu hanya punya satu kelas di tingkat tsb.",
                "Nama kelas dibuat otomatis seperti di form Kelas: Tingkat + Jurusan + Rombel, mis. X TKJ 1. Kolom Nama Kelas boleh kosong.",
                "Atau cukup isi kolom Nama Kelas saja (mis. XI DKV 2); tingkat, jurusan, dan rombel dibaca dari namanya.",
                "Kode kelas dibuat otomatis. Kelas yang namanya sudah ada tidak diimpor ulang.",
                "Contoh baris: X | TKJ | 1 | (kosong)");

        saveTemplate(owner, "template-import-kelas.xlsx", "Kelas", ClassroomImportParser.COLUMNS, notes);
    }

    // ------------------------------------------------------------------ Ruangan

    public static void importRooms(Region mainRoot, Window owner, Runnable onImported) {
        File file = chooseExcel(owner, "Pilih file Excel/CSV data ruangan");
        if (file == null) {
            return;
        }

        try {
            List<TableParser.Row> rows = readRows(file, RoomImportParser.ALIASES, RoomImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                noData("Nama Ruangan dan Kapasitas");
                return;
            }

            MasterService service = new MasterServiceImpl();
            List<ImportItem<Room>> items = RoomImportParser.parse(rows, service.getAllRooms());

            ImportPreviewDialog.show(mainRoot, "Impor Ruangan", file.getName(),
                    RoomImportParser.COLUMNS, items,
                    list -> service.importRooms(list, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole()),
                    onImported);
        } catch (Exception ex) {
            readError(ex);
        }
    }

    public static void saveRoomTemplate(Window owner) {
        List<String> notes = List.of(
                "PETUNJUK PENGISIAN TEMPLATE RUANGAN",
                "Isi data di sheet pertama mulai baris 2. Satu baris = satu ruangan ujian.",
                "Kolom wajib: Nama Ruangan dan Kapasitas (jumlah kursi peserta, angka 1 - " + RoomImportParser.MAX_CAPACITY + ").",
                "Lantai boleh kosong (dianggap lantai 1).",
                "Kode ruangan dibuat otomatis. Ruangan yang namanya sudah ada (mis. \"R 1\" dan \"R1\" dianggap sama) tidak diimpor ulang.",
                "Contoh baris: R 1 | 1 | 36");

        saveTemplate(owner, "template-import-ruangan.xlsx", "Ruangan", RoomImportParser.COLUMNS, notes);
    }

    // ------------------------------------------------------------------ Guru

    public static void importTeachers(Region mainRoot, Window owner, Runnable onImported) {
        File file = chooseExcel(owner, "Pilih file Excel data guru");
        if (file == null) {
            return;
        }

        try {
            List<TableParser.Row> rows = readRows(file, TeacherImportParser.ALIASES, TeacherImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                noData("Nama dan Jenis Kelamin");
                return;
            }

            MasterService service = new MasterServiceImpl();
            List<ImportItem<Teacher>> items = TeacherImportParser.parse(rows, service.getAllTeachers());

            ImportPreviewDialog.show(mainRoot, "Impor Guru dari Excel", file.getName(),
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
                "Kolom wajib: Nama, Jenis Kelamin. Kolom lain (termasuk NIP) boleh kosong.",
                "Jenis Kelamin diisi L atau P (boleh juga Laki-laki / Perempuan).",
                "NIP boleh dikosongkan bagi guru yang belum punya NIP; jika diisi, tidak boleh sama dengan NIP guru lain.",
                "Kolom sudah berformat Teks supaya NIP yang panjang dan nol di depan No. HP tidak berubah.",
                "Jika menyalin data dari file lain, tempel sebagai teks (Paste Special > Values), jangan sebagai angka.",
                "Contoh baris: 199001012020011001 | Nama Guru Contoh, S.Pd. | P | 081234567890 | contoh@email.com | Jl. Contoh No. 1");

        saveTemplate(owner, "template-import-guru.xlsx", "Guru", TeacherImportParser.COLUMNS, notes);
    }

    // ------------------------------------------------------------------ Mapel

    public static void importSubjects(Region mainRoot, Window owner, Runnable onImported) {
        File file = chooseExcel(owner, "Pilih file Excel data mapel");
        if (file == null) {
            return;
        }

        try {
            List<TableParser.Row> rows = readRows(file, SubjectImportParser.ALIASES, SubjectImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                noData("Nama Mapel dan Jenis");
                return;
            }

            MasterService service = new MasterServiceImpl();
            List<ImportItem<Subject>> items = SubjectImportParser.parse(rows, service.getAllSubjects(), service.getAllMajors());

            ImportPreviewDialog.show(mainRoot, "Impor Mapel dari Excel", file.getName(),
                    SubjectImportParser.COLUMNS, items,
                    list -> service.importSubjects(list, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole()),
                    onImported);
        } catch (Exception ex) {
            readError(ex);
        }
    }

    public static void saveSubjectTemplate(Window owner) {
        List<String> notes = List.of(
                "PETUNJUK PENGISIAN TEMPLATE MAPEL",
                "Isi data di sheet pertama, mulai dari baris 2. Jangan mengubah judul kolom di baris 1.",
                "Kolom wajib: Nama Mapel, Jenis. Kolom lain boleh kosong.",
                "Jenis diisi Umum atau Kejuruan. Mapel Umum: kolom Jurusan dikosongkan. Mapel Kejuruan: Jurusan wajib diisi.",
                "Jurusan diisi singkatan (mis. TKJ, DKV, TP) atau nama lengkap jurusan, sesuai data di menu Jurusan.",
                "Tingkat diisi X, XI, atau XII. Kosongkan bila mapel berlaku untuk semua tingkat.",
                "KKM diisi angka 0 - 100. Bila kosong, dipakai KKM " + SubjectImportParser.DEFAULT_KKM + ".",
                "Kode Mapel boleh kosong (dibuat otomatis, mis. M16). Isi bila sheet Pengampu di file yang sama "
                        + "merujuk mapel lewat kodenya. Kode tidak boleh sama dengan mapel lain.",
                "Mapel yang sudah terdaftar (kode sama, atau nama + jurusan + tingkat sama) tidak diimport ulang.",
                "Contoh baris: (kosong) | Dasar-dasar Program Keahlian | Kejuruan | TKJ | X | 75");

        saveTemplate(owner, "template-import-mapel.xlsx", "Mapel", SubjectImportParser.COLUMNS, notes);
    }

    // ------------------------------------------------------------------ Pengampu

    public static void importTeachingAssignments(Region mainRoot, Window owner, Runnable onImported) {
        File file = chooseExcel(owner, "Pilih file Excel data pengampu");
        if (file == null) {
            return;
        }

        try {
            List<TableParser.Row> rows = readRows(file, TeachingAssignmentImportParser.ALIASES,
                    TeachingAssignmentImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                noData("Guru dan Kelas (serta Kode Mapel atau Mapel)");
                return;
            }

            MasterService masterService = new MasterServiceImpl();
            TeachingAssignmentService service = new TeachingAssignmentServiceImpl();
            // getClassroomBy("") memuat jurusan kelas (dibutuhkan untuk cek mapel berlaku di kelas itu)
            List<ImportItem<TeachingAssignment>> items = TeachingAssignmentImportParser.parse(rows,
                    masterService.getAllTeachers(), masterService.getAllSubjects(), masterService.getClassroomBy(""),
                    service.getAllTeachingAssignments());

            ImportPreviewDialog.show(mainRoot, "Impor Pengampu dari Excel", file.getName(),
                    TeachingAssignmentImportParser.COLUMNS, items,
                    list -> service.importTeachingAssignments(list, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole()),
                    onImported);
        } catch (Exception ex) {
            readError(ex);
        }
    }

    public static void saveTeachingAssignmentTemplate(Window owner) {
        List<String> notes = List.of(
                "PETUNJUK PENGISIAN TEMPLATE PENGAMPU",
                "Impor Guru dan Mapel lebih dulu: pengampu hanya bisa merujuk guru, mapel, dan kelas yang sudah ada di aplikasi.",
                "Isi data di sheet pertama, mulai dari baris 2. Satu baris = satu guru mengajar satu mapel di satu kelas.",
                "Guru: nama lengkap sesuai menu Guru (perbedaan penulisan gelar masih dikenali). Bila ada nama guru yang sama, isi kolom NIP.",
                "Kode Mapel: kode di menu Mapel (mis. M10). Boleh dikosongkan bila kolom Mapel berisi nama mapel yang tidak kembar.",
                "Kode Guru: angka 1 - 99 untuk mapel yang diajar beberapa guru, mis. DDPK TKJ (1), (2), (3). Kosongkan bila tidak perlu.",
                "Dalam satu mapel, satu Kode Guru hanya boleh dipakai satu guru.",
                "Kelas: nama kelas sesuai menu Kelas (mis. X TKJ 1). Mapel harus berlaku untuk kelasnya (jurusan dan tingkat).",
                "Pengampu yang sudah terdaftar (guru, mapel, kelas sama) tidak diimport ulang.",
                "File data-mapel-pengampu dari sheet \"Pengampu (Rinci)\" bisa langsung diimpor.",
                "Contoh baris: Nama Guru, S.Kom | (kosong) | M10 | DDPK | 1 | X TKJ 1");

        saveTemplate(owner, "template-import-pengampu.xlsx", "Pengampu", TeachingAssignmentImportParser.COLUMNS, notes);
    }

    // ------------------------------------------------------------------ Jadwal ujian

    public static void importExamSchedules(Region mainRoot, Window owner, Runnable onImported) {
        File file = chooseExcel(owner, "Pilih file Excel jadwal ujian");
        if (file == null) {
            return;
        }

        try {
            List<TableParser.Row> rows = readRows(file, ExamScheduleImportParser.ALIASES,
                    ExamScheduleImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                noData("Tanggal, Jam Mulai, Jam Selesai, Kelas (serta Kode Mapel atau Mapel)");
                return;
            }

            MasterService masterService = new MasterServiceImpl();
            ExamScheduleService service = new ExamScheduleServiceImpl();
            // getClassroomBy("") memuat jurusan kelas (dibutuhkan untuk cek mapel berlaku di kelas itu)
            List<ImportItem<ExamSchedule>> items = ExamScheduleImportParser.parse(rows,
                    masterService.getAllSubjects(), masterService.getClassroomBy(""),
                    new TeachingAssignmentServiceImpl().getAllTeachingAssignments(),
                    service.getAllExamSchedulesDetailed(),
                    new co.id.service.impl.ExamTypeServiceImpl().getActiveCodes());

            ImportPreviewDialog.show(mainRoot, "Impor Jadwal Ujian dari Excel", file.getName(),
                    ExamScheduleImportParser.COLUMNS, items,
                    list -> service.importExamSchedules(list, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole()),
                    onImported);
        } catch (Exception ex) {
            readError(ex);
        }
    }

    public static void saveExamScheduleTemplate(Window owner) {
        List<String> notes = List.of(
                "PETUNJUK PENGISIAN TEMPLATE JADWAL UJIAN",
                "Isi data Kelas, Mapel, dan Pengampu lebih dulu. Isi di sheet pertama mulai baris 2. Satu baris = satu mapel diujikan di satu kelas.",
                "Tanggal: tulis sebagai teks 2026-11-30 (atau 30/11/2026). Jam Mulai/Selesai: 07:00 (atau 07.00).",
                "Kelas: nama kelas sesuai menu Kelas (mis. X TKJ 1).",
                "Kode Mapel: kode di menu Mapel (mis. M10). Boleh dikosongkan bila kolom Mapel berisi nama mapel yang tidak kembar.",
                "Mapel harus berlaku untuk kelasnya (jurusan dan tingkat), sama seperti form Jadwal Ujian.",
                "Kode Guru: wajib bila mapel itu di kelas tsb punya pengampu berkode (mis. DDPK TKJ (1), (2), (3)); pilihannya mengikuti data Pengampu.",
                "Jenis Ujian: " + String.join(", ", new co.id.service.impl.ExamTypeServiceImpl().getActiveCodes())
                        + " (daftar ini diatur di menu Master > Jenis Ujian)" + ". Semester: Ganjil / Genap. Tahun Akademik: 2026/2027.",
                "Mode Ujian: Offline (di ruangan) atau Online (tanpa ruangan & pengawas). Kosong = Offline.",
                "Kelas yang sama tidak boleh ujian di jam yang bertumpuk. Jadwal yang persis sama dengan yang sudah ada dilewati.",
                "Kode ujian dibuat otomatis. Ruangan dan pengawas jadwal Offline diatur di menu Penempatan Ruangan.",
                "Contoh baris: 2026-11-30 | 07:00 | 08:30 | X TKJ 1 | M19 | PAI | 3 | AAS | Ganjil | 2026/2027 | Offline | (kosong)");

        saveTemplate(owner, "template-import-jadwal-ujian.xlsx", "Jadwal Ujian", ExamScheduleImportParser.COLUMNS, notes);
    }

    // ------------------------------------------------------------------ Status administrasi siswa

    public static void importAdminStatuses(Region mainRoot, Window owner, Runnable onImported) {
        File file = chooseExcel(owner, "Pilih file Excel status administrasi");
        if (file == null) {
            return;
        }

        try {
            List<TableParser.Row> rows = readRows(file, AdminStatusImportParser.ALIASES, AdminStatusImportParser.REQUIRED, Set.of());
            if (rows.isEmpty()) {
                noData("NIS dan Status");
                return;
            }

            MasterService service = new MasterServiceImpl();
            // getStudentBy("") memuat kelas siswa (untuk ditampilkan di pratinjau)
            List<ImportItem<Student>> items = AdminStatusImportParser.parse(rows, service.getStudentBy(""));
            if (items.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION,
                        "Semua status di file sama dengan data sekarang. Tidak ada yang perlu diubah.").showAndWait();
                return;
            }

            ImportPreviewDialog.show(mainRoot, "Impor Status Administrasi", file.getName(),
                    AdminStatusImportParser.COLUMNS, items,
                    list -> service.importAdminStatuses(list, AuthContext.getCurrentUsername(), AuthContext.getCurrentRole()),
                    onImported);
        } catch (Exception ex) {
            readError(ex);
        }
    }

    /** Template berisi semua siswa beserta status sekarang, supaya bagian keuangan cukup mengubah kolom Status. */
    public static void saveAdminStatusTemplate(Window owner) {
        List<String> notes = List.of(
                "PETUNJUK PENGISIAN STATUS ADMINISTRASI",
                "Sheet pertama sudah berisi semua siswa beserta statusnya saat ini. Cukup ubah kolom Status (dan Keterangan bila perlu).",
                "Status diisi Lunas atau Belum Lunas.",
                "Siswa Belum Lunas otomatis tidak dicentang saat memilih peserta ujian. Admin tetap bisa mencentangnya (dispensasi).",
                "Kolom wajib: NIS dan Status. Nama Siswa dan Kelas hanya petunjuk, tidak mengubah data siswa.",
                "Baris boleh dihapus; siswa yang tidak ada di file tidak berubah statusnya.",
                "Jangan mengubah judul kolom di baris 1.");

        MasterService service = new MasterServiceImpl();
        List<List<String>> rows = service.getStudentBy("").stream()
                .sorted(java.util.Comparator
                        .comparing((Student s) -> s.getClassroom() != null ? s.getClassroom().getName() : "")
                        .thenComparing(s -> s.getName() == null ? "" : s.getName()))
                .map(s -> List.of(
                        s.getNis() == null ? "" : s.getNis(),
                        s.getName() == null ? "" : s.getName(),
                        s.getClassroom() != null ? s.getClassroom().getName() : "",
                        s.getAdminStatus() == null ? Student.ADMIN_PAID : s.getAdminStatus(),
                        s.getAdminNote() == null ? "" : s.getAdminNote()))
                .toList();

        saveTemplate(owner, "status-administrasi-siswa.xlsx", "Status Administrasi", AdminStatusImportParser.COLUMNS, notes, rows);
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

            ImportPreviewDialog.show(mainRoot, "Impor Nilai: " + scheduleTitle(schedule), file.getName(),
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

    /** Pilih file impor: Excel (.xlsx/.xls) atau CSV (.csv, juga .txt hasil ekspor aplikasi lain). */
    private static File chooseExcel(Window owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel / CSV (*.xlsx, *.xls, *.csv)", "*.xlsx", "*.xls", "*.csv", "*.txt"),
                new FileChooser.ExtensionFilter("File Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("File CSV (*.csv, *.txt)", "*.csv", "*.txt"));
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
        chooser.setTitle("Simpan template");
        chooser.setInitialFileName(defaultName);
        FileChooser.ExtensionFilter excelFilter = new FileChooser.ExtensionFilter("File Excel (*.xlsx)", "*.xlsx");
        FileChooser.ExtensionFilter csvFilter = new FileChooser.ExtensionFilter("File CSV (*.csv)", "*.csv");
        chooser.getExtensionFilters().addAll(excelFilter, csvFilter);

        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }

        // Format mengikuti pilihan jenis file (atau akhiran nama file yang diketik)
        boolean csv = file.getName().toLowerCase().endsWith(".csv")
                || (chooser.getSelectedExtensionFilter() == csvFilter && !file.getName().toLowerCase().endsWith(".xlsx"));
        if (csv && !file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName().replaceFirst("(?i)\\.xlsx$", "") + ".csv");
        }

        try {
            if (csv) {
                CsvTemplateWriter.write(file, headers, rows);
                new Alert(Alert.AlertType.INFORMATION, "Template CSV tersimpan di:\n" + file.getAbsolutePath()
                        + "\n\nPetunjuk pengisian hanya ada di template Excel. Bila file CSV dibuka lalu disimpan "
                        + "dengan Excel, angka panjang (NIP, NIS, No. HP) bisa berubah bentuk; untuk data seperti itu "
                        + "lebih aman memakai template Excel.").showAndWait();
                return;
            }
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
        new Alert(Alert.AlertType.ERROR, "Gagal membaca file: " + ex.getMessage()
                + "\nPastikan file berformat Excel (.xlsx/.xls) atau CSV. "
                + "Jika file sedang dibuka di Excel, tutup dulu lalu coba lagi.").showAndWait();
    }
}