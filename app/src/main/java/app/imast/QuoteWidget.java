package app.imast;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * Виджет: цитата дня, а утром и вечером на час — первый вопрос дневника с кнопкой «Ответить».
 * Данные (цитаты, вопросы, время) передаёт страница через NativeBridge.
 */
public class QuoteWidget extends AppWidgetProvider {
    static final String PREFS = "imast_widget";
    private static final String A_NEXT = "app.imast.W_NEXT";
    private static final String A_LIKE = "app.imast.W_LIKE";
    private static final String A_DISLIKE = "app.imast.W_DISLIKE";
    private static final String A_TICK = "app.imast.W_TICK";
    private static final int WINDOW_MIN = 60;

    private static final int GOLD = 0xFFD4AE5A;
    private static final int DIM = 0x66EDE6D6;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        updateAll(c);
    }

    @Override
    public void onEnabled(Context c) {
        updateAll(c);
    }

    @Override
    public void onDisabled(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(tickIntent(c));
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        String a = intent.getAction();
        if (a == null) return;
        SharedPreferences p = prefs(c);
        switch (a) {
            case A_NEXT: {
                int n = count(p);
                if (n > 1) {
                    int cur = p.getInt("qIdx", 0), next;
                    Random r = new Random();
                    do { next = r.nextInt(n); } while (next == cur);
                    p.edit().putInt("qIdx", next).putString("qDate", today()).apply();
                }
                updateAll(c);
                break;
            }
            case A_LIKE:
            case A_DISLIKE:
                vote(p, p.getInt("qIdx", 0), A_LIKE.equals(a) ? 1 : -1);
                updateAll(c);
                break;
            case A_TICK:
                updateAll(c);
                break;
            default:
                break;
        }
    }

    // ---------- отрисовка ----------

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, QuoteWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews v = build(c);
        for (int id : ids) m.updateAppWidget(id, v);
        scheduleNext(c);
    }

    private static RemoteViews build(Context c) {
        SharedPreferences p = prefs(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_quote);
        String day = today();
        int now = nowMin();
        int wm = p.getInt("wm", 8 * 60), we = p.getInt("we", 20 * 60 + 30);
        boolean doneM = day.equals(p.getString("doneM", "")), doneE = day.equals(p.getString("doneE", ""));

        String sess = null;
        if (inWindow(now, wm) && !doneM) sess = "m";
        else if (inWindow(now, we) && !doneE) sess = "e";

        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 100, open, flags());

        if (sess != null) {
            // режим вопроса
            String q = p.getString(sess.equals("m") ? "qm" : "qe", sess.equals("m") ? "Что сегодня главное?" : "Что получилось?");
            v.setTextViewText(R.id.label, (sess.equals("m") ? "УТРО" : "ВЕЧЕР") + " · ВОПРОС 1 ИЗ 3");
            v.setTextViewText(R.id.text, q);
            v.setTextViewText(R.id.author, "до " + hhmm(sess.equals("m") ? wm + WINDOW_MIN : we + WINDOW_MIN));
            v.setViewVisibility(R.id.like, View.GONE);
            v.setViewVisibility(R.id.dislike, View.GONE);
            v.setViewVisibility(R.id.next, View.GONE);
            v.setViewVisibility(R.id.answer, View.VISIBLE);

            Intent ans = new Intent(c, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("sess", sess);
            PendingIntent ansPi = PendingIntent.getActivity(c, sess.equals("m") ? 101 : 102, ans, flags());
            v.setOnClickPendingIntent(R.id.answer, ansPi);
            v.setOnClickPendingIntent(R.id.text, ansPi);
            v.setOnClickPendingIntent(R.id.author, ansPi);
            return v;
        }

        // режим цитаты
        int n = count(p);
        String label = "ЦИТАТА ДНЯ";
        if (doneE) label = "ВЕЧЕР ЗАПИСАН ✓";
        else if (doneM) label = "УТРО ЗАПИСАНО ✓";
        v.setTextViewText(R.id.label, label);
        v.setViewVisibility(R.id.answer, View.GONE);

        if (n == 0) {
            v.setTextViewText(R.id.text, "Откройте Imast, чтобы загрузить цитаты.");
            v.setTextViewText(R.id.author, "Imast");
            v.setViewVisibility(R.id.like, View.GONE);
            v.setViewVisibility(R.id.dislike, View.GONE);
            v.setViewVisibility(R.id.next, View.GONE);
            v.setOnClickPendingIntent(R.id.text, openPi);
            v.setOnClickPendingIntent(R.id.author, openPi);
            return v;
        }

        if (!day.equals(p.getString("qDate", ""))) {
            int idx = Math.abs((day.hashCode() * 31) % n);
            p.edit().putInt("qIdx", idx).putString("qDate", day).apply();
        }
        int idx = Math.min(p.getInt("qIdx", 0), n - 1);
        try {
            JSONObject q = new JSONArray(p.getString("quotes", "[]")).getJSONObject(idx);
            v.setTextViewText(R.id.text, "«" + q.optString("t") + "»");
            v.setTextViewText(R.id.author, q.optString("a"));
        } catch (Exception e) {
            v.setTextViewText(R.id.text, "Откройте Imast, чтобы загрузить цитаты.");
        }
        int vote = votes(p).optInt(String.valueOf(idx), 0);
        v.setViewVisibility(R.id.like, View.VISIBLE);
        v.setViewVisibility(R.id.dislike, View.VISIBLE);
        v.setViewVisibility(R.id.next, n > 1 ? View.VISIBLE : View.GONE);
        v.setInt(R.id.like, "setBackgroundColor", vote == 1 ? 0x33C9A24A : 0x00000000);
        v.setInt(R.id.dislike, "setBackgroundColor", vote == -1 ? 0x33C9A24A : 0x00000000);
        v.setTextColor(R.id.like, vote == -1 ? DIM : GOLD);
        v.setTextColor(R.id.dislike, vote == 1 ? DIM : GOLD);

        v.setOnClickPendingIntent(R.id.text, self(c, A_NEXT, 1));
        v.setOnClickPendingIntent(R.id.next, self(c, A_NEXT, 4));
        v.setOnClickPendingIntent(R.id.like, self(c, A_LIKE, 2));
        v.setOnClickPendingIntent(R.id.dislike, self(c, A_DISLIKE, 3));
        v.setOnClickPendingIntent(R.id.author, openPi);
        return v;
    }

    // ---------- расписание: смена режимов и цитата в полночь ----------

    static void scheduleNext(Context c) {
        SharedPreferences p = prefs(c);
        int wm = p.getInt("wm", 8 * 60), we = p.getInt("we", 20 * 60 + 30);
        int now = nowMin();
        int[] points = {wm, wm + WINDOW_MIN, we, we + WINDOW_MIN, 24 * 60};
        int best = 24 * 60 + wm;
        for (int t : points) if (t > now && t < best) best = t;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 1);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MINUTE, best);

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = tickIntent(c);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }

    // ---------- данные от страницы ----------

    static void setData(Context c, String quotesJson, String qm, String qe) {
        prefs(c).edit().putString("quotes", quotesJson).putString("qm", qm).putString("qe", qe).apply();
        updateAll(c);
    }

    static void setTimes(Context c, int wm, int we) {
        prefs(c).edit().putInt("wm", wm).putInt("we", we).apply();
        updateAll(c);
    }

    static void markAnswered(Context c, String sess, String date) {
        prefs(c).edit().putString("m".equals(sess) ? "doneM" : "doneE", date).apply();
        updateAll(c);
    }

    static String votesJson(Context c) {
        return votes(prefs(c)).toString();
    }

    static void setVotes(Context c, String json) {
        prefs(c).edit().putString("votes", json).apply();
        updateAll(c);
    }

    // ---------- вспомогательное ----------

    private static void vote(SharedPreferences p, int idx, int val) {
        JSONObject v = votes(p);
        try {
            String k = String.valueOf(idx);
            if (v.optInt(k, 0) == val) v.remove(k); else v.put(k, val);
        } catch (Exception ignored) {}
        p.edit().putString("votes", v.toString()).apply();
    }

    private static JSONObject votes(SharedPreferences p) {
        try { return new JSONObject(p.getString("votes", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    private static int count(SharedPreferences p) {
        try { return new JSONArray(p.getString("quotes", "[]")).length(); } catch (Exception e) { return 0; }
    }

    private static boolean inWindow(int now, int start) {
        return now >= start && now < start + WINDOW_MIN;
    }

    private static int nowMin() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
    }

    private static String hhmm(int min) {
        min = min % (24 * 60);
        return String.format(Locale.US, "%d:%02d", min / 60, min % 60);
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    private static PendingIntent self(Context c, String action, int code) {
        Intent i = new Intent(c, QuoteWidget.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, flags());
    }

    private static PendingIntent tickIntent(Context c) {
        Intent i = new Intent(c, QuoteWidget.class).setAction(A_TICK);
        return PendingIntent.getBroadcast(c, 9, i, flags());
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
