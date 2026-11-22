package app.imast;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Шаги за сегодня по датчику телефона TYPE_STEP_COUNTER.
 * Датчик отдаёт шаги с последней перезагрузки, поэтому храним «базу» на начало дня.
 * Данные лежат только на телефоне, отдельно от дневника.
 */
final class Steps implements SensorEventListener {
    private final Context c;
    private final SharedPreferences p;
    private boolean on = false;

    Steps(Context ctx) {
        c = ctx.getApplicationContext();
        p = c.getSharedPreferences("imast_steps", Context.MODE_PRIVATE);
    }

    private SensorManager sm() { return (SensorManager) c.getSystemService(Context.SENSOR_SERVICE); }

    boolean available() {
        SensorManager m = sm();
        return m != null && m.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    void start() {
        if (on) return;
        SensorManager m = sm();
        if (m == null) return;
        Sensor s = m.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (s == null) return;
        try { on = m.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL); } catch (Exception ignored) { on = false; }
    }

    void stop() {
        if (!on) return;
        SensorManager m = sm();
        if (m != null) m.unregisterListener(this);
        on = false;
    }

    private static String fmt(Date d) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d); }
    private static String day() { return fmt(new Date()); }
    private static String dayShift(int n) {
        Calendar k = Calendar.getInstance();
        k.add(Calendar.DAY_OF_YEAR, n);
        return fmt(k.getTime());
    }

    @Override public void onSensorChanged(SensorEvent e) { record((long) e.values[0]); }
    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }

    synchronized void record(long v) {
        String d = day();
        String lastDate = p.getString("lastDate", null);
        String baseDate = p.getString("baseDate", null);
        long lastVal = p.getLong("lastVal", -1);
        long base = p.getLong("base", 0);
        boolean newDay = !d.equals(baseDate);
        if (lastVal >= 0 && v < lastVal) {
            // телефон перезагрузился, счётчик начался с нуля: сегодняшние шаги сохраняем
            base = newDay ? 0 : base - lastVal;
        } else if (newDay) {
            // новый день: считаем от последнего вчерашнего показания, иначе от текущего
            base = (lastVal >= 0 && dayShift(-1).equals(lastDate)) ? lastVal : v;
        }
        long today = Math.max(0, v - base);
        p.edit().putLong("lastVal", v).putString("lastDate", d)
                .putLong("base", base).putString("baseDate", d)
                .putLong("day:" + d, today).apply();
    }

    long today() { return p.getLong("day:" + day(), 0); }

    /** Шаги по дням за последние n дней: {"2026-11-22": 5400, ...} */
    String history(int n) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i < n; i++) {
                String d = dayShift(-i);
                long v = p.getLong("day:" + d, -1);
                if (v >= 0) o.put(d, v);
            }
        } catch (Exception ignored) { }
        return o.toString();
    }
}
