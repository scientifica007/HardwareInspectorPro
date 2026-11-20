package dz.scientifica.hardwareinspectorpro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Optional local monitoring. It never pretends to reconstruct use before installation.
 * Each recorded interval is attributed to the observed screen/charging state.
 */
public final class MonitorService extends Service {
    public static final String PREFS = "inspector_metrics";
    public static final String STOP = "dz.scientifica.hardwareinspectorpro.STOP";
    private static final String CHANNEL = "inspector_monitor";
    private static final long MAX_INTERVAL_MS = 120_000L;
    private static final long TICK_MS = 15_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean screenOn = false;
    private boolean charging = false;
    private boolean registered = false;
    private long lastElapsed = 0L;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            tally();
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) screenOn = true;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) screenOn = false;
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        == BatteryManager.BATTERY_STATUS_CHARGING;
            }
        }
    };

    private final Runnable timer = new Runnable() {
        @Override public void run() {
            tally();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.contains("first_seen")) p.edit().putLong("first_seen", System.currentTimeMillis()).apply();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        screenOn = pm != null && pm.isInteractive();
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        charging = battery != null && battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                == BatteryManager.BATTERY_STATUS_CHARGING;
        lastElapsed = SystemClock.elapsedRealtime();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(receiver, filter);
        registered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        notification();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("monitor_running", true).apply();
        handler.removeCallbacks(timer);
        handler.postDelayed(timer, TICK_MS);
        return START_NOT_STICKY;
    }

    private void notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "مراقبة عتاد الهاتف", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("مراقبة اختيارية لساعات الشاشة والشحن منذ بدء المراقبة");
            nm.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 10, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, MonitorService.class).setAction(STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 11, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Hardware Inspector Pro")
                .setContentText("تسجيل أزمنة الشاشة والشحن منذ بدء المراقبة")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(openIntent)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "إيقاف المراقبة", stopIntent).build())
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    private void tally() {
        long now = SystemClock.elapsedRealtime();
        if (lastElapsed == 0) { lastElapsed = now; return; }
        long delta = now - lastElapsed;
        lastElapsed = now;
        if (delta <= 0) return;
        // Avoid converting a long unobserved process suspension into fictitious precision.
        long accepted = Math.min(delta, MAX_INTERVAL_MS);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        e.putLong("monitor_ms", p.getLong("monitor_ms", 0L) + accepted);
        if (screenOn) e.putLong("screen_ms", p.getLong("screen_ms", 0L) + accepted);
        if (charging) e.putLong("charging_ms", p.getLong("charging_ms", 0L) + accepted);
        if (delta > MAX_INTERVAL_MS) e.putLong("unobserved_ms",
                p.getLong("unobserved_ms", 0L) + delta - accepted);
        e.apply();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(timer);
        tally();
        if (registered) {
            try { unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("monitor_running", false).apply();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
