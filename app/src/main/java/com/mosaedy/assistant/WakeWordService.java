package com.mosaedy.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

/** Explicit, time-limited (5 minutes) experimental wake-word session.
 *  NOT an always-on hotword engine; Android may suspend background recognition.
 */
public class WakeWordService extends Service implements RecognitionListener {
    public static final String ACTION_START = "com.mosaedy.WAKE_START";
    public static final String ACTION_STOP = "com.mosaedy.WAKE_STOP";
    private static final String CHANNEL = "mosaedy_wake_v32";
    private static final int NOTIFICATION_ID = 9324;
    private static final long DURATION_MS = 5L * 60L * 1000L;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static volatile boolean active = false;
    private SpeechRecognizer recognizer;
    private long expiresAt;
    private boolean stopping = false;
    private boolean awaitingResult = false;
    private final Runnable expire = () -> stopSelf();
    private final Runnable restart = this::listen;
    public static boolean isActive(){return active;}

    @Override public void onCreate(){super.onCreate();
        boolean en = "en".equals(getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("language", "ar"));
        NotificationChannel channel = new NotificationChannel(CHANNEL,en ? "Mosaedy wake session" : "استماع مساعدي التجريبي",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(en ? "A user-started, time-limited session with a visible stop control" : "جلسة محدودة يفعّلها المستخدم، مع زر إيقاف واضح");
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
    }
    private PendingIntent stopAction(){Intent i=new Intent(this,WakeWordService.class);i.setAction(ACTION_STOP);
        return PendingIntent.getService(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification ongoing(){boolean en="en".equals(getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("language", "ar"));Intent i=new Intent(this,MainActivity.class);i.putExtra("open_assistant",true);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent p=PendingIntent.getActivity(this,9300,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
           .setContentTitle(en ? "🎙️ Mosaedy is listening for your wake phrase" : "🎙️ مساعدي بيسمع كلمة النداء")
           .setContentText(en ? "Experimental 5-minute session — tap to open or stop" : "جلسة تجريبية ٥ دقائق — اضغط للفتح أو الإيقاف")
           .setContentIntent(p).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
           .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,en ? "Stop" : "إيقاف",stopAction()).build()).build();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        if(ACTION_STOP.equals(action)){stopSelf();return START_NOT_STICKY;}
        if(!ACTION_START.equals(action)){stopSelf();return START_NOT_STICKY;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        try {startForeground(NOTIFICATION_ID,ongoing());}catch(Exception e){stopSelf();return START_NOT_STICKY;}
        stopping=false;active=true;expiresAt=System.currentTimeMillis()+DURATION_MS;
        HANDLER.removeCallbacks(expire);HANDLER.postDelayed(expire,DURATION_MS);
        MainActivity.notifyWakeStatus(true);
        if(!SpeechRecognizer.isRecognitionAvailable(this)){stopSelf();return START_NOT_STICKY;}
        try {if(recognizer!=null)recognizer.destroy();
            if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
                recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            }else{recognizer=SpeechRecognizer.createSpeechRecognizer(this);}
            recognizer.setRecognitionListener(this);
            listen();
        }catch(Exception e){stopSelf();}
        return START_NOT_STICKY;
    }
    private void listen(){
        if(stopping || recognizer==null || System.currentTimeMillis()>=expiresAt){stopSelf();return;}
        try{Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            String language = getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("language", "ar");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en".equals(language) ? "en-US" : "ar-EG");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            awaitingResult=true;
            recognizer.startListening(intent);
        }catch(Exception e){onError(SpeechRecognizer.ERROR_CLIENT);}
    }
    private void next(){awaitingResult=false;if(!stopping){HANDLER.removeCallbacks(restart);HANDLER.postDelayed(restart,900);}}
    private String normalize(String text){return text.replaceAll("[\\u064B-\\u065F\\u0670ـ]","").trim().replaceAll("\\s+"," ");}
    private void checkWords(ArrayList<String> candidates){if(candidates==null)return;
        SharedPreferences prefs=getSharedPreferences("mosaedy_wake_v32",Context.MODE_PRIVATE);
        String custom=normalize(prefs.getString("custom",""));
        for(String item:candidates){if(item==null)continue;String t=normalize(item);
            String match="";
            String language = getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("language", "ar");
            if(t.contains("يا مساعدي"))match="يا مساعدي";
            else if(t.contains("مساعدي"))match="مساعدي";
            else if("en".equals(language) && t.toLowerCase(Locale.ROOT).contains("hey mosaedy"))match="hey mosaedy";
            else if("en".equals(language) && t.toLowerCase(Locale.ROOT).contains("mosaedy"))match="mosaedy";
            else if(custom.length()>=3 && t.toLowerCase(Locale.ROOT).contains(custom.toLowerCase(Locale.ROOT)))match=custom;
            if(match.isEmpty())continue;
            int matchAt = t.toLowerCase(Locale.ROOT).indexOf(match.toLowerCase(Locale.ROOT));
            String command=t.substring(Math.min(t.length(), Math.max(0, matchAt) + match.length())).trim();
            onDetected(match,command);
            stopSelf(); // Stop the microphone before starting the new user command.
            break;
        }
    }
    private void onDetected(String phrase,String command){
        if(MainActivity.deliverWake(phrase,command))return;
        // Android prohibits reliably launching activities from the background or unlocking the phone.
        // Offer a user-controlled notification instead; the microphone remains subject to OS limits.
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        boolean en = "en".equals(getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("language", "ar"));
        NotificationChannel ch=new NotificationChannel("mosaedy_wake_result_v32",en ? "Wake phrase detected" : "تم سماع مساعدي",NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(ch);
        Intent i=new Intent(this,MainActivity.class);i.putExtra("open_assistant",true);i.putExtra("wake_command",command);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,9301,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification note=new Notification.Builder(this,ch.getId())
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(en ? "I heard you — open Mosaedy" : "سمعتك! افتح مساعدي")
            .setContentText(command.isEmpty()?(en ? "Tap to speak or create a reminder" : "اضغط عشان تتكلم أو تسجل تذكير"):(en ? "Tap to review your request" : "اضغط لمراجعة طلبك"))
            .setAutoCancel(true).setContentIntent(pi).build();
        nm.notify(9302,note);
    }
    @Override public void onReadyForSpeech(Bundle b){}
    @Override public void onBeginningOfSpeech(){}
    @Override public void onRmsChanged(float rms){}
    @Override public void onBufferReceived(byte[] data){}
    @Override public void onEndOfSpeech(){}
    @Override public void onError(int error){
        // Backoff on recognizer overload; no infinite immediate retry loops.
        if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error==SpeechRecognizer.ERROR_CLIENT && stopping){stopSelf();return;}
        next();
    }
    @Override public void onResults(Bundle results){
        checkWords(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));next();
    }
    @Override public void onPartialResults(Bundle partial){}
    @Override public void onEvent(int type,Bundle bundle){}
    @Override public void onDestroy(){stopping=true;active=false;HANDLER.removeCallbacks(expire);HANDLER.removeCallbacks(restart);
        if(recognizer!=null){try{recognizer.cancel();recognizer.destroy();}catch(Exception ignored){}recognizer=null;}
        MainActivity.notifyWakeStatus(false);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
