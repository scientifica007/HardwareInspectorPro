package dz.scientifica.hardwareinspectorpro;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 101;
    private static final int REQUEST_MIC = 102;
    private static final int REQUEST_NOTIFY = 103;
    private static final int REQUEST_SAVE = 104;
    private static final int REQUEST_BT = 105;
    private static final int BG = Color.rgb(10, 17, 32);
    private static final int CARD = Color.rgb(22, 32, 53);
    private static final int ACCENT = Color.rgb(56, 189, 248);
    private static final int MUTED = Color.rgb(170, 187, 206);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout reportContainer;
    private TextView testOutput;
    private TextView ageInfo;
    private TextView monitorInfo;
    private HardwareReport latest;
    private String exportJson;
    private SensorManager activeSensors;
    private SensorEventListener activeListener;
    private LocationManager activeLocation;
    private LocationListener activeLocationListener;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE)
                .edit().putLong("first_seen",
                        getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE)
                                .getLong("first_seen", System.currentTimeMillis())).apply();
        buildInterface();
        refresh();
    }

    private static GradientDrawable rounded(int color, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(22);
        d.setStroke(1, border);
        return d;
    }

    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }

    private TextView text(String label, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(2), dp(5), dp(2), dp(5));
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return v;
    }

    private void addSpace(LinearLayout p, int dpHeight) {
        View v = new View(this);
        p.addView(v, new LinearLayout.LayoutParams(1, dp(dpHeight)));
    }

    private void box(LinearLayout parent, View content) {
        LinearLayout border = vertical();
        border.setBackground(rounded(CARD, Color.rgb(42, 59, 85)));
        border.setPadding(dp(14), dp(12), dp(14), dp(12));
        border.addView(content);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(10));
        parent.addView(border, p);
    }

    private Button button(String title, int color, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(13);
        b.setTextColor(color == ACCENT ? BG : Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(rounded(color, color));
        b.setPadding(dp(5), dp(5), dp(5), dp(5));
        b.setOnClickListener(click);
        return b;
    }

    private void buttons(LinearLayout parent, Button one, Button two) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(52), 1);
        a.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(one, a);
        row.addView(two, a);
        parent.addView(row);
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(12), dp(18), dp(12), dp(28));
        scroll.addView(root);

        root.addView(text("Hardware Inspector Pro", 25, ACCENT, true));
        root.addView(text("فاحص عتاد Android الأصلي • OPPO A12 وأجهزة أخرى", 13, MUTED, false));
        root.addView(text("القياسات الفعلية منفصلة عن التقديرات والبيانات التي لا يتيحها Android.", 12, Color.rgb(236, 202, 129), false));
        addSpace(root, 10);

        LinearLayout controls = vertical();
        buttons(controls,
                button("تحديث جميع القراءات", ACCENT, v -> refresh()),
                button("حفظ تقرير JSON", Color.rgb(100, 89, 180), v -> export()));
        buttons(controls,
                button("نسخ التقرير", Color.rgb(40, 113, 145), v -> copy()),
                button("البوصلة والجيروسكوب", Color.rgb(42, 115, 88), v -> startActivity(new Intent(this, InstrumentsActivity.class))));
        box(root, controls);

        LinearLayout ageBlock = vertical();
        ageBlock.addView(text("عدادات استعمال الهاتف وتاريخ الرصد", 17, Color.WHITE, true));
        ageInfo = text("", 13, MUTED, false);
        ageBlock.addView(ageInfo);
        monitorInfo = text("", 13, MUTED, false);
        ageBlock.addView(monitorInfo);
        buttons(ageBlock,
                button("بدء مراقبة مستمرة", Color.rgb(40, 126, 91), v -> startMonitor()),
                button("إيقاف المراقبة", Color.rgb(150, 67, 73), v -> stopMonitor()));
        ageBlock.addView(text("لا تحتاج إدخال تاريخ يدوي. يُقرأ وقت الإقلاع الحقيقي وتُجمع أزمنة الشاشة والشحن منذ بدء المراقبة فقط. قد يوقف ColorOS المراقبة في الخلفية.", 12, Color.rgb(236, 202, 129), false));
        box(root, ageBlock);

        LinearLayout testBlock = vertical();
        testBlock.addView(text("اختبارات عملية عند الطلب", 17, Color.WHITE, true));
        buttons(testBlock, button("البوصلة الحية + الجيروسكوب", Color.rgb(40, 113, 145),
                v -> startActivity(new Intent(this, InstrumentsActivity.class))),
                button("اللمس المتعدد", Color.rgb(40, 113, 145), v -> testTouch()));
        buttons(testBlock, button("المستشعرات الحية", Color.rgb(40, 113, 145), v -> testSensors()),
                button("اللمس المتعدد", Color.rgb(40, 113, 145), v -> testTouch()));
        buttons(testBlock, button("اهتزاز", Color.rgb(40, 113, 145), v -> vibrate()),
                button("صوت السماعة", Color.rgb(40, 113, 145), v -> speaker()));
        buttons(testBlock, button("الميكروفون (dBFS)", Color.rgb(40, 113, 145), v -> microphone()),
                button("GPS مباشر", Color.rgb(40, 113, 145), v -> location()));
        buttons(testBlock, button("فتح الكاميرا", Color.rgb(40, 113, 145), v -> camera()),
                button("صلاحية Bluetooth", Color.rgb(40, 113, 145), v -> bluetoothPermission()));
        testOutput = text("اختر اختبارًا؛ الموقع والميكروفون لا يعملان إلا بموافقتك.", 12, MUTED, false);
        testOutput.setTextIsSelectable(true);
        testBlock.addView(testOutput);
        box(root, testBlock);

        reportContainer = vertical();
        root.addView(reportContainer);
        root.addView(text("مصادر البيانات: واجهات Android الرسمية. لا تُرسل معلومات الهاتف إلى خادم.", 12, MUTED, false));
        setContentView(scroll);
    }

    private void refresh() {
        try {
            latest = HardwareReport.capture(this);
            reportContainer.removeAllViews();
            for (Map.Entry<String, LinkedHashMap<String, String>> group : latest.sections().entrySet()) {
                LinearLayout content = vertical();
                content.addView(text(group.getKey(), 17, ACCENT, true));
                for (Map.Entry<String, String> row : group.getValue().entrySet()) {
                    content.addView(text(row.getKey(), 12, MUTED, false));
                    TextView value = text(row.getValue(), 13, Color.WHITE, true);
                    value.setTextIsSelectable(true);
                    content.addView(value);
                    View line = new View(this);
                    line.setBackgroundColor(Color.rgb(41, 54, 77));
                    content.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                box(reportContainer, content);
            }
            displayAge();
        } catch (Exception e) {
            show("تعذر تحديث البيانات: " + e.getMessage());
        }
    }

    private void displayAge() {
        SharedPreferences p = getSharedPreferences(MonitorService.PREFS, MODE_PRIVATE);
        long first = p.getLong("first_seen", 0L);
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
        ageInfo.setText("أول تشغيل رصده التطبيق: " +
                (first <= 0 ? "غير متاح" : format.format(new java.util.Date(first))) +
                "\\nلا يمكن الجزم بتاريخ تصنيع الهاتف أو تشغيله الأول دون سجل موثوق.");
        monitorInfo.setText("مدة المراقبة المسجلة: " + HardwareReport.duration(p.getLong("monitor_ms", 0)) +
                "\\nإضاءة الشاشة أثناء المراقبة: " + HardwareReport.duration(p.getLong("screen_ms", 0)) +
                "\\nمدة الشحن أثناء المراقبة: " + HardwareReport.duration(p.getLong("charging_ms", 0)) +
                "\\nفترات تعذّر رصدها: " + HardwareReport.duration(p.getLong("unobserved_ms", 0)));
    }

    private void startMonitor() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFY);
            return;
        }
        try {
            Intent intent = new Intent(this, MonitorService.class);
            startForegroundService(intent);
            show("بدأت مراقبة الشاشة والشحن. سيظهر إشعار مستمر.");
            handler.postDelayed(this::refresh, 1100);
        } catch (Exception e) { show("تعذر بدء المراقبة: " + e.getMessage()); }
    }

    private void stopMonitor() {
        stopService(new Intent(this, MonitorService.class));
        handler.postDelayed(this::refresh, 350);
        show("طلب إيقاف المراقبة. تبقى السجلات المسجلة محفوظة.");
    }

    private void copy() {
        if (latest == null) refresh();
        if (latest == null) return;
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("Hardware Inspector Pro", latest.asText()));
        show("نُسخ التقرير النصي");
    }

    private void export() {
        refresh();
        if (latest == null) return;
        exportJson = latest.asJson();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "hardware-inspector-report.json");
        startActivityForResult(i, REQUEST_SAVE);
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQUEST_SAVE && result == RESULT_OK && data != null && data.getData() != null && exportJson != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out != null) {
                    out.write(exportJson.getBytes(StandardCharsets.UTF_8));
                    show("حُفظ ملف JSON على الهاتف");
                }
            } catch (Exception e) { show("تعذر حفظ الملف: " + e.getMessage()); }
        }
    }

    private void testSensors() {
        if (activeSensors != null && activeListener != null) activeSensors.unregisterListener(activeListener);
        activeSensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (activeSensors == null) { show("مستشعرات Android غير متاحة"); return; }
        final LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        activeListener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                StringBuilder readings = new StringBuilder();
                for (float f : e.values) {
                    if (readings.length() != 0) readings.append(", ");
                    readings.append(String.format(Locale.US, "%.2f", f));
                }
                seen.put(e.sensor.getName(), readings.toString() + " (وحدات المستشعر)");
                StringBuilder display = new StringBuilder("بيانات حية لمدة 5 ثوانٍ:\n");
                for (Map.Entry<String, String> row : seen.entrySet())
                    display.append(row.getKey()).append(": ").append(row.getValue()).append("\n");
                testOutput.setText(display.toString());
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        int[] types = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_LIGHT,
                Sensor.TYPE_PROXIMITY, Sensor.TYPE_MAGNETIC_FIELD};
        int count = 0;
        for (int type : types) {
            Sensor sensor = activeSensors.getDefaultSensor(type);
            if (sensor != null && activeSensors.registerListener(activeListener, sensor, SensorManager.SENSOR_DELAY_NORMAL))
                count++;
        }
        if (count == 0) testOutput.setText("هذه المستشعرات غير متاحة أو تعذر تسجيلها");
        SensorManager localManager = activeSensors;
        SensorEventListener localListener = activeListener;
        handler.postDelayed(() -> {
            localManager.unregisterListener(localListener);
            if (activeListener == localListener) { activeListener = null; activeSensors = null; }
        }, 5000);
    }

    private void testTouch() {
        Dialog d = new Dialog(this);
        TextView board = text("المس الشاشة بإصبع أو أكثر. اضغط رجوع للإنهاء.", 18, Color.WHITE, true);
        board.setBackgroundColor(Color.rgb(14, 29, 53));
        board.setGravity(Gravity.CENTER);
        board.setOnTouchListener((v, ev) -> {
            int count = (ev.getActionMasked() == MotionEvent.ACTION_UP ||
                    ev.getActionMasked() == MotionEvent.ACTION_CANCEL) ? 0 : ev.getPointerCount();
            StringBuilder t = new StringBuilder("عدد نقاط اللمس: " + count);
            for (int j = 0; j < count; j++)
                t.append("\n").append(j+1).append(": ")
                        .append((int)ev.getX(j)).append(" , ").append((int)ev.getY(j));
            board.setText(t.toString());
            return true;
        });
        d.setContentView(board);
        if (d.getWindow() != null) d.getWindow().setLayout(-1, dp(420));
        d.show();
        if (d.getWindow() != null) d.getWindow().setLayout(-1, dp(420));
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) { testOutput.setText("محرك الاهتزاز غير معلن"); return; }
            v.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE));
            testOutput.setText("أرسلت إشارة اهتزاز. تأكيد الإحساس به يحتاج ملاحظتك.");
        } catch (Exception e) { show("تعذر الاهتزاز: " + e.getMessage()); }
    }

    private void speaker() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 350);
            handler.postDelayed(tone::release, 700);
            testOutput.setText("تم إرسال نغمة اختبار إلى مخرج الصوت؛ أكد السماع بنفسك.");
        } catch (Exception e) { show("اختبار الصوت لم ينجح: " + e.getMessage()); }
    }

    private void microphone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        testOutput.setText("اختبار الميكروفون لثانية واحدة؛ لا يُحفظ التسجيل.");
        new Thread(() -> {
            AudioRecord recorder = null;
            try {
                int rate = 16000;
                int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) throw new IllegalStateException("غير مدعوم");
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, rate * 2));
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("لم يُهيأ الميكروفون");
                recorder.startRecording();
                short[] samples = new short[rate];
                int position = 0;
                while (position < samples.length) {
                    int read = recorder.read(samples, position, samples.length - position);
                    if (read <= 0) break;
                    position += read;
                }
                double power = 0;
                for (int i = 0; i < position; i++) power += (double)samples[i] * samples[i];
                double rms = position == 0 ? 0 : Math.sqrt(power / position) / 32768.0;
                double dbfs = rms <= 0 ? -120.0 : 20.0 * Math.log10(rms);
                final String result = "الميكروفون: " + position + " عينة • " +
                        String.format(Locale.US, "%.1f dBFS", dbfs) +
                        " (مستوى رقمي نسبي، ليس dB SPL فيزيائيًا)";
                runOnUiThread(() -> testOutput.setText(result));
            } catch (Exception e) {
                final String msg = "فشل اختبار الميكروفون: " + e.getMessage();
                runOnUiThread(() -> testOutput.setText(msg));
            } finally {
                if (recorder != null) {
                    try { recorder.stop(); } catch (Exception ignored) {}
                    recorder.release();
                }
            }
        }).start();
    }

    private void location() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        activeLocation = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (activeLocation == null) { testOutput.setText("خدمة الموقع غير متاحة"); return; }
        try {
            String provider = activeLocation.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (!activeLocation.isProviderEnabled(provider)) {
                testOutput.setText("فعّل الموقع من إعدادات الهاتف وأعد المحاولة");
                return;
            }
            testOutput.setText("يجري طلب موقع جديد؛ قد يحتاج إلى مكان مفتوح. لا يُحفظ الموقع في التقرير.");
            activeLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    testOutput.setText(String.format(Locale.US,
                            "المصدر: %s\nخط العرض: %.6f\nخط الطول: %.6f\nالدقة المعلنة: ±%.0f m",
                            loc.getProvider(), loc.getLatitude(), loc.getLongitude(), loc.getAccuracy()));
                    closeLocation();
                }
                @Override public void onStatusChanged(String p, int status, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            LocationListener listening = activeLocationListener;
            activeLocation.requestSingleUpdate(provider, listening, Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (activeLocationListener == listening) {
                    closeLocation();
                    testOutput.setText("انتهت مهلة 20 ثانية دون موقع جديد. جرّب في مكان مفتوح.");
                }
            }, 20000);
        } catch (Exception e) { testOutput.setText("تعذر اختبار GPS: " + e.getMessage()); closeLocation(); }
    }

    private void closeLocation() {
        if (activeLocation != null && activeLocationListener != null) {
            try { activeLocation.removeUpdates(activeLocationListener); } catch (Exception ignored) {}
        }
        activeLocationListener = null;
        activeLocation = null;
    }

    private void camera() {
        try {
            Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            if (i.resolveActivity(getPackageManager()) == null) {
                testOutput.setText("لا يوجد تطبيق كاميرا يمكن فتحه");
                return;
            }
            startActivity(i);
            testOutput.setText("فُتح تطبيق الكاميرا لاختبارها بصريًا. لم تُلتقط صورة تلقائيًا.");
        } catch (Exception e) { testOutput.setText("تعذّر فتح الكاميرا: " + e.getMessage()); }
    }

    private void bluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
            else refresh();
        } else {
            show("في Android 9 لا يحتاج فحص حالة Bluetooth إلى إذن جديد");
            refresh();
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] grants) {
        super.onRequestPermissionsResult(code, p, grants);
        if (grants.length == 0 || grants[0] != PackageManager.PERMISSION_GRANTED) {
            show("لم تمنح الإذن؛ لن يتم تنفيذ الاختبار");
            return;
        }
        if (code == REQUEST_MIC) microphone();
        if (code == REQUEST_LOCATION) { refresh(); location(); }
        if (code == REQUEST_NOTIFY) startMonitor();
        if (code == REQUEST_BT) refresh();
    }

    private void show(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() { super.onResume(); if (reportContainer != null) refresh(); }

    @Override protected void onDestroy() {
        if (activeSensors != null && activeListener != null) activeSensors.unregisterListener(activeListener);
        closeLocation();
        super.onDestroy();
    }
}
