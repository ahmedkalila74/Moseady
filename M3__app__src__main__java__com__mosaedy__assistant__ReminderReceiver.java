package com.mosaedy.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id");
        if (id == null || id.isEmpty()) return;
        JSONObject record = ReminderStore.find(context, id);
        if (record == null) return;
        boolean snoozed = intent.getBooleanExtra("snoozed", false);
        String repeat = record.optString("repeat", "none");
        long due = record.optLong("dueAt");
        if (!snoozed && due > System.currentTimeMillis() + 1500) return;
        String mode = record.optString("mode", "alarm");
        if (!"alarm".equals(mode) && !ReminderNotifier.allowed(context)) {
            if ("none".equals(repeat)) ReminderStore.archive(context, id, "missed");
            else {
                long next = ReminderScheduler.nextOccurrence(due, repeat, System.currentTimeMillis());
                ReminderStore.updateTime(context, id, next, "upcoming");
                try { record.put("dueAt", next); } catch (Exception ignored) { }
                ReminderScheduler.schedule(context, record);
            }
            return;
        }
        ReminderStore.setStatus(context, id, "triggered");
        ReminderNotifier.show(context, id, record.optString("title"), mode);
        if (snoozed) {
            if (!"alarm".equals(mode) && "none".equals(repeat)) ReminderStore.archive(context, id, "completed");
            else if (!"alarm".equals(mode)) ReminderStore.setStatus(context, id, "upcoming");
            return;
        }
        if (repeat.equals("daily") || repeat.equals("weekly") || repeat.equals("monthly")) {
            long next = ReminderScheduler.nextOccurrence(due, repeat, System.currentTimeMillis());
            ReminderStore.updateTime(context, id, next, "upcoming");
            try { record.put("dueAt", next); } catch (Exception ignored) { }
            ReminderScheduler.schedule(context, record);
        } else if (!"alarm".equals(record.optString("mode", "alarm"))) {
            ReminderStore.archive(context, id, "completed");
        }
    }
}
