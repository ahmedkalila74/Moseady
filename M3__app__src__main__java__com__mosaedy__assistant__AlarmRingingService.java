package com.mosaedy.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** User-selected audible alarms. Sound loops for at most 10 minutes, and can be stopped/snoozed. */
public class AlarmRingingService extends Service {
    public static final String ACTION_START = "com.mosaedy.alarm.START";
    public static final String ACTION_STOP = "com.mosaedy.alarm.STOP";
    public static final String ACTION_SNOOZE = "com.mosaedy.alarm.SNOOZE";
    private static final String CHANNEL = "mosaedy_ringing_v1";
    private static final int ID = 7623;
    private MediaPlayer player;
    private Handler handler;
    private String activeId = "";
    private String activeTitle = "تذكير";
    private final Runnable timeout = () -> stopSelf();

    public static void start(Context context, String id, String title) {
        Intent i = new Intent(context, AlarmRingingService.class);
        i.setAction(ACTION_START);
        i.putExtra("id", id);
        i.putExtra("title", title);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
        } catch (Exception ex) {
            // Android background service restrictions can prevent launching in some modes.
            ReminderNotifier.showRegular(context, id, title);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        NotificationChannel channel = new NotificationChannel(CHANNEL,"ألارم مساعدي",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("منبّه صوتي مع إيقاف وتأجيل");
        channel.setSound(null, null); // Audio is played by the service, not twice by the notification.
        channel.enableVibration(true);
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
    }

    private PendingIntent action(String verb, String id, String title, int code) {
        Intent i = new Intent(this, AlarmRingingService.class);
        i.setAction(verb);
        i.putExtra("id", id);
        i.putExtra("title", title);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification makeNotification(String id, String title) {
        Intent open = new Intent(this, AlarmActivity.class);
        open.putExtra("id", id);
        open.putExtra("title", title);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 810, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ ألارم مساعدي")
            .setContentText(title)
            .setStyle(new Notification.BigTextStyle().bigText(title))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(content)
            .setPriority(Notification.PRIORITY_MAX)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "إيقاف", action(ACTION_STOP,id,title,811)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_lock_idle_alarm, "تأجيل 10 دقائق", action(ACTION_SNOOZE,id,title,812)).build());
        // Full screen shows only when Android permits it for alarm apps. Otherwise heads-up remains.
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()) b.setFullScreenIntent(content, true);
        return b.build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent==null?"":intent.getAction();
        if (ACTION_STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_SNOOZE.equals(action)) {
            String id=intent.getStringExtra("id");
            String title=intent.getStringExtra("title");
            if (id==null||id.isEmpty()) id=activeId;
            if (title==null||title.isEmpty()) title=activeTitle;
            if (!id.isEmpty()) ReminderScheduler.snooze(this,id,title,"alarm");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        String id = intent.getStringExtra("id"), title=intent.getStringExtra("title");
        if (id==null||id.isEmpty()) {stopSelf(); return START_NOT_STICKY;}
        activeId=id;activeTitle=(title==null||title.isEmpty())?"تذكير":title;
        startForeground(ID, makeNotification(activeId,activeTitle));
        stopSound();
        try {
            Uri uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri==null)uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this,uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) { stopSound(); /* Notification still shows stop/snooze. */ }
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, 600000L);
        return START_NOT_STICKY;
    }

    private void stopSound(){if(player!=null){try{player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}}
    @Override public void onDestroy(){handler.removeCallbacks(timeout);stopSound();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
