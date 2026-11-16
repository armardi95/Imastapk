package app.imast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** После перезагрузки телефона или обновления приложения заново ставит напоминания. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Reminders.rescheduleAll(c);
        QuoteWidget.updateAll(c);
    }
}
