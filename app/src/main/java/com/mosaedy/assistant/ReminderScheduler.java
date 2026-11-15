package com.mosaedy.assistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

public final class ReminderScheduler {
    private ReminderScheduler() { }
    private static final String PREFIX = "mosaedy://reminder/";
    private static PendingIntent pending(Context context, String id, String title, boolean snoozed, String mode) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(snoozed ? "com.mosaedy.SNOOZED_REMINDER" : "com.mosaedy.REMINDER");
        intent.setData(Uri.parse(PREFIX + Uri.encode(id) + (snoozed ? "/snooze" : "")));
        intent.putExtra("id", id); intent.putExtra("title", title);
        intent.putExtra("snoozed", snoozed); intent.putExtra("mode", mode);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    public static boolean exactAllowed(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms();
    }
    public static void schedule(Context context, JSONObject reminder) {
        String id = reminder.optString("id"); long due = reminder.optLong("dueAt");
        if (id.isEmpty() || due <= System.currentTimeMillis()) return;
        scheduleAlarm(context, due, pending(context, id, reminder.optString("title"), false, reminder.optString("mode", "alarm")),
            "alarm".equals(reminder.optString("mode", "alarm")), id);
    }
    public static int defaultSnoozeMinutes(Context context) {
        String raw = context.getApplicationContext().getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("snooze_options", "5,10,15");
        try { String first = raw.split(",")[0].trim(); int n = Integer.parseInt(first); return Math.max(1, Math.min(180, n)); }
        catch (Exception ignored) { return 5; }
    }
    public static void snooze(Context context, String id, String title, String mode) {
        snooze(context, id, title, mode, defaultSnoozeMinutes(context));
    }
    public static void snooze(Context context, String id, String title, String mode, int minutes) {
        int safe = Math.max(1, Math.min(180, minutes));
        long due = System.currentTimeMillis() + safe * 60L * 1000L;
        JSONObject record = ReminderStore.find(context, id);
        boolean recurring = record != null && !"none".equals(record.optString("repeat", "none"));
        if (recurring) ReminderStore.setStatus(context, id, "snoozed");
        else ReminderStore.updateTime(context, id, due, "snoozed");
        scheduleAlarm(context, due, pending(context, id, title, true, mode), "alarm".equals(mode), id);
    }
    private static void scheduleAlarm(Context context, long due, PendingIntent pendingIntent, boolean alarmMode, String id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            if (exactAllowed(context)) {
                if (alarmMode) {
                    Intent show = new Intent(context, MainActivity.class); show.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PendingIntent showIntent = PendingIntent.getActivity(context, id.hashCode(), show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(due, showIntent), pendingIntent);
                } else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pendingIntent);
                return;
            }
        } catch (SecurityException ignored) { }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pendingIntent);
    }
    public static void cancel(Context context, String id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(context, id, "", false, "notification"));
        am.cancel(pending(context, id, "", true, "notification"));
    }
    /** Calendar-based recurrence retains the local wall-clock time across daylight-saving changes. */
    public static long nextOccurrence(long oldTime, String repeat, long now) {
        Calendar calendar = Calendar.getInstance(); calendar.setTimeInMillis(oldTime);
        if ("monthly".equals(repeat)) {
            int desiredDay = calendar.get(Calendar.DAY_OF_MONTH);
            while (calendar.getTimeInMillis() <= now) {
                calendar.add(Calendar.MONTH, 1);
                int lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
                calendar.set(Calendar.DAY_OF_MONTH, Math.min(desiredDay, lastDay));
            }
            return calendar.getTimeInMillis();
        }
        int field = "weekly".equals(repeat) ? Calendar.WEEK_OF_YEAR : Calendar.DAY_OF_YEAR;
        while (calendar.getTimeInMillis() <= now) calendar.add(field, 1);
        return calendar.getTimeInMillis();
    }
    public static void restoreAll(Context context) {
        JSONArray arr = ReminderStore.all(context); long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i); if (r == null) continue;
            String repeat = r.optString("repeat", "none"); long due = r.optLong("dueAt");
            if (due <= now) {
                if ("none".equals(repeat)) {
                    String mode = r.optString("mode", "alarm");
                    ReminderNotifier.show(context, r.optString("id"), r.optString("title"), mode);
                    if (!"alarm".equals(mode)) ReminderStore.archive(context, r.optString("id"), ReminderNotifier.allowed(context) ? "completed" : "missed");
                    continue;
                }
                due = nextOccurrence(due, repeat, now);
                ReminderStore.updateTime(context, r.optString("id"), due, "upcoming");
                try { r.put("dueAt", due); } catch (Exception ignored) { }
            }
            schedule(context, r);
        }
    }
}
