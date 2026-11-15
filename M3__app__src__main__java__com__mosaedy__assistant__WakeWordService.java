package com.mosaedy.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.Manifest;
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

/**
 * User-enabled continuous wake-word listener. It stays active until the user stops it.
 * Android/OEM speech-service and background-start limits still apply.
 */
public class WakeWordService extends Service implements RecognitionListener {
    public static final String ACTION_START = "com.mosaedy.WAKE_START";
    public static final String ACTION_STOP = "com.mosaedy.WAKE_STOP";
    public static final String PREFS = "mosaedy_wake_v32";
    public static final String KEY_ENABLED = "wake_enabled";
    private static final String CHANNEL = "mosaedy_wake_v35";
    private static final int NOTIFICATION_ID = 9324;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static volatile boolean active = false;
    private SpeechRecognizer recognizer;
    private boolean stopping = false;
    private long lastWakeNoticeAt = 0L;
    private final Runnable restart = this::listen;

    public static boolean isActive() { return active; }
    public static boolean isEnabled(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    @Override public void onCreate() {
        super.onCreate();
        boolean en = "en".equals(getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("language", "ar"));
        NotificationChannel channel = new NotificationChannel(CHANNEL,
            en ? "Mosaedy continuous wake listening" : "استماع مساعدي المستمر", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(en ? "User-enabled wake listening. Stop it from the notification or app settings."
            : "استماع فعّله المستخدم. يمكنك إيقافه من الإشعار أو إعدادات التطبيق.");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
    }

    private PendingIntent stopAction() {
        Intent i = new Intent(this, WakeWordService.class);
        i.setAction(ACTION_STOP);
        return PendingIntent.getService(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification ongoing() {
        boolean en = "en".equals(getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("language", "ar"));
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("open_assistant", true);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent p = PendingIntent.getActivity(this, 9300, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(en ? "🎙️ Mosaedy is listening" : "🎙️ مساعدي في وضع الاستماع المستمر")
            .setContentText(en ? "Active until you stop it" : "يظل نشطًا حتى تضغط إيقاف")
            .setContentIntent(p).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, en ? "Stop" : "إيقاف", stopAction()).build())
            .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (ACTION_STOP.equals(action)) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        else if (!prefs.getBoolean(KEY_ENABLED, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIFICATION_ID, ongoing());
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        stopping = false;
        active = true;
        MainActivity.notifyWakeStatus(true);
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            MainActivity.notifyWakeStatus(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (recognizer != null) recognizer.destroy();
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
            recognizer.setRecognitionListener(this);
            listen();
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private Intent recognitionIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        // Do not bind recognition to the UI language. On older Android versions the recognizer
        // uses the device speech locale; API 34+ can switch between Arabic and English per utterance.
        if (Build.VERSION.SDK_INT >= 34) {
            intent.putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true);
            intent.putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", "balanced");
            ArrayList<String> allowed = new ArrayList<>();
            allowed.add("ar-EG"); allowed.add("en-US");
            intent.putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", allowed);
            intent.putStringArrayListExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES", allowed);
        }
        return intent;
    }

    private void listen() {
        if (stopping || recognizer == null || !isEnabled(this)) return;
        try {
            recognizer.startListening(recognitionIntent());
        } catch (Exception e) {
            next();
        }
    }

    private void next() {
        if (stopping || !isEnabled(this)) return;
        HANDLER.removeCallbacks(restart);
        HANDLER.postDelayed(restart, 1200);
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
            .replaceAll("[\\u064B-\\u065F\\u0670ـ]", "").trim().replaceAll("\\s+", " ");
    }

    private boolean checkWords(ArrayList<String> candidates) {
        if (candidates == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String custom = normalize(prefs.getString("custom", ""));
        for (String item : candidates) {
            if (item == null) continue;
            String text = normalize(item);
            String match = "";
            int at = text.indexOf("يا مساعدي");
            if (at >= 0) match = "يا مساعدي";
            else if ((at = text.indexOf("مساعدي")) >= 0) match = "مساعدي";
            else if ((at = text.indexOf("hey mosaedy")) >= 0) match = "hey mosaedy";
            else if ((at = text.indexOf("mosaedy")) >= 0) match = "mosaedy";
            else if (!custom.isEmpty() && (at = text.indexOf(custom)) >= 0) match = custom;
            if (match.isEmpty()) continue;
            String command = text.substring(Math.min(text.length(), at + match.length())).trim();
            boolean activityReceivedWake = onDetected(match, command);
            if (activityReceivedWake) {
                stopSelf(); // Release the microphone before starting the foreground command recorder.
            } else {
                // Keep the persistent listener running while the user decides whether to tap the notification.
                HANDLER.removeCallbacks(restart);
                HANDLER.postDelayed(restart, 3500);
            }
            return true;
        }
        return false;
    }

    private boolean onDetected(String phrase, String command) {
        if (MainActivity.deliverWake(phrase, command)) return true;
        // Android can block background activity launches. Use a high-priority, user-tappable notification.
        // The listener remains active after a short cooldown if the user has not opened the app.
        long now = System.currentTimeMillis();
        if (now - lastWakeNoticeAt < 15000L) return false;
        lastWakeNoticeAt = now;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean en = "en".equals(getSharedPreferences("mosaedy_settings", Context.MODE_PRIVATE).getString("language", "ar"));
        NotificationChannel ch = new NotificationChannel("mosaedy_wake_result_v35",
            en ? "Wake phrase detected" : "تم سماع كلمة النداء", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(ch);
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("open_assistant", true);
        i.putExtra("wake_command", command);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 9301, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(this, ch.getId())
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(en ? "I heard you — open Mosaedy" : "سمعتك! افتح مساعدي")
            .setContentText(command.isEmpty() ? (en ? "Tap to speak or create a reminder" : "اضغط للتسجيل أو إضافة تذكير")
                : (en ? "Tap to process your request" : "اضغط لتنفيذ طلبك"))
            .setAutoCancel(true).setContentIntent(pi).setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_MAX).build();
        nm.notify(9302, note);
        return false;
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onError(int error) {
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || (error == SpeechRecognizer.ERROR_CLIENT && stopping)) {
            stopSelf();
            return;
        }
        next();
    }
    @Override public void onResults(Bundle results) {
        boolean wakeHandled = checkWords(results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
        if (!wakeHandled) next();
    }
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }
    @Override public void onDestroy() {
        stopping = true;
        active = false;
        HANDLER.removeCallbacks(restart);
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
        MainActivity.notifyWakeStatus(false);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
