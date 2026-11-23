package app.imast;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;

/** Повторяющиеся напоминания: хранятся на телефоне, переживают перезагрузку. */
public final class Reminders {
    static final String CHANNEL = "reminders";
    private static final String PREFS = "imast_reminders";

    private Reminders() {}

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            // повторный вызов с тем же id только обновляет название канала на нужном языке
            boolean en = QuoteWidget.en(QuoteWidget.prefs(c));
            NotificationChannel ch = new NotificationChannel(CHANNEL, en ? "Reminders" : "Напоминания", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(en ? "Journal and habits" : "Дневник и привычки");
            nm.createNotificationChannel(ch);
        }
    }

    /** days: строка вида "1,2,3" где 1 = понедельник, 7 = воскресенье. Пусто = каждый день. */
    static void set(Context c, String id, String title, String text, int hour, int minute, String days) {
        try {
            JSONObject o = new JSONObject();
            o.put("title", title);
            o.put("text", text);
            o.put("hour", hour);
            o.put("minute", minute);
            o.put("days", days == null ? "" : days);
            prefs(c).edit().putString(id, o.toString()).apply();
            schedule(c, id, o);
        } catch (Exception ignored) {}
    }

    static void cancel(Context c, String id) {
        prefs(c).edit().remove(id).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(c, id));
    }

    static String list(Context c) {
        JSONObject all = new JSONObject();
        try {
            for (String k : prefs(c).getAll().keySet()) {
                all.put(k, new JSONObject(prefs(c).getString(k, "{}")));
            }
        } catch (Exception ignored) {}
        return all.toString();
    }

    static void rescheduleAll(Context c) {
        for (String k : prefs(c).getAll().keySet()) {
            try {
                schedule(c, k, new JSONObject(prefs(c).getString(k, "{}")));
            } catch (Exception ignored) {}
        }
    }

    static JSONObject get(Context c, String id) {
        try {
            String s = prefs(c).getString(id, null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    static void schedule(Context c, String id, JSONObject o) {
        long when = nextTime(o.optInt("hour"), o.optInt("minute"), o.optString("days", ""));
        if (when <= 0) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(c, id);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact && Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi); // может прийти на несколько минут позже
        } else {
            am.set(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    private static long nextTime(int hour, int minute, String days) {
        Calendar now = Calendar.getInstance();
        for (int add = 0; add <= 7; add++) {
            Calendar t = (Calendar) now.clone();
            t.add(Calendar.DAY_OF_YEAR, add);
            t.set(Calendar.HOUR_OF_DAY, hour);
            t.set(Calendar.MINUTE, minute);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() <= now.getTimeInMillis() + 1000) continue;
            int wd = (t.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1; // 1 = понедельник
            if (days.isEmpty() || ("," + days + ",").contains("," + wd + ",")) return t.getTimeInMillis();
        }
        return -1;
    }

    private static PendingIntent pending(Context c, String id) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("app.imast.REMINDER");
        i.putExtra("id", id);
        return PendingIntent.getBroadcast(c, id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

}
