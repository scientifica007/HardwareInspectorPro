package dz.scientifica.hardwareinspectorpro;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Optional read-only battery lifetime telemetry exported by Linux power_supply.
 * Data may not exist or may be blocked by vendor permissions / SELinux.
 */
public final class BatteryWear {
    private BatteryWear() {}

    private static String read(String... paths) {
        for (String path : paths) {
            File f = new File(path);
            if (!f.isFile()) continue;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String result = br.readLine();
                if (result != null && !result.trim().isEmpty()) return result.trim();
            } catch (Exception ignored) {
                // An unreadable sysfs file is NOT evidence that the hardware lacks the sensor.
            }
        }
        return null;
    }

    private static Long positiveLong(String input) {
        if (input == null) return null;
        try {
            long v = Long.parseLong(input.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Map<String, String> probe() {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        out.put("نوع القراءة الإضافية", "قراءة اختيارية من عدادات نواة Linux؛ لا تعتمد على Root");
        String cycles = read(
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/battery/battery_cycle");
        if (cycles == null) {
            out.put("دورات الشحن التراكمية للبطارية (sysfs)",
                    "غير متاحة أو محجوبة على هذا الإصدار من ColorOS");
        } else {
            Long n = positiveLong(cycles);
            if (n != null && n < 1_000_000L) {
                out.put("دورات شحن البطارية التي أبلغ عنها العداد", n + " دورة مكافئة بحسب وحدة القياس");
                out.put("ملاحظة عن دورات الشحن", "قيمة مبلّغ عنها من السائق ولا تثبت تاريخ بدء استخدامها أو ما إذا أعيد ضبطها");
            } else {
                out.put("عداد الدورات الخام", cycles);
                out.put("تفسير عداد الدورات", "صفر أو قيمة غير قابلة للاعتماد؛ لا يُعد دليلًا على بطارية جديدة");
            }
        }

        String currentFull = read(
                "/sys/class/power_supply/battery/charge_full");
        String designFull = read(
                "/sys/class/power_supply/battery/charge_full_design");
        Long now = positiveLong(currentFull);
        Long design = positiveLong(designFull);
        if (currentFull != null)
            out.put("سعة الشحن الكامل المعلنة (قيمة خام)", currentFull);
        else
            out.put("سعة الشحن الكامل المعلنة", "غير متاحة للقراءة");
        if (designFull != null)
            out.put("سعة التصميم المعلنة (قيمة خام)", designFull);
        else
            out.put("سعة التصميم المعلنة", "غير متاحة للقراءة");

        if (now != null && design != null) {
            double percent = now * 100.0 / design;
            if (percent >= 20.0 && percent <= 130.0) {
                out.put("نسبة سعة الشحن الحالية إلى التصميم (تقدير من السائق)",
                        String.format(Locale.US, "%.1f %%", percent));
                out.put("دقة النسبة", "مؤشر تقريبي فقط إذا كان العدّادان بوحدات متسقة ومُعايرين؛ ليس اختبار تفريغ فعلي");
            } else {
                out.put("نسبة السعة", "قيم سعة غير متسقة؛ امتنع التطبيق عن حساب النسبة");
            }
        } else {
            out.put("نسبة صحة البطارية من السعة الأصلية", "لا يمكن حسابها من بيانات موثوقة متاحة");
        }
        out.put("عمر البطارية بالساعات أو السنوات",
                "غير قابل للاستخراج من عدد دورات الشحن دون تاريخ قياس موثق");
        return out;
    }
}
