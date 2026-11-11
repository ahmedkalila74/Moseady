package com.mosaedy.app;
import android.app.*;import android.content.*;import android.os.Build;import android.provider.Settings;import org.json.*;
public class ReminderStore {
 static JSONArray get(Context c){try{return new JSONArray(c.getSharedPreferences("data",0).getString("items","[]"));}catch(Exception e){return new JSONArray();}}
 static void add(Context c,String title,long at){JSONArray a=get(c);JSONObject o=new JSONObject();try{o.put("title",title);o.put("at",at);o.put("id",(int)(System.currentTimeMillis()%Integer.MAX_VALUE));a.put(o);c.getSharedPreferences("data",0).edit().putString("items",a.toString()).apply();schedule(c,o);}catch(Exception ignored){}}
 static boolean schedule(Context c,JSONObject o){try{
 long at=o.getLong("at");if(at<=System.currentTimeMillis())return false;
 AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
 Intent i=new Intent(c,AlarmReceiver.class);i.putExtra("title",o.getString("title"));i.putExtra("id",o.getInt("id"));
 PendingIntent pi=PendingIntent.getBroadcast(c,o.getInt("id"),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);return false;}
 am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);return true;
 }catch(Exception e){return false;}}
 static void restore(Context c){JSONArray a=get(c);for(int j=0;j<a.length();j++)try{schedule(c,a.getJSONObject(j));}catch(Exception ignored){}}
}
