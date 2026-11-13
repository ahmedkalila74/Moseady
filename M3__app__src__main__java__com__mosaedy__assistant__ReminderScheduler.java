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
        intent.putExtra("id", id);
        intent.putExtra("title", title);
        intent.putExtra("snoozed", snoozed);
        intent.putExtra("mode", mode);
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static boolean exactAllowed(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms();
    }

    public static void schedule(Context context, JSONObject reminder) {
        String id = reminder.optString("id");
        long due = reminder.optLong("dueAt");
        if (id.isEmpty() || due <= System.currentTimeMillis()) return;
        scheduleAlarm(context, due, pending(context, id, reminder.optString("title"), false, reminder.optString("mode","alarm")), "alarm".equals(reminder.optString("mode","alarm")), id);
    }

    public static void snooze(Context context, String id, String title, String mode) {
        long due = System.currentTimeMillis() + 10L * 60L * 1000L;
        scheduleAlarm(context, due, pending(context, id, title, true, mode), "alarm".equals(mode), id);
    }

    private static void scheduleAlarm(Context context, long due, PendingIntent pendingIntent, boolean alarmMode, String id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            if (exactAllowed(context)) {
                if (alarmMode) {
                    Intent show=new Intent(context,MainActivity.class);
                    show.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PendingIntent showIntent=PendingIntent.getActivity(context, id.hashCode(), show,
                        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(due,showIntent),pendingIntent);
                } else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pendingIntent);
                return;
            }
        } catch (SecurityException ignored) { }
        // Android may delay inexact alarms due to battery management / Doze.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pendingIntent);
    }

    public static void cancel(Context context, String id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(context, id, "", false, "notification"));
        am.cancel(pending(context, id, "", true, "notification"));
    }

    /** Add calendar days instead of 24-hour milliseconds, retaining local clock time across DST. */
    public static long nextOccurrence(long oldTime, String repeat, long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(oldTime);
        int field = "weekly".equals(repeat) ? Calendar.WEEK_OF_YEAR : Calendar.DAY_OF_YEAR;
        while (calendar.getTimeInMillis() <= now) calendar.add(field, 1);
        return calendar.getTimeInMillis();
    }

    public static void restoreAll(Context context) {
        JSONArray arr = ReminderStore.all(context);
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            String repeat = r.optString("repeat", "none");
            long due = r.optLong("dueAt");
            if (due <= now) {
                if ("none".equals(repeat)) {
                    // One-time reminders that became overdue while powered off are surfaced once.
                    ReminderNotifier.showRegular(context, r.optString("id"), r.optString("title"));
                    ReminderStore.remove(context, r.optString("id"));
                    continue;
                }
                due = nextOccurrence(due, repeat, now);
                ReminderStore.updateTime(context, r.optString("id"), due);
                try { r.put("dueAt", due); } catch (Exception ignored) { }
            }
            schedule(context, r);
        }
    }
}
