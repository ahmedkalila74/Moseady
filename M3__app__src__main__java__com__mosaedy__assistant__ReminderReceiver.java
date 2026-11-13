package com.mosaedy.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id");
        if (id == null || id.isEmpty()) return;
        if (intent.getBooleanExtra("snoozed", false)) {
            ReminderNotifier.show(context, id, intent.getStringExtra("title"), intent.getStringExtra("mode"));
            return;
        }
        JSONObject record = ReminderStore.find(context, id);
        if (record == null) return; // deleted before scheduled firing
        long due = record.optLong("dueAt");
        if (due > System.currentTimeMillis() + 1500) return; // stale alarm
        ReminderNotifier.show(context, id, record.optString("title"), record.optString("mode", "alarm"));
        String repeat = record.optString("repeat", "none");
        if (repeat.equals("daily") || repeat.equals("weekly")) {
            long next = ReminderScheduler.nextOccurrence(due, repeat, System.currentTimeMillis());
            ReminderStore.updateTime(context, id, next);
            try { record.put("dueAt", next); } catch (Exception ignored) { }
            ReminderScheduler.schedule(context, record);
        } else {
            ReminderStore.remove(context, id);
        }
    }
}
