package dz.scientifica.hardwareinspectorpro;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Portable, opt-in restoration of monitoring counters from prior exported reports.
 * Historical times were measured by earlier app versions; they cannot be made more
 * precise retroactively. Import does not attempt to reconstruct missing events.
 */
public final class MonitorBackup {
    private static final long MAX_REASONABLE_MS = 20L * 365 * 24 * 3600 * 1000;
    private static final Pattern LEGACY = Pattern.compile(
            "(\\d+)\\s*يوم.*?(\\d+)\\s*ساعة.*?(\\d+)\\s*دقيقة");
    private static final Pattern EXACT = Pattern.compile(
            "(\\d+)\\s*يوم\\s*(\\d{1,2}):(\\d{2}):(\\d{2})");

    public static final class Snapshot {
        public long monitorMs;
        public long screenMs;
        public long chargingMs;
        public long uncertainMs;
        public long firstSeen;
        public JSONArray events;
        public long eventsDiscarded;
        public boolean legacyApproximate;
    }

    private MonitorBackup() {}

    private static long parseDuration(String value) {
        if (value == null) throw new IllegalArgumentException("مدة مفقودة");
        Matcher exact = EXACT.matcher(value);
        if (exact.find()) {
            long seconds = Long.parseLong(exact.group(1)) * 86400
                    + Long.parseLong(exact.group(2)) * 3600
                    + Long.parseLong(exact.group(3)) * 60
                    + Long.parseLong(exact.group(4));
            return seconds * 1000;
        }
        Matcher old = LEGACY.matcher(value);
        if (old.find()) {
            long seconds = Long.parseLong(old.group(1)) * 86400
                    + Long.parseLong(old.group(2)) * 3600
                    + Long.parseLong(old.group(3)) * 60;
            return seconds * 1000;
        }
        throw new IllegalArgumentException("صيغة مدة غير مدعومة: " + value);
    }

    private static void validate(long value) {
        if (value < 0 || value > MAX_REASONABLE_MS) {
            throw new IllegalArgumentException("عداد استخدام خارج النطاق المتوقع");
        }
    }

    public static Snapshot parse(Context context, JSONObject data) throws Exception {
        String expectedDevice = Build.MANUFACTURER + " " + Build.MODEL;
        String device = data.optString("device", "");
        if (!expectedDevice.equalsIgnoreCase(device)) {
            throw new IllegalArgumentException("التقرير يخص جهازًا آخر (" + device + ")");
        }
        if (!data.optString("schema", "").startsWith("HardwareInspectorPro-")) {
            throw new IllegalArgumentException("ملف JSON ليس تقرير Hardware Inspector Pro");
        }
        Snapshot item = new Snapshot();
        JSONObject v2 = data.optJSONObject("monitoring");
        if (v2 != null) {
            item.monitorMs = v2.getLong("monitorMs");
            item.screenMs = v2.getLong("screenOnMs");
            item.chargingMs = v2.getLong("chargingMs");
            item.uncertainMs = v2.optLong("uncertainStateMs", 0);
            item.events = v2.optJSONArray("events");
            item.eventsDiscarded = v2.optLong("olderEventsDiscarded", 0);
        } else {
            JSONObject sections = data.getJSONObject("sections");
            JSONObject old = sections.getJSONObject("09 | العمر وساعات التشغيل");
            item.monitorMs = parseDuration(old.getString("مدة تشغيل المراقب المسجلة"));
            item.screenMs = parseDuration(old.getString("مدة إضاءة الشاشة المسجلة أثناء المراقبة"));
            item.chargingMs = parseDuration(old.getString("مدة الشحن المسجلة أثناء المراقبة"));
            item.uncertainMs = parseDuration(old.getString("فترات تعذر تتبعها (لا تُحتسب)"));
            item.legacyApproximate = true;
        }
        validate(item.monitorMs);
        validate(item.screenMs);
        validate(item.chargingMs);
        validate(item.uncertainMs);

        // Estimate is never imported as a real hardware lifetime.
        JSONObject sections = data.optJSONObject("sections");
        if (sections != null) {
            JSONObject age = sections.optJSONObject("09 | العمر وساعات التشغيل");
            if (age != null) {
                String value = age.optString("أول رصد بواسطة هذا التطبيق", "");
                if (!value.isEmpty() && !value.startsWith("غير")) {
                    try {
                        item.firstSeen = new SimpleDateFormat("yyyy-MM-dd HH:mm",
                                Locale.US).parse(value).getTime();
                    } catch (Exception ignored) {}
                }
            }
        }
        return item;
    }

    /**
     * Import only into empty history. This avoids overwriting newly captured data
     * or counting a report twice. Importing requires explicit UI confirmation.
     */
    public static void restore(Context context, Snapshot data) {
        SharedPreferences p = context.getSharedPreferences(
                MonitorService.PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean("monitor_running", false)) {
            throw new IllegalStateException("أوقف المراقبة قبل استرجاع البيانات");
        }
        if (EventJournal.events(context).length() > 0) {
            throw new IllegalStateException(
                    "يوجد سجل أحداث في التطبيق؛ الاسترجاع متاح قبل بدء أي مراقبة جديدة فقط");
        }
        if (p.getLong("monitor_ms", 0) > 0 || p.getLong("screen_ms", 0) > 0
                || p.getLong("charging_ms", 0) > 0) {
            throw new IllegalStateException(
                    "توجد بيانات استخدام حالية؛ لا يُسمح بالاستيراد فوقها لتجنب فقد البيانات");
        }
        SharedPreferences.Editor e = p.edit();
        e.putLong("monitor_ms", data.monitorMs);
        e.putLong("screen_ms", data.screenMs);
        e.putLong("charging_ms", data.chargingMs);
        e.putLong("unobserved_ms", data.uncertainMs);
        if (data.firstSeen > 0) e.putLong("first_seen", data.firstSeen);
        if (!e.commit()) throw new IllegalStateException("تعذر حفظ البيانات المسترجعة");
        if (data.events != null) EventJournal.restoreHistory(context, data.events, data.eventsDiscarded);
        EventJournal.append(context, "backup_restored", "user_confirmed_import",
                false, false, 0,
                data.legacyApproximate
                        ? "استُعيدت عدادات من تقرير قديم مقرب إلى الدقيقة؛ الأحداث السابقة غير قابلة للاسترجاع."
                        : "استُعيدت عدادات وسجل أحداث موثق من تقرير JSON سابق.");
    }

    public static String preview(Snapshot data) {
        return "ستُسترجع القيم التالية من التقرير السابق:\n\n"
                + "مدة المراقبة: " + HardwareReport.durationSeconds(data.monitorMs)
                + "\nمدة الشاشة: " + HardwareReport.durationSeconds(data.screenMs)
                + "\nمدة الشحن: " + HardwareReport.durationSeconds(data.chargingMs)
                + "\nفترات غير مؤكدة: " + HardwareReport.durationSeconds(data.uncertainMs)
                + "\n\n"
                + (data.legacyApproximate
                    ? "الأرقام مأخوذة من تقرير قديم يعرض الدقائق فقط، دون معرفة الثواني الفعلية.\n"
                    : "أوقات الأحداث الموجودة في الملف ستُسترجع أيضًا.\n")
                + "لا يمكن استرجاع قياسات لم تُسجل أصلًا أو بيانات حُذفت دون نسخة احتياطية.";
    }
}
