package com.mosaedy.app;
import android.app.*;import android.content.*;import android.os.Build;
public class AlarmReceiver extends android.content.BroadcastReceiver {
 public static final String CHANNEL="reminders";
 public void onReceive(Context c,Intent i){
  String title=i.getStringExtra("title"); if(title==null)title="عندك تذكير";
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  nm.createNotificationChannel(new NotificationChannel(CHANNEL,"تنبيهات مساعدي",NotificationManager.IMPORTANCE_HIGH));
  Intent open=new Intent(c,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent pi=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("مساعدي بيفكرك").setContentText(title).setAutoCancel(true).setContentIntent(pi).setCategory(Notification.CATEGORY_REMINDER).build();
  nm.notify(i.getIntExtra("id",1),n);
 }
}
