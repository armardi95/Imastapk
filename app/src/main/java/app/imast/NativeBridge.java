package app.imast;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Мост между страницей и телефоном. В JavaScript доступен как window.ImastNative.
 * Ответы на асинхронные запросы приходят в window.ImastNativeCallback(type, value).
 */
public class NativeBridge {
    private final MainActivity a;

    NativeBridge(MainActivity a) { this.a = a; }

    @JavascriptInterface public String version() { return BuildConfig.VERSION_NAME; }

    // ---------- разрешения ----------

    /** name: notifications | calendar | exactAlarm | biometric | activity */
    @JavascriptInterface
    public boolean hasPermission(String name) {
        switch (name) {
            case "notifications":
                return Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS);
            case "calendar":
                return granted(Manifest.permission.WRITE_CALENDAR) && granted(Manifest.permission.READ_CALENDAR);
            case "exactAlarm":
                if (Build.VERSION.SDK_INT < 31) return true;
                return ((AlarmManager) a.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
            case "biometric":
                return a.biometricAvailable();
            case "activity":
                return a.stepsAllowed();
            default:
                return false;
        }
    }

    /** Показывает системный запрос. Итог придёт в ImastNativeCallback('permission:'+name, true|false). */
    @JavascriptInterface
    public void requestPermission(final String name) {
        a.runOnUiThread(() -> {
            switch (name) {
                case "notifications":
                    if (Build.VERSION.SDK_INT >= 33) {
                        a.askPermissions(name, new String[]{Manifest.permission.POST_NOTIFICATIONS});
                    } else {
                        a.callback("permission:" + name, "true");
                    }
                    break;
                case "calendar":
                    a.askPermissions(name, new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR});
                    break;
                case "exactAlarm":
                    if (Build.VERSION.SDK_INT >= 31 && !hasPermission("exactAlarm")) {
                        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + a.getPackageName()));
                        a.startActivity(i);
                    } else {
                        a.callback("permission:" + name, "true");
                    }
                    break;
                case "activity":
                    if (Build.VERSION.SDK_INT >= 29 && !a.stepsAllowed()) {
                        a.askPermissions(name, new String[]{Manifest.permission.ACTIVITY_RECOGNITION});
                    } else {
                        a.steps.start();
                        a.callback("permission:" + name, "true");
                    }
                    break;
                default:
                    a.callback("permission:" + name, "false");
            }
        });
    }

    @JavascriptInterface
    public void openAppSettings() {
        a.runOnUiThread(() -> a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.getPackageName()))));
    }

    // ---------- напоминания (уведомления) ----------

    /** days: "1,2,3,4,5" (1 = понедельник), пустая строка = каждый день */
    @JavascriptInterface
    public void setReminder(String id, String title, String text, int hour, int minute, String days) {
        Reminders.ensureChannel(a);
        Reminders.set(a, id, title, text, hour, minute, days);
    }

    @JavascriptInterface public void cancelReminder(String id) { Reminders.cancel(a, id); }

    @JavascriptInterface public String listReminders() { return Reminders.list(a); }

    // ---------- будильник в системных часах ----------

    @JavascriptInterface
    public void setAlarmClock(final int hour, final int minute, final String label, final String days) {
        a.runOnUiThread(() -> {
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            ArrayList<Integer> d = calendarDays(days);
            if (!d.isEmpty()) i.putExtra(AlarmClock.EXTRA_DAYS, d);
            safeStart(i);
        });
    }

    // ---------- событие в календаре (открывает календарь для подтверждения) ----------

    @JavascriptInterface
    public void addCalendarEvent(final String title, final String description, final String startDate,
                                 final int hour, final int minute, final int durationMin, final String days) {
        a.runOnUiThread(() -> {
            Calendar c = Calendar.getInstance();
            try {
                String[] p = startDate.split("-");
                c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), hour, minute, 0);
            } catch (Exception e) {
                c.set(Calendar.HOUR_OF_DAY, hour);
                c.set(Calendar.MINUTE, minute);
            }
            long begin = c.getTimeInMillis();
            Intent i = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + durationMin * 60000L)
                    .putExtra(CalendarContract.Events.TITLE, title)
                    .putExtra(CalendarContract.Events.DESCRIPTION, description);
            String rrule = rrule(days);
            if (rrule != null) i.putExtra(CalendarContract.Events.RRULE, rrule);
            safeStart(i);
        });
    }

    // ---------- биометрия ----------

    /** Итог придёт в ImastNativeCallback('auth', 'true'|'false'|'unavailable') */
    @JavascriptInterface
    public void authenticate(final String title) {
        a.runOnUiThread(() -> a.authenticate(title));
    }

    // ---------- обмен данными ----------

    /** Резервная копия: открывает меню «Поделиться» (сохранить в файлы, отправить себе). */
    @JavascriptInterface
    public void shareText(final String text, final String subject) {
        a.runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(Intent.EXTRA_SUBJECT, subject);
            safeStart(Intent.createChooser(i, subject));
        });
    }

    /** Резервная копия файлом: системное окно «Сохранить как». Итог в ImastNativeCallback('saved', true|false). */
    @JavascriptInterface
    public void saveFile(final String name, final String text) {
        a.runOnUiThread(() -> a.saveFile(name, text));
    }

    @JavascriptInterface
    public void vibrate(int ms) {
        Vibrator v = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(ms);
    }

    // ---------- виджет ----------

    /** quotesJson: [{"t":"...","a":"..."}], qm/qe: первые утренний и вечерний вопросы */
    @JavascriptInterface
    public void setWidgetData(String quotesJson, String qm, String qe) { QuoteWidget.setData(a, quotesJson, qm, qe); }

    /** Язык приложения ("ru" | "en"): виджет, уведомления и системные окна говорят на нём же */
    @JavascriptInterface
    public void setLang(String lang) {
        QuoteWidget.setLang(a, lang);
        Reminders.ensureChannel(a);
    }

    /** Начало утреннего и вечернего окна вопросов (каждое длится час) */
    @JavascriptInterface
    public void setWidgetTimes(int mh, int mm, int eh, int em) { QuoteWidget.setTimes(a, mh * 60 + mm, eh * 60 + em); }

    /** sess: m | e, date: yyyy-MM-dd */
    @JavascriptInterface
    public void markAnswered(String sess, String date) { QuoteWidget.markAnswered(a, sess, date); }

    /** Голоса за цитаты, поставленные в виджете: {"индекс": 1 | -1} */
    @JavascriptInterface public String getWidgetVotes() { return QuoteWidget.votesJson(a); }

    @JavascriptInterface public void setWidgetVotes(String json) { QuoteWidget.setVotes(a, json); }

    /** Какую сессию открыть, если приложение запущено кнопкой «Ответить» в виджете. Читается один раз. */
    @JavascriptInterface public String takeLaunch() { return a.takeLaunch(); }

    /** Есть ли виджет на экране */
    @JavascriptInterface
    public boolean hasWidget() {
        int[] ids = android.appwidget.AppWidgetManager.getInstance(a)
                .getAppWidgetIds(new android.content.ComponentName(a, QuoteWidget.class));
        return ids != null && ids.length > 0;
    }

    /** Просит лаунчер добавить виджет на главный экран (Android 8+, если лаунчер умеет) */
    @JavascriptInterface
    public boolean requestPinWidget() {
        if (Build.VERSION.SDK_INT < 26) return false;
        android.appwidget.AppWidgetManager m = a.getSystemService(android.appwidget.AppWidgetManager.class);
        if (m == null || !m.isRequestPinAppWidgetSupported()) return false;
        return m.requestPinAppWidget(new android.content.ComponentName(a, QuoteWidget.class), null, null);
    }

    // ---------- шаги (датчик телефона, данные только на телефоне) ----------

    @JavascriptInterface public boolean stepsAvailable() { return a.steps != null && a.steps.available(); }

    /** Шаги за сегодня; заодно включает датчик, если разрешение уже есть */
    @JavascriptInterface
    public long steps() {
        if (a.steps == null) return 0;
        if (a.stepsAllowed()) a.runOnUiThread(() -> a.steps.start());
        return a.steps.today();
    }

    @JavascriptInterface public String stepsHistory(int days) { return a.steps == null ? "{}" : a.steps.history(Math.max(1, Math.min(days, 400))); }

    // ---------- вспомогательное ----------

    private boolean granted(String p) {
        return a.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void safeStart(Intent i) {
        try {
            a.startActivity(i);
        } catch (Exception e) {
            a.callback("error", "'no_app'");
        }
    }

    private static ArrayList<Integer> calendarDays(String days) {
        ArrayList<Integer> out = new ArrayList<>();
        if (days == null || days.isEmpty()) return out;
        int[] map = {0, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        for (String s : days.split(",")) {
            try { out.add(map[Integer.parseInt(s.trim())]); } catch (Exception ignored) {}
        }
        return out;
    }

    private static String rrule(String days) {
        if (days == null || days.isEmpty()) return "FREQ=DAILY";
        String[] code = {"", "MO", "TU", "WE", "TH", "FR", "SA", "SU"};
        StringBuilder b = new StringBuilder();
        for (String s : days.split(",")) {
            try {
                if (b.length() > 0) b.append(',');
                b.append(code[Integer.parseInt(s.trim())]);
            } catch (Exception ignored) {}
        }
        return b.length() == 0 ? null : "FREQ=WEEKLY;BYDAY=" + b;
    }
}
