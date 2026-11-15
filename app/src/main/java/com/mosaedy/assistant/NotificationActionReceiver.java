package com.mosaedy.assistant;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONException;
import org.json.JSONObject;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"com.mosaedy.SNOOZE".equals(intent.getAction())) return;
        String id = intent.getStringExtra("id"); if (id == null || id.isEmpty()) return;
        String title = intent.getStringExtra("title");
        int minutes = intent.getIntExtra("minutes", ReminderScheduler.defaultSnoozeMinutes(context));
        // A one-off notification is archived after delivery. Restore it briefly so its Snooze action works.
        JSONObject active = ReminderStore.find(context, id);
        if (active == null) {
            JSONObject archived = ReminderStore.findHistory(context, id);
            if (archived == null || !"completed".equals(archived.optString("status"))) return;
            long finishedAt = archived.optLong("finishedAt", 0L);
            if (finishedAt > 0L && System.currentTimeMillis() - finishedAt > 24L * 60L * 60L * 1000L) return;
            try {
                archived.put("dueAt", System.currentTimeMillis() + Math.max(1, Math.min(180, minutes)) * 60L * 1000L);
                archived.put("status", "upcoming");
                ReminderStore.put(context, archived);
                active = ReminderStore.find(context, id);
            } catch (JSONException e) { return; }
        }
        if (active == null) return;
        String safeTitle = title == null || title.trim().isEmpty() ? active.optString("title", "تذكير") : title;
        ReminderScheduler.snooze(context, id, safeTitle, "notification", minutes);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(id.hashCode());
    }
}
