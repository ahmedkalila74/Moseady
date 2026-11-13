package com.mosaedy.assistant;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"com.mosaedy.SNOOZE".equals(intent.getAction())) return;
        String id = intent.getStringExtra("id"); if (id == null || id.isEmpty()) return;
        String title = intent.getStringExtra("title");
        int minutes = intent.getIntExtra("minutes", ReminderScheduler.defaultSnoozeMinutes(context));
        ReminderScheduler.snooze(context, id, title == null ? "تذكير" : title, "notification", minutes);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(id.hashCode());
    }
}
