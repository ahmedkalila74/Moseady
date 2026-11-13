package com.mosaedy.assistant;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

public final class ReminderNotifier {
    public static final String CHANNEL = "mosaedy_reminders";
    private ReminderNotifier() { }

    public static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "تذكيرات مساعدي", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("تنبيه بمواعيدك وتذكيراتك");
        channel.enableVibration(true);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    public static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm.areNotificationsEnabled();
    }

    public static void show(Context context, String id, String title) {
        if (!allowed(context)) return;
        ensureChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.setData(Uri.parse("mosaedy://open/" + Uri.encode(id)));
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent snooze = new Intent(context, NotificationActionReceiver.class);
        snooze.setAction("com.mosaedy.SNOOZE");
        snooze.setData(Uri.parse("mosaedy://snoozeaction/" + Uri.encode(id)));
        snooze.putExtra("id", id);
        snooze.putExtra("title", title);
        PendingIntent snoozeIntent = PendingIntent.getBroadcast(context, 0, snooze,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ تذكير من مساعدي")
            .setContentText(title)
            .setStyle(new Notification.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setColor(Color.rgb(114, 103, 233))
            .addAction(new Notification.Action.Builder(
                android.R.drawable.ic_lock_idle_alarm, "تأجيل 10 دقائق", snoozeIntent).build())
            .build();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(id.hashCode(), n);
    }
}
