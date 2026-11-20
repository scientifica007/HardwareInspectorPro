package dz.scientifica.hardwareinspectorpro;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** One-time measurements. All unknown values remain explicitly unknown. */
public final class HardwareReport {
    private final LinkedHashMap<String, LinkedHashMap<String, String>> sections = new LinkedHashMap<>();
    private static final String NA = "غير متاح من النظام";
    private final Context context;

    private HardwareReport(Context context) {
        this.context = context.getApplicationContext();
    }

    public static HardwareReport capture(Context context) {
        HardwareReport r = new HardwareReport(context);
        r.device();
        r.battery();
        r.cpuMemory();
        r.storageWear();
        r.display();
        r.sensors();
        r.cameras();
        r.network();
        r.audioAndPorts();
        r.lifetime();
        return r;
    }

    public Map<String, LinkedHashMap<String, String>> sections() { return sections; }

    private void put(String section, String name, Object value) {
        if (!sections.containsKey(section)) sections.put(section, new LinkedHashMap<>());
        sections.get(section).put(name, value == null ? NA : String.valueOf(value));
    }

    private void device() {
        String s = "01 | الجهاز والنظام";
        put(s, "الشركة", Build.MANUFACTURER);
        put(s, "العلامة", Build.BRAND);
        put(s, "الطراز المبلغ عنه", Build.MODEL);
        put(s, "اسم الجهاز", Build.DEVICE);
        put(s, "اللوحة", Build.BOARD);
        put(s, "منصة العتاد", Build.HARDWARE);
        put(s, "المنتج", Build.PRODUCT);
        if (Build.VERSION.SDK_INT >= 31) {
            put(s, "الشريحة SoC", Build.SOC_MODEL);
            put(s, "صانع الشريحة", Build.SOC_MANUFACTURER);
        }
        put(s, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        put(s, "ترقيعة الأمان", Build.VERSION.SECURITY_PATCH);
        put(s, "معماريات المعالج", String.join(", ", Build.SUPPORTED_ABIS));
        put(s, "تاريخ بناء نسخة النظام (ليس تاريخ التصنيع)",
                new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(Build.TIME)));
        put(s, "نوع القراءة", "بيانات أفصح عنها نظام Android؛ لا تثبت أصالة القطع");
    }

    private void battery() {
        String s = "02 | البطارية والطاقة";
        Intent b = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) {
            put(s, "البيانات", "تعذرت قراءة نشرات البطارية");
            return;
        }
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (scale > 0 && level >= 0) put(s, "الشحن الحالي", String.format(Locale.US, "%.1f %%", level * 100.0 / scale));
        int state = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        put(s, "حالة الشحن", state == BatteryManager.BATTERY_STATUS_CHARGING ? "تشحن" :
                state == BatteryManager.BATTERY_STATUS_FULL ? "ممتلئة" :
                state == BatteryManager.BATTERY_STATUS_DISCHARGING ? "تفرغ" :
                state == BatteryManager.BATTERY_STATUS_NOT_CHARGING ? "لا تشحن" : "غير معروفة");
        int health = b.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        put(s, "حالة الصحة التي يبلغ عنها النظام",
                health == BatteryManager.BATTERY_HEALTH_GOOD ? "جيدة حسب النظام (ليست 100%)" :
                health == BatteryManager.BATTERY_HEALTH_OVERHEAT ? "سخونة زائدة" :
                health == BatteryManager.BATTERY_HEALTH_DEAD ? "عطل شديد" :
                health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE ? "جهد مفرط" :
                health == BatteryManager.BATTERY_HEALTH_COLD ? "برودة شديدة" :
                health == BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE ? "خلل غير محدد" : NA);
        int temperature = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (temperature != Integer.MIN_VALUE)
            put(s, "حرارة البطارية", String.format(Locale.US, "%.1f °C", temperature / 10.0));
        int mv = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        if (mv > 0) put(s, "جهد البطارية", mv + " mV");
        put(s, "تقنية البطارية", b.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
        int plug = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        put(s, "مصدر الشحن", plug == 0 ? "لا يوجد" :
                (plug & BatteryManager.BATTERY_PLUGGED_AC) != 0 ? "شاحن AC" :
                (plug & BatteryManager.BATTERY_PLUGGED_USB) != 0 ? "USB" :
                (plug & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 ? "لاسلكي" : NA);
        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (manager != null) {
            int current = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (current != Integer.MIN_VALUE && current != 0)
                put(s, "التيار اللحظي (كما أبلغه الجهاز)", current + " µA");
            int charge = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (charge > 0 && charge != Integer.MAX_VALUE)
                put(s, "شحنة متبقية مقدرة (ليست السعة الأصلية)", String.format(Locale.US, "%.0f mAh", charge / 1000.0));
        }
        if (Build.VERSION.SDK_INT >= 34) {
            int cycles = b.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
            put(s, "دورات الشحن من النظام", cycles >= 0 ? String.valueOf(cycles) : NA);
        } else {
            put(s, "دورات الشحن طوال العمر", "لا تتيحها واجهة Android " + Build.VERSION.RELEASE + " القياسية");
        }
        put(s, "نسبة الصحة والسعة الأصلية", NA + "؛ لا يمكن استنتاجها من نسبة الشحن");
    }

    private void cpuMemory() {
        String s = "03 | CPU / RAM / التخزين / GPU";
        put(s, "أنوية المعالج المتاحة للتطبيق", Runtime.getRuntime().availableProcessors());
        String cpu = readFile("/proc/cpuinfo", 2400);
        if (cpu != null) {
            for (String line : cpu.split("\\n")) {
                if (line.toLowerCase(Locale.US).startsWith("hardware") ||
                        line.toLowerCase(Locale.US).startsWith("model name")) {
                    put(s, "اسم المعالج المبلغ عنه", line.trim());
                    break;
                }
            }
        }
        String freq = readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq", 40);
        if (freq != null) {
            try { put(s, "أقصى تردد للنواة 0", String.format(Locale.US, "%.0f MHz", Long.parseLong(freq.trim()) / 1000.0)); }
            catch (NumberFormatException ignored) {}
        }
        String current = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", 40);
        if (current != null) {
            try { put(s, "التردد الجاري للنواة 0 (لقطة)", String.format(Locale.US, "%.0f MHz", Long.parseLong(current.trim()) / 1000.0)); }
            catch (NumberFormatException ignored) {}
        }
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memory);
            put(s, "RAM الكلية المعلنة للنظام", gb(memory.totalMem));
            put(s, "RAM المتاحة حاليًا", gb(memory.availMem));
            put(s, "حالة انخفاض ذاكرة النظام", memory.lowMemory ? "نعم" : "لا");
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            put(s, "الحجم الكلي لقسم بيانات المستخدم", gb(fs.getTotalBytes()));
            put(s, "المتاح بقسم بيانات المستخدم", gb(fs.getAvailableBytes()));
        } catch (Exception e) { put(s, "قسم البيانات", NA); }
        String gpu = gpuRenderer();
        put(s, "معالج الرسوميات المعلن عبر OpenGL", gpu == null ? NA : gpu);
        put(s, "حرارة المعالج", "غير مضمونة القراءة على Android العادي؛ تختلف صلاحيات sysfs");
        put(s, "تاريخ إجمالي تشغيل CPU/GPU/Flash", NA + " منذ التصنيع");
    }

    private void display() {
        String s = "04 | الشاشة واللمس";
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        Display d = wm.getDefaultDisplay();
        Point size = new Point();
        d.getRealSize(size);
        DisplayMetrics metrics = new DisplayMetrics();
        d.getRealMetrics(metrics);
        put(s, "دقة عرض النظام الفعلية", size.x + " × " + size.y + " px");
        put(s, "الكثافة المعلنة", metrics.densityDpi + " dpi");
        put(s, "معدل التحديث المعلن", String.format(Locale.US, "%.1f Hz", d.getRefreshRate()));
        put(s, "دعم اللمس المتعدد", context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) ? "نعم" : "لم يُعلن النظام عن دعمه");
        put(s, "ملاحظة", "دقة إطار النظام ليست قياسًا مجهريًا لمصفوفة الشاشة");
    }

    private void sensors() {
        String s = "05 | المستشعرات الفيزيائية";
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return;
        List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
        put(s, "عدد إدخالات المستشعرات", all.size() + " (قد يتضمن مستشعرات افتراضية)");
        for (int i = 0; i < all.size(); i++) {
            Sensor sensor = all.get(i);
            put(s, String.format(Locale.US, "%02d | %s", i + 1, sensor.getName()),
                    "النوع " + sensor.getType() + " • " + sensor.getVendor() +
                    " • resolution " + sensor.getResolution() + " • power " + sensor.getPower() + " mA");
        }
    }

    private void cameras() {
        String s = "06 | الكاميرات";
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return;
            String[] ids = cm.getCameraIdList();
            put(s, "أجهزة الكاميرا المنطقية", ids.length + " (لا يساوي دائمًا عدد العدسات)");
            for (String id : ids) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer direction = c.get(CameraCharacteristics.LENS_FACING);
                String where = direction == null ? "غير معروف" :
                        direction == CameraCharacteristics.LENS_FACING_FRONT ? "أمامية" :
                        direction == CameraCharacteristics.LENS_FACING_BACK ? "خلفية" : "خارجية";
                Size size = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                Integer orientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
                put(s, "Camera ID " + id, where + " • مصفوفة " + (size == null ? "؟" : size) +
                        " • زاوية " + (orientation == null ? "؟" : orientation + "°"));
                if (Build.VERSION.SDK_INT >= 28 && !c.getPhysicalCameraIds().isEmpty())
                    put(s, "مكوّنات Camera " + id, c.getPhysicalCameraIds().toString());
            }
        } catch (Exception e) {
            put(s, "حالة واجهة الكاميرات", "تعذر القراءة: " + e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("deprecation")
    private void network() {
        String s = "07 | الشبكة والاتصالات";
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
            put(s, "الاتصال الحالي", info == null ? "غير متصل أو غير متاح" :
                    (info.isConnected() ? "متصل عبر " + info.getTypeName() : "غير متصل"));
        } catch (Exception e) { put(s, "الشبكة", NA); }
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                put(s, "Wi-Fi", wifi.isWifiEnabled() ? "مفعّل" : "معطّل");
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    WifiInfo info = wifi.getConnectionInfo();
                    if (info != null) {
                        put(s, "اسم الشبكة الحالية", info.getSSID());
                        put(s, "قوة الإشارة", info.getRssi() + " dBm");
                        put(s, "سرعة الربط المعلنة", info.getLinkSpeed() + " Mbps (ليست سرعة الإنترنت)");
                        put(s, "تردد Wi-Fi", info.getFrequency() + " MHz");
                    }
                } else put(s, "SSID / RSSI", "يتطلب إذن الموقع وتفعيل خدماته");
            }
        } catch (Exception e) { put(s, "تفاصيل Wi-Fi", NA); }
        try {
            if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                put(s, "Bluetooth", "يتطلب إذن الأجهزة القريبة");
            } else {
                BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                put(s, "Bluetooth", bt == null ? "لا يوجد محول معلن" : bt.isEnabled() ? "متاح ومفعّل" : "متاح ومعطّل");
            }
        } catch (Exception e) { put(s, "Bluetooth", NA); }
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                put(s, "اسم المشغل", tm.getNetworkOperatorName());
                put(s, "نوع الهاتف", tm.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM ? "GSM" :
                        tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA ? "CDMA" : "غير محدد");
            }
        } catch (Exception e) { put(s, "الاتصال الخلوي", NA); }
        put(s, "IMEI / الرقم التسلسلي المقيد", "لا تتم محاولة قراءته (قيود Android والخصوصية)");
    }

    private void audioAndPorts() {
        String s = "08 | الصوت والمنافذ";
        PackageManager pm = context.getPackageManager();
        put(s, "ميكروفون", pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) ? "معلن" : "غير معلن");
        put(s, "اتصال USB Host", pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST) ? "مدعوم" : "غير معلن");
        UsbManager usb = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usb != null) {
            try { put(s, "أجهزة USB الموصولة", usb.getDeviceList().size()); }
            catch (Exception e) { put(s, "أجهزة USB", NA); }
        }
        try {
            NfcAdapter nfc = NfcAdapter.getDefaultAdapter(context);
            put(s, "NFC", nfc == null ? "غير موجود بحسب Android" : nfc.isEnabled() ? "موجود ومفعّل" : "موجود ومعطّل");
        } catch (Exception e) { put(s, "NFC", NA); }
        put(s, "زمن تشغيل المكبر والكاميرا والميكروفون منذ شراء الهاتف", NA);
    }

    private void storageWear() {
        String s = "10 | اهتراء التخزين eMMC وعدادات I/O";
        for (Map.Entry<String, String> row : StorageWear.probe().entrySet()) {
            put(s, row.getKey(), row.getValue());
        }
    }

    private void lifetime() {
        String s = "09 | العمر وساعات التشغيل";
        put(s, "الوقت منذ آخر إقلاع (بما فيه النوم)", duration(SystemClock.elapsedRealtime()));
        put(s, "وقت يقظة النظام منذ آخر إقلاع (دون النوم العميق)", duration(SystemClock.uptimeMillis()));
        put(s, "تاريخ تصنيع الهاتف الفعلي", NA);
        SharedPreferences p = context.getSharedPreferences(MonitorService.PREFS, Context.MODE_PRIVATE);
        put(s, "تاريخ أول استعمال للهاتف", "لا يوجد مصدر محلي عام موثوق لهذا التاريخ؛ لا يُطلب إدخاله يدويًا");
        long elapsed = SystemClock.elapsedRealtime();
        put(s, "آخر إقلاع تقريبي (محسوب من عداد التشغيل)",
                new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
                    .format(new Date(System.currentTimeMillis() - elapsed)));
        long observed = p.getLong("first_seen", 0);
        put(s, "أول رصد بواسطة هذا التطبيق", observed == 0 ? NA :
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(observed)));
        put(s, "مدة تشغيل المراقب المسجلة", duration(p.getLong("monitor_ms", 0)));
        put(s, "مدة إضاءة الشاشة المسجلة أثناء المراقبة", duration(p.getLong("screen_ms", 0)));
        put(s, "مدة الشحن المسجلة أثناء المراقبة", duration(p.getLong("charging_ms", 0)));
        put(s, "فترات تعذر تتبعها (لا تُحتسب)", duration(p.getLong("unobserved_ms", 0)));
        put(s, "تغطية سجل الاستخدام", "منذ تفعيل المراقبة داخل التطبيق؛ ليس سجل عمر القطع");
        put(s, "فترات تعذر المراقبة", "لا تُعوَّض بتقديرات افتراضية");
        put(s, "الجيروسكوب وزمن عمل الكاميرا", "لا يوفر Android عداد ساعات عمر تراكمي لهذه القطع");
        put(s, "ساعات التشغيل التراكمية للقطع قبل تثبيت التطبيق", NA);
    }

    private static String readFile(String path, int maxChars) {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(path)))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && sb.length() < maxChars) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception ignored) { return null; }
    }

    private static String gpuRenderer() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) return null;
        int[] ver = new int[2];
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return null;
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        try {
            int[] attrs = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE};
            EGLConfig[] cfg = new EGLConfig[1];
            int[] n = new int[1];
            if (!EGL14.eglChooseConfig(display, attrs, 0, cfg, 0, 1, n, 0) || n[0] < 1) return null;
            int[] pa = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
            surface = EGL14.eglCreatePbufferSurface(display, cfg[0], pa, 0);
            int[] ca = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            context = EGL14.eglCreateContext(display, cfg[0], EGL14.EGL_NO_CONTEXT, ca, 0);
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null;
            return GLES20.glGetString(GLES20.GL_RENDERER) + " | " + GLES20.glGetString(GLES20.GL_VERSION);
        } catch (Exception ignored) { return null; }
        finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            EGL14.eglTerminate(display);
        }
    }

    private static String gb(long bytes) {
        return String.format(Locale.US, "%.2f GiB", bytes / 1073741824.0);
    }

    public static String duration(long millis) {
        long minutes = Math.max(0, millis / 60000);
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        return days + " يوم، " + hours + " ساعة، " + mins + " دقيقة";
    }

    public String asText() {
        StringBuilder s = new StringBuilder("HardwareInspectorPro | تقرير فحص\n");
        s.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("\n\n");
        for (Map.Entry<String, LinkedHashMap<String, String>> block : sections.entrySet()) {
            s.append(block.getKey()).append("\n");
            for (Map.Entry<String, String> row : block.getValue().entrySet())
                s.append("  ").append(row.getKey()).append(": ").append(row.getValue()).append("\n");
            s.append("\n");
        }
        return s.toString();
    }

    public String asJson() {
        try {
            JSONObject report = new JSONObject();
            report.put("schema", "HardwareInspectorPro-v1");
            report.put("collectedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()));
            report.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            JSONObject blocks = new JSONObject();
            for (Map.Entry<String, LinkedHashMap<String, String>> block : sections.entrySet()) {
                JSONObject rows = new JSONObject();
                for (Map.Entry<String, String> row : block.getValue().entrySet()) rows.put(row.getKey(), row.getValue());
                blocks.put(block.getKey(), rows);
            }
            report.put("sections", blocks);
            report.put("provenance", "Android public APIs; user-entered values are labeled; unsupported counters remain unavailable");
            return report.toString(2);
        } catch (Exception e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }
}
