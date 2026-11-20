package dz.scientifica.hardwareinspectorpro;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Live, native compass + gyroscope. The compass is magnetic-North based;
 * gyroscope is explicitly flagged as vendor virtual when its identity says so.
 */
public final class InstrumentsActivity extends Activity implements SensorEventListener {
    private static final int BG = Color.rgb(10, 17, 32);
    private static final int CARD = Color.rgb(22, 32, 53);
    private static final int LIGHT = Color.rgb(238, 243, 251);
    private static final int BLUE = Color.rgb(56, 189, 248);
    private static final int MUTED = Color.rgb(166, 185, 205);

    private SensorManager manager;
    private Sensor accelerometer, magnetometer, gyro, stepCounter;
    private final float[] gravity = new float[3];
    private final float[] magnetic = new float[3];
    private boolean hasGravity, hasMagnetic, hasHeading;
    private float heading = 0f;
    private float pitch = 0f;
    private float roll = 0f;
    private float gyroX, gyroY, gyroZ;
    private long lastUi = 0;
    private CompassDial dial;
    private TextView compassText, gyroText, sensorText, stepsText, accuracyText;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager != null) {
            accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            stepCounter = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(16), dp(16), dp(16), dp(22));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);
        TextView title = label("البوصلة والجيروسكوب", 24, BLUE, true);
        root.addView(title);
        root.addView(label("قياسات مباشرة من مستشعرات هذا الهاتف، وليست صورة تجريبية.", 13, MUTED, false));

        dial = new CompassDial(this);
        root.addView(dial, new LinearLayout.LayoutParams(-1, dp(280)));
        compassText = label("البوصلة: في انتظار المستشعرات...", 17, LIGHT, true);
        root.addView(compassText);
        accuracyText = label("", 12, MUTED, false);
        root.addView(accuracyText);
        root.addView(label("البوصلة تشير إلى الشمال المغناطيسي، لا الشمال الجغرافي. أمسك الهاتف أفقيًا بعيدًا عن المعادن، وحرّكه على شكل 8 للمعايرة.", 12, MUTED, false));

        root.addView(label("الجيروسكوب — السرعة الزاوية", 20, BLUE, true));
        sensorText = label("", 13, MUTED, false);
        root.addView(sensorText);
        gyroText = label("X: —     Y: —     Z: —", 16, LIGHT, true);
        root.addView(gyroText);
        root.addView(label("وحدة القياس: درجة/ثانية، مع إظهار طبيعة المستشعر الحقيقي أو الافتراضي. الجيروسكوب الافتراضي لا يساوي وجود شريحة مستقلة.", 12, MUTED, false));

        root.addView(label("عداد الخطوات", 18, BLUE, true));
        stepsText = label("جارٍ فحص عداد الخطوات...", 14, LIGHT, false);
        root.addView(stepsText);
        root.addView(label("عدد الخطوات إن توفر هو منذ آخر إقلاع، وليس منذ تصنيع الهاتف. في Android 10+ قد يتطلب إذن النشاط البدني.", 12, MUTED, false));

        root.addView(label("قابلية القراءة", 17, BLUE, true));
        String accName = accelerometer == null ? "غير موجود" : accelerometer.getName();
        String magName = magnetometer == null ? "غير موجود" : magnetometer.getName();
        root.addView(label("مقياس التسارع: " + accName + "\nالمستشعر المغناطيسي: " + magName, 13, MUTED, false));

        sensorText.setText(gyro == null ? "الجيروسكوب: لا يعلن Android عن مستشعر من هذا النوع" :
                "اسم المستشعر: " + gyro.getName() +
                "\nالمورد: " + gyro.getVendor() +
                "\nالتصنيف: " + (isVirtual(gyro) ?
                    "افتراضي (معلن عن طريق برمجيات الشركة)" :
                    "لم يثبت أنه افتراضي من الاسم أو المورد؛ التحقق الفيزيائي غير متاح") );
        if (stepCounter == null) stepsText.setText("لا يوجد عداد خطوات مُعلن");
        else if (Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission("android.permission.ACTIVITY_RECOGNITION") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            stepsText.setText("عداد الخطوات موجود، لكن Android يتطلب إذن النشاط البدني لعرضه");
        setContentView(scroll);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private TextView label(String t, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(t);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setGravity(Gravity.RIGHT);
        view.setPadding(0, dp(7), 0, dp(7));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static boolean isVirtual(Sensor s) {
        if (s == null) return false;
        String identity = (s.getName() + " " + s.getVendor() + " " +
                s.getStringType()).toLowerCase(Locale.US);
        return identity.contains("virtual") || identity.contains("software")
                || identity.contains("synthetic");
    }

    @Override protected void onResume() {
        super.onResume();
        if (manager == null) return;
        if (accelerometer != null) manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        if (magnetometer != null) manager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        if (gyro != null) manager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
        if (stepCounter != null && (Build.VERSION.SDK_INT < 29 ||
                checkSelfPermission("android.permission.ACTIVITY_RECOGNITION")
                        == android.content.pm.PackageManager.PERMISSION_GRANTED))
            manager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override protected void onPause() {
        if (manager != null) manager.unregisterListener(this);
        super.onPause();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor != null && sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            accuracyText.setText(accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ?
                    "دقة المجال المغناطيسي منخفضة — أبعد الهاتف عن المغناطيس وأعد المعايرة" :
                    accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH ?
                    "دقة المجال المغناطيسي المبلّغ عنها: مرتفعة" :
                    "دقة المجال المغناطيسي المبلّغ عنها: متوسطة أو غير محددة");
        }
    }

    private String cardinal(float angle) {
        String[] names = {"شمال", "شمال شرقي", "شرق", "جنوب شرقي",
                "جنوب", "جنوب غربي", "غرب", "شمال غربي"};
        return names[Math.round(angle / 45f) % 8];
    }

    @Override public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            hasGravity = true;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetic, 0, 3);
            hasMagnetic = true;
        } else if (type == Sensor.TYPE_GYROSCOPE) {
            gyroX = event.values[0];
            gyroY = event.values[1];
            gyroZ = event.values[2];
        } else if (type == Sensor.TYPE_STEP_COUNTER) {
            stepsText.setText(String.format(Locale.US, "خطوات منذ آخر إعادة إقلاع وفق المستشعر: %.0f", event.values[0]));
        }

        if (hasGravity && hasMagnetic) {
            float[] rotation = new float[9];
            float[] inclination = new float[9];
            if (SensorManager.getRotationMatrix(rotation, inclination, gravity, magnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotation, orientation);
                float angle = (float) Math.toDegrees(orientation[0]);
                heading = (angle + 360f) % 360f;
                pitch = (float) Math.toDegrees(orientation[1]);
                roll = (float) Math.toDegrees(orientation[2]);
                hasHeading = true;
            }
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastUi < 90) return;
        lastUi = now;
        if (hasHeading) {
            dial.setHeading(heading);
            compassText.setText(String.format(Locale.US,
                    "الاتجاه: %.0f° • %s\nالميل الطولي: %.1f° • الميل الجانبي: %.1f°",
                    heading, cardinal(heading), pitch, roll));
        } else {
            compassText.setText("تعذرت البوصلة: يلزم تسارع ومجال مغناطيسي موثوقان");
        }
        if (gyro != null) {
            double degrees = 180.0 / Math.PI;
            gyroText.setText(String.format(Locale.US,
                    "X: %+.2f °/s\nY: %+.2f °/s\nZ: %+.2f °/s",
                    gyroX * degrees, gyroY * degrees, gyroZ * degrees));
        }
    }

    private static final class CompassDial extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float degrees = 0;

        CompassDial(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

        void setHeading(float next) { degrees = next; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * .5f, cy = getHeight() * .49f;
            float radius = Math.min(getWidth(), getHeight()) * .40f;
            p.setColor(CARD);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, radius + 21, p);
            p.setColor(Color.rgb(67, 93, 125));
            p.setStrokeWidth(3);
            p.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, radius, p);
            p.setStyle(Paint.Style.FILL);
            for (int a = 0; a < 360; a += 10) {
                double rad = Math.toRadians(a - 90);
                float outer = radius * .95f;
                float inner = radius * (a % 30 == 0 ? .82f : .89f);
                p.setColor(a % 30 == 0 ? LIGHT : MUTED);
                p.setStrokeWidth(a % 30 == 0 ? 3 : 1);
                canvas.drawLine(cx + (float)Math.cos(rad) * inner,
                        cy + (float)Math.sin(rad) * inner,
                        cx + (float)Math.cos(rad) * outer,
                        cy + (float)Math.sin(rad) * outer, p);
            }
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(radius * .20f);
            p.setColor(LIGHT);
            canvas.drawText("N", cx, cy - radius * .66f, p);
            canvas.drawText("S", cx, cy + radius * .77f, p);
            canvas.drawText("E", cx + radius * .69f, cy + radius * .06f, p);
            canvas.drawText("W", cx - radius * .69f, cy + radius * .06f, p);

            // Red arrow always points toward magnetic north relative to the phone.
            canvas.save();
            canvas.rotate(-degrees, cx, cy);
            Path north = new Path();
            north.moveTo(cx, cy - radius * .63f);
            north.lineTo(cx - radius * .075f, cy + radius * .10f);
            north.lineTo(cx + radius * .075f, cy + radius * .10f);
            north.close();
            p.setColor(Color.rgb(249, 91, 91));
            canvas.drawPath(north, p);
            Path south = new Path();
            south.moveTo(cx, cy + radius * .63f);
            south.lineTo(cx - radius * .075f, cy - radius * .10f);
            south.lineTo(cx + radius * .075f, cy - radius * .10f);
            south.close();
            p.setColor(BLUE);
            canvas.drawPath(south, p);
            canvas.restore();

            p.setColor(LIGHT);
            canvas.drawCircle(cx, cy, radius * .09f, p);
            p.setColor(BG);
            canvas.drawCircle(cx, cy, radius * .035f, p);
        }
    }
}
