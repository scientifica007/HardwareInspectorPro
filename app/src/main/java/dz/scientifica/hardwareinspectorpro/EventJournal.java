package dz.scientifica.hardwareinspectorpro;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A bounded, local event journal. It records observations, not reconstructed history.
 * Entries use wall-clock timestamps for display and elapsedRealtime for ordering
 * during the same boot. Clock changes can affect wall-clock timestamps.
 */
public final class EventJournal {
    private static final String KEY = "monitor_events_v2";
    private static final String PRUNED = "monitor_events_pruned_v2";
    private static final int MAX_EVENTS = 300;
    private static final Object LOCK = new Object();

    public static final String START = "monitor_start";
    public static final String STOP = "monitor_stop";
    public static final String TERMINATED = "service_destroyed";
    public static final String INTERRUPTED = "previous_session_unresolved";
    public static final String SCREEN_ON = "screen_on";
    public static final String SCREEN_OFF = "screen_off";
    public static final String CHARGING_START = "charging_start";
    public static final String CHARGING_END = "charging_end";
    public static final String GAP = "observation_gap";
    public static final String RESTORED = "backup_restored";

    private EventJournal() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                MonitorService.PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray readArray(SharedPreferences prefs) {
        try {
            return new JSONArray(prefs.getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    public static void append(Context context, String event, String origin,
                              boolean screenOn, boolean charging, long uncertainMs,
                              String note) {
        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            JSONArray arr = readArray(prefs);
            JSONObject item = new JSONObject();
            try {
                item.put("event", event);
                item.put("timestampMs", System.currentTimeMillis());
                item.put("elapsedSinceBootMs", SystemClock.elapsedRealtime());
                item.put("source", origin);
                item.put("screenOnObserved", screenOn);
                item.put("chargingObserved", charging);
                item.put("uncertainDurationMs", Math.max(0, uncertainMs));
                if (note != null && !note.isEmpty()) item.put("note", note);
                arr.put(item);
                int removed = Math.max(0, arr.length() - MAX_EVENTS);
                if (removed > 0) {
                    JSONArray trimmed = new JSONArray();
                    for (int i = removed; i < arr.length(); i++) {
                        trimmed.put(arr.optJSONObject(i));
                    }
                    arr = trimmed;
                }
                // commit(), not apply(), keeps the journal on disk before returning.
                prefs.edit()
                        .putString(KEY, arr.toString())
                        .putLong(PRUNED, prefs.getLong(PRUNED, 0) + removed)
                        .commit();
            } catch (Exception ignored) {
                // Android report still works even if storing an event fails.
            }
        }
    }

    /** Restore an exported journal only if this installation has no journal yet. */
    public static void restoreHistory(Context context, JSONArray history, long alreadyPruned) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(context);
            if (readArray(p).length() != 0) {
                throw new IllegalStateException("يوجد سجل أحداث حالي؛ لا يمكن استبداله");
            }
            JSONArray retained = new JSONArray();
            int cut = Math.max(0, history.length() - MAX_EVENTS);
            for (int i = cut; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item != null && item.has("event") && item.has("timestampMs"))
                    retained.put(item);
            }
            if (!p.edit().putString(KEY, retained.toString())
                    .putLong(PRUNED, Math.max(0, alreadyPruned) + cut).commit()) {
                throw new IllegalStateException("تعذر استرجاع سجل الأحداث");
            }
        }
    }

    public static JSONArray events(Context context) {
        synchronized (LOCK) {
            return readArray(prefs(context));
        }
    }

    public static String clock(long wallMs) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
                .format(new Date(wallMs));
    }

    private static String label(String event) {
        switch (event) {
            case START: return "بدء المراقبة";
            case STOP: return "إيقاف بطلب المستخدم";
            case TERMINATED: return "إنهاء خدمة المراقبة";
            case INTERRUPTED: return "جلسة سابقة لم يُرصد إغلاقها";
            case SCREEN_ON: return "إضاءة الشاشة";
            case SCREEN_OFF: return "إطفاء الشاشة";
            case CHARGING_START: return "بدء الشحن";
            case CHARGING_END: return "انتهاء الشحن";
            case GAP: return "فجوة في رصد الحالات";
            case RESTORED: return "استرجاع بيانات من تقرير سابق";
            default: return event;
        }
    }

    public static String recentText(Context context, int limit) {
        JSONArray data = events(context);
        if (data.length() == 0) return "لم تُسجل أحداث بعد. ابدأ المراقبة لإنشاء سجل زمني.";
        StringBuilder out = new StringBuilder();
        int start = Math.max(0, data.length() - Math.max(1, limit));
        for (int i = data.length() - 1; i >= start; i--) {
            JSONObject e = data.optJSONObject(i);
            if (e == null) continue;
            out.append(clock(e.optLong("timestampMs", 0)))
               .append(" — ").append(label(e.optString("event", "")));
            if (e.optLong("uncertainDurationMs", 0) > 0) {
                out.append("\nفترة غير مؤكدة: ")
                   .append(HardwareReport.durationSeconds(e.optLong("uncertainDurationMs", 0)));
            }
            String note = e.optString("note", "");
            if (!note.isEmpty()) out.append("\n").append(note);
            out.append("\n\n");
        }
        return out.toString().trim();
    }

    public static JSONObject json(Context context) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(context);
            JSONObject section = new JSONObject();
            try {
                section.put("schema", "monitoring-event-journal-v2");
                section.put("events", readArray(p));
                section.put("retainedEventLimit", MAX_EVENTS);
                section.put("olderEventsDiscarded", p.getLong(PRUNED, 0));
                section.put("currentlyMarkedRunning", p.getBoolean("monitor_running", false));
                section.put("lastCheckpointWallMs", p.getLong("monitor_last_checkpoint_wall", 0));
                section.put("lastCheckpointElapsedMs", p.getLong("monitor_last_checkpoint_elapsed", 0));
                section.put("monitorMs", p.getLong("monitor_ms", 0));
                section.put("screenOnMs", p.getLong("screen_ms", 0));
                section.put("chargingMs", p.getLong("charging_ms", 0));
                section.put("uncertainStateMs", p.getLong("unobserved_ms", 0));
                section.put("notes", "Events are observed since logging was enabled. "
                        + "Timestamps are device wall-clock seconds and may change with clock settings; "
                        + "durations use elapsedRealtime. Screen and charging times are not "
                        + "reconstructed across unknown intervals.");
            } catch (Exception ignored) {}
            return section;
        }
    }
}
