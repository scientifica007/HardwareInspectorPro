package dz.scientifica.hardwareinspectorpro;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort, READ-ONLY storage diagnostics via publicly readable Linux sysfs.
 * eMMC DEVICE_LIFE_TIME_EST_TYP_A/B indicate estimated erase-cycle wear bands,
 * not hours of operation and not remaining calendar life.
 *
 * No root, shell, hidden APIs, device IDs, or dangerous write commands are used.
 */
public final class StorageWear {
    private StorageWear() {}

    private static String read(String path) {
        File f = new File(path);
        if (!f.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String s = reader.readLine();
            return s == null ? null : s.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstReadable(String... paths) {
        for (String p : paths) {
            String s = read(p);
            if (s != null && !s.isEmpty()) return s;
        }
        return null;
    }

    private static int hexByte(String token) {
        try {
            String s = token.trim().toLowerCase(Locale.US);
            if (s.startsWith("0x")) s = s.substring(2);
            int n = Integer.parseInt(s, 16);
            return n >= 0 && n <= 255 ? n : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String wearBand(int v) {
        if (v == 0) return "غير معرّف من الشريحة (0x00)";
        if (v >= 1 && v <= 10)
            return "استهلاك دورة الكتابة المقدّر " + ((v - 1) * 10) + "–" + (v * 10) +
                    "% من معيار التحمل المرجعي للشريحة (ليس عمرًا زمنيًا)";
        if (v == 11) return "تجاوز 100% من معيار التحمل المرجعي للشريحة (0x0B)";
        return "قيمة محجوزة أو غير معروفة: 0x" + Integer.toHexString(v);
    }

    private static String preEol(int code) {
        switch (code) {
            case 1: return "NORMAL (0x01) — لا تحذير استهلاك مسجّل";
            case 2: return "WARNING (0x02) — تنبيه استهلاك احتياطي الكتل";
            case 3: return "URGENT (0x03) — تنبيه حرج";
            case 0: return "غير معرّف من الشريحة (0x00)";
            default: return "رمز غير معروف: 0x" + Integer.toHexString(code);
        }
    }

    private static String gibFromSectors(long sectors) {
        return String.format(Locale.US, "%.2f GiB", sectors * (512.0 / 1073741824.0));
    }

    public static Map<String, String> probe() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        out.put("نوع القياس", "مؤشرات سجل عتاد متاحة للقراءة فقط؛ تختلف حسب إصدار نواة الهاتف والصلاحيات");
        String removable = read("/sys/block/mmcblk0/removable");
        if (removable != null)
            out.put("القرص mmcblk0 قابل للإزالة؟", removable.equals("0") ? "لا حسب النواة" : "نعم أو غير معروف");
        String model = read("/sys/block/mmcblk0/device/name");
        if (model != null) out.put("اسم شريحة eMMC (إذا أتاحته النواة)", model);
        String lifetime = firstReadable(
                "/sys/block/mmcblk0/device/life_time",
                "/sys/bus/mmc/devices/mmc0:0001/life_time",
                "/sys/class/mmc_host/mmc0/mmc0:0001/life_time");
        String eol = firstReadable(
                "/sys/block/mmcblk0/device/pre_eol_info",
                "/sys/bus/mmc/devices/mmc0:0001/pre_eol_info",
                "/sys/class/mmc_host/mmc0/mmc0:0001/pre_eol_info");
        if (lifetime == null) {
            out.put("eMMC DEVICE_LIFE_TIME A/B",
                    "غير متاح للتطبيق (قد يكون المسار غير موجود أو محجوبًا بـ SELinux)");
        } else {
            out.put("القيم الخام لاهتراء eMMC", lifetime);
            String[] fields = lifetime.split("\\s+");
            if (fields.length >= 1 && hexByte(fields[0]) >= 0)
                out.put("اهتراء خلايا eMMC من النوع A", wearBand(hexByte(fields[0])));
            if (fields.length >= 2 && hexByte(fields[1]) >= 0)
                out.put("اهتراء خلايا eMMC من النوع B", wearBand(hexByte(fields[1])));
        }
        if (eol == null) {
            out.put("eMMC Pre-EOL", "غير متاح للقراءة");
        } else {
            out.put("eMMC Pre-EOL (خام)", eol);
            int code = hexByte(eol.split("\\s+")[0]);
            if (code >= 0) out.put("تفسير Pre-EOL", preEol(code));
        }

        // Linux block stats: sector count and I/O busy time are SINCE boot/device registration.
        // Kernel sector accounting uses 512-byte sectors even if physical blocks are larger.
        String stat = read("/sys/block/mmcblk0/stat");
        if (stat == null) {
            out.put("عداد عمليات التخزين منذ الإقلاع", "محجوب أو غير متاح");
        } else {
            String[] fields = stat.trim().split("\\s+");
            if (fields.length >= 11) {
                try {
                    long sectorsRead = Long.parseLong(fields[2]);
                    long sectorsWritten = Long.parseLong(fields[6]);
                    long ioMillis = Long.parseLong(fields[9]);
                    out.put("مقدار القراءة من القرص منذ تهيئة عدادات النواة",
                            gibFromSectors(sectorsRead) + " (عادةً منذ آخر إقلاع)");
                    out.put("مقدار الكتابة إلى القرص منذ تهيئة عدادات النواة",
                            gibFromSectors(sectorsWritten) + " (عادةً منذ آخر إقلاع)");
                    out.put("زمن وجود عمليات تخزين I/O منذ تهيئة العداد",
                            HardwareReport.duration(ioMillis) +
                            " (زمن انشغال الإدخال/الإخراج، وليس ساعات تشغيل الشريحة)");
                } catch (NumberFormatException ignored) {
                    out.put("عدادات التخزين", "صيغة غير مدعومة");
                }
            } else {
                out.put("عدادات التخزين", "صيغة غير متوقعة من النواة");
            }
        }
        out.put("ساعات عمل eMMC منذ التصنيع",
                "لا يمكن استخراجها من eMMC life_time أو عدادات النواة");
        out.put("ما يمكن استنتاجه",
                "اهتراء الذاكرة لا يحدد عمر الهاتف بالسنوات أو ساعات الاستخدام الفعلية");
        return out;
    }
}
