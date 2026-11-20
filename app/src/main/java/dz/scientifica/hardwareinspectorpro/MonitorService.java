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
 * Optional foreground monitoring for OPPO/Android.
 *
 * Uses elapsedRealtime() for durations (includes screen-off deep sleep) and
 * transition broadcasts for screen and battery state. No inference of any
 * history before monitoring. Gaps in observed states are recorded separately.
 */
public final class MonitorService extends Service {
    public static final String PREFS = "inspector_metrics";
    public static final String STOP = "dz.scientifica.hardwareinspectorpro.STOP";
    private static final String CHANNEL = "inspector_monitor";
    private static final long TICK_MS = 15_000L;
    private static final long MAX_CONFIDENT_STATE_INTERVAL_MS = 120_000L;
    private static final long MAX_RECONCILABLE_INTERRUPTION_MS = 24 * 60 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean screenOn;
    private boolean charging;
    private boolean registered;
    private boolean monitoring;
    private boolean requestedStop;
    private long lastElapsed;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!monitoring || intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (!screenOn) {
                    checkpoint();
                    screenOn = true;
                    EventJournal.append(MonitorService.this, EventJournal.SCREEN_ON,
                            "system_broadcast", screenOn, charging, 0, "");
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (screenOn) {
                    checkpoint();
                    screenOn = false;
                    EventJournal.append(MonitorService.this, EventJournal.SCREEN_OFF,
                            "system_broadcast", screenOn, charging, 0, "");
                }
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                boolean nowCharging = isCharging(intent);
                if (charging != nowCharging) {
                    checkpoint();
                    charging = nowCharging;
                    EventJournal.append(MonitorService.this,
                            charging ? EventJournal.CHARGING_START : EventJournal.CHARGING_END,
                            "system_broadcast", screenOn, charging, 0, "");
                }
            }
        }
    };

    private final Runnable timer = new Runnable() {
        @Override public void run() {
            if (!monitoring) return;
            checkpoint();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private static boolean isCharging(Intent battery) {
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                == BatteryManager.BATTERY_STATUS_CHARGING;
    }

    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.contains("first_seen")) {
            p.edit().putLong("first_seen", System.currentTimeMillis()).commit();
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        screenOn = pm != null && pm.isInteractive();
        charging = isCharging(registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(receiver, filter);
        registered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            requestedStop = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        notification();
        if (!monitoring) beginSession();
        return START_NOT_STICKY;
    }

    private void beginSession() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();

        // A previous session may have been killed without onDestroy(). The
        // missing interval is UNKNOWN, not counted as screen or charging time.
        if (p.getBoolean("monitor_running", false)) {
            long previousWall = p.getLong("monitor_last_checkpoint_wall", 0);
            long previousElapsed = p.getLong("monitor_last_checkpoint_elapsed", 0);
            long gap = 0;
            String note = "الجلسة السابقة لم تسجل نهاية مؤكدة؛ لا نعيد بناء أحداثها.";
            if (previousWall > 0 && previousElapsed > 0) {
                long wallGap = nowWall - previousWall;
                // Same boot: elapsedRealtime is monotonic. Only count a
                // plausible missing interval with no apparent clock anomaly.
                long elapsedGap = nowElapsed - previousElapsed;
                if (elapsedGap >= 0 && wallGap >= 0
                        && wallGap <= MAX_RECONCILABLE_INTERRUPTION_MS
                        && Math.abs(wallGap - elapsedGap) < 60_000L) {
                    gap = elapsedGap;
                    note = "الفترة منذ آخر نقطة تحقق مسجلة؛ لا تُنسب إلى الشاشة أو الشحن.";
                } else {
                    note = "لا يمكن قياس الفجوة بسبب إعادة الإقلاع أو تغيّر الساعة أو طول الانقطاع.";
                }
            }
            if (gap > 0) {
                p.edit().putLong("unobserved_ms",
                        p.getLong("unobserved_ms", 0) + gap).commit();
            }
            EventJournal.append(this, EventJournal.INTERRUPTED, "session_recovery",
                    screenOn, charging, gap, note);
        }

        monitoring = true;
        requestedStop = false;
        lastElapsed = nowElapsed;
        p.edit().putBoolean("monitor_running", true)
                .putLong("monitor_last_checkpoint_wall", nowWall)
                .putLong("monitor_last_checkpoint_elapsed", nowElapsed)
                .commit();

        EventJournal.append(this, EventJournal.START, "user_action",
                screenOn, charging, 0, "تبدأ قياسات هذه الجلسة من هذه اللحظة.");
        EventJournal.append(this, screenOn ? EventJournal.SCREEN_ON : EventJournal.SCREEN_OFF,
                "initial_state", screenOn, charging, 0, "حالة الشاشة عند بدء الجلسة");
        EventJournal.append(this, charging ? EventJournal.CHARGING_START : EventJournal.CHARGING_END,
                "initial_state", screenOn, charging, 0, "حالة الشحن عند بدء الجلسة");
        handler.removeCallbacks(timer);
        handler.postDelayed(timer, TICK_MS);
    }

    private void checkpoint() {
        if (!monitoring) return;
        long now = SystemClock.elapsedRealtime();
        long delta = now - lastElapsed;
        if (delta <= 0) return;
        lastElapsed = now;

        // When the process remains alive but callbacks are delayed, elapsed
        // time confirms elapsed monitoring session duration. The last observed
        // screen/battery states are not assumed for the entire long interval.
        long stateKnown = Math.min(delta, MAX_CONFIDENT_STATE_INTERVAL_MS);
        long uncertain = delta - stateKnown;

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        e.putLong("monitor_ms", p.getLong("monitor_ms", 0) + delta);
        if (screenOn) e.putLong("screen_ms", p.getLong("screen_ms", 0) + stateKnown);
        if (charging) e.putLong("charging_ms", p.getLong("charging_ms", 0) + stateKnown);
        if (uncertain > 0) {
            e.putLong("unobserved_ms", p.getLong("unobserved_ms", 0) + uncertain);
        }
        e.putLong("monitor_last_checkpoint_wall", System.currentTimeMillis());
        e.putLong("monitor_last_checkpoint_elapsed", now);
        e.commit();

        if (uncertain > 0) {
            EventJournal.append(this, EventJournal.GAP, "delayed_observation",
                    screenOn, charging, uncertain,
                    "تأخر تحديث الخدمة. مدة الجلسة محسوبة؛ حالة الشاشة والشحن غير مؤكدة للفجوة.");
        }
    }

    private void notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "مراقبة عتاد الهاتف", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("تسجيل أحداث الشاشة والشحن بأوقات دقيقة");
            nm.createNotificationChannel(channel);
        }
        PendingIntent open = PendingIntent.getActivity(this, 10,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 11,
                new Intent(this, MonitorService.class).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Hardware Inspector Pro")
                .setContentText("تسجيل أوقات الشاشة والشحن — اضغط لعرض السجل")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "إيقاف المراقبة", stop).build())
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(timer);
        if (monitoring) {
            checkpoint();
            EventJournal.append(this,
                    requestedStop ? EventJournal.STOP : EventJournal.TERMINATED,
                    requestedStop ? "user_action" : "system_lifecycle",
                    screenOn, charging, 0,
                    requestedStop ? "أوقف المستخدم مراقبة الهاتف." :
                            "استُدعيت نهاية الخدمة؛ لا يعني ذلك أن المستخدم طلب الإيقاف.");
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("monitor_running", false).commit();
            monitoring = false;
        }
        if (registered) {
            try { unregisterReceiver(receiver); }
            catch (IllegalArgumentException ignored) {}
            registered = false;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
