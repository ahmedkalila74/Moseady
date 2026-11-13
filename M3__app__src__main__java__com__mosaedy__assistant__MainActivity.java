package com.mosaedy.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.lang.ref.WeakReference;

/** Android WebView for locally bundled UI; reminders, notifications, speech use Android APIs. */
public class MainActivity extends Activity {
    private static final int REQ_SPEECH = 1001;
    private static final int REQ_SAVE = 1002;
    private static final int REQ_IMPORT = 1003;
    private static final int REQ_NOTIFICATION = 1004;
    private static final int REQ_WAKE_MIC = 1005;
    private static final int REQ_RINGTONE = 1006;
    private static WeakReference<MainActivity> instance = new WeakReference<>(null);
    private boolean visible = false;
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean pageLoaded = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance=new WeakReference<>(this);
        getWindow().setStatusBarColor(Color.rgb(17, 26, 57));
        getWindow().setNavigationBarColor(Color.rgb(248, 248, 255));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ReminderNotifier.ensureChannel(this);
        ReminderScheduler.restoreAll(this);

        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                String lang = getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("language", "ar");
                int result = tts.setLanguage("en".equals(lang) ? Locale.US : new Locale("ar", "EG"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(247, 248, 254));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("file:///android_asset/");
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                callJs("window.nativeRefresh && window.nativeRefresh()");
                if (getIntent() != null && getIntent().getBooleanExtra("open_assistant",false)) openFromWake(getIntent());
                if (getIntent() != null && getIntent().getData() != null) {
                    callJs("window.openTab && window.openTab('tasks')");
                }
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void callJs(String statement) {
        runOnUiThread(() -> {
            if (web != null && pageLoaded) web.evaluateJavascript(statement, null);
        });
    }

    public static void notifyWakeStatus(boolean active){MainActivity activity=instance.get();if(activity!=null)activity.callJs("window.onWakeStatus && window.onWakeStatus("+active+")");}
    public static boolean deliverWake(String phrase,String command){MainActivity activity=instance.get();if(activity==null||!activity.visible||!activity.pageLoaded)return false;
        activity.callJs("window.onWakeDetected && window.onWakeDetected("+JSONObject.quote(phrase)+","+JSONObject.quote(command)+")");return true;}
    private void openFromWake(Intent intent){if(intent==null)return;
        callJs("window.openTab && window.openTab('assistant')");
        String command=intent.getStringExtra("wake_command");
        if(command!=null&&!command.trim().isEmpty()) callJs("window.handleMessage && window.handleMessage("+JSONObject.quote(command)+")");
        else callJs("window.onWakeDetected && window.onWakeDetected('wake','')");
    }
    private void beginWakeService(){
        try{Intent i=new Intent(this,WakeWordService.class);i.setAction(WakeWordService.ACTION_START);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            showMessage("بدأت جلسة الاستماع لمدة ٥ دقائق");
        }catch(Exception e){showMessage("تعذر بدء الاستماع على الجهاز ده");}
    }
    private void showMessage(String message) {
        boolean en = "en".equals(getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("language", "ar"));
        if (en) {
            if (message.contains("بدأت جلسة الاستماع")) message = "Wake listening session started";
            else if (message.contains("تعذر بدء الاستماع")) message = "Could not start the listening session on this device";
            else if (message.contains("تم السماح بالإشعارات")) message = "Notifications permission granted";
            else if (message.contains("الإشعارات غير مفعّلة")) message = "Notifications are disabled; reminders may not appear";
            else if (message.contains("تم حفظ نغمة الألارم")) message = "Alarm sound saved";
            else if (message.contains("تم تصدير المواعيد")) message = "Backup exported successfully";
            else if (message.contains("تعذر حفظ النسخة")) message = "Could not save the backup";
            else if (message.contains("تم استيراد")) message = "Reminders imported successfully";
            else if (message.contains("الملف غير صالح")) message = "The backup file is invalid or could not be read";
            else if (message.contains("لا يمكن الاستماع")) message = "Microphone permission is required for wake listening";
        }
        final String display = message;
        runOnUiThread(() -> Toast.makeText(this, display, Toast.LENGTH_LONG).show());
    }

    public class Bridge {
        @JavascriptInterface public String getReminders() {
            return ReminderStore.all(getApplicationContext()).toString();
        }
        @JavascriptInterface public String getHistory() {
            return ReminderStore.history(getApplicationContext()).toString();
        }
        @JavascriptInterface public String getSettings() {
            try {
                android.content.SharedPreferences settings = getSharedPreferences("mosaedy_settings", MODE_PRIVATE);
                android.content.SharedPreferences wake = getSharedPreferences("mosaedy_wake_v32", MODE_PRIVATE);
                JSONObject out = new JSONObject();
                out.put("language", settings.getString("language", "ar"));
                out.put("timeFormat", settings.getString("time_format", "12"));
                out.put("snoozeOptions", settings.getString("snooze_options", "5,10,15"));
                out.put("alarmToneUri", settings.getString("alarm_tone_uri", ""));
                out.put("defaultMode", settings.getString("default_mode", "alarm"));
                out.put("wakePhrase", wake.getString("custom", ""));
                out.put("wakeAutoMic", wake.getBoolean("autoMic", true));
                return out.toString();
            } catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public void completeReminder(String id) {
            ReminderScheduler.cancel(getApplicationContext(), id);
            ReminderStore.archive(getApplicationContext(), id, "completed");
        }

        @JavascriptInterface public String saveReminder(String raw) {
            try {
                JSONObject record = new JSONObject(raw);
                String id = record.optString("id");
                ReminderStore.put(getApplicationContext(), record);
                ReminderScheduler.cancel(getApplicationContext(), id);
                ReminderScheduler.schedule(getApplicationContext(), record);
                return "{\"ok\":true}";
            } catch (Exception e) {
                return "{\"ok\":false,\"error\":\"invalid_reminder\"}";
            }
        }

        @JavascriptInterface public void deleteReminder(String id) {
            ReminderScheduler.cancel(getApplicationContext(), id);
            ReminderStore.archive(getApplicationContext(), id, "cancelled");
        }
        @JavascriptInterface public void configureSnoozeOptions(String csv) {
            StringBuilder safe = new StringBuilder();
            if (csv != null) for (String item : csv.split(",")) {
                try {
                    int n = Integer.parseInt(item.trim());
                    if (n >= 1 && n <= 180 && safe.indexOf("," + n + ",") < 0) safe.append(n).append(',');
                } catch (Exception ignored) { }
            }
            String value = safe.length() == 0 ? "5,10,15" : safe.substring(0, safe.length()-1);
            getApplicationContext().getSharedPreferences("mosaedy_settings", MODE_PRIVATE).edit().putString("snooze_options", value).apply();
        }
        @JavascriptInterface public void chooseAlarmTone() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختيار نغمة ألارم مساعدي");
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    String saved = getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("alarm_tone_uri", "");
                    if (!saved.isEmpty()) intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(saved));
                    startActivityForResult(intent, REQ_RINGTONE);
                } catch (Exception e) { showMessage("تعذر فتح نغمات الهاتف"); }
            });
        }
        @JavascriptInterface public void setTimeFormat(String format) {
            getApplicationContext().getSharedPreferences("mosaedy_settings", MODE_PRIVATE).edit().putString("time_format", "24".equals(format) ? "24" : "12").apply();
        }
        @JavascriptInterface public void setDefaultMode(String mode) {
            getApplicationContext().getSharedPreferences("mosaedy_settings", MODE_PRIVATE).edit().putString("default_mode", "notification".equals(mode) ? "notification" : "alarm").apply();
        }
        @JavascriptInterface public void setAppLanguage(String language) {
            String safe = "en".equals(language) ? "en" : "ar";
            getApplicationContext().getSharedPreferences("mosaedy_settings", MODE_PRIVATE).edit().putString("language", safe).apply();
            runOnUiThread(() -> {
                if (tts != null) {
                    int result = tts.setLanguage("en".equals(safe) ? Locale.US : new Locale("ar", "EG"));
                    ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
                }
            });
        }

        @JavascriptInterface public String capabilities() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("notificationsAllowed", ReminderNotifier.allowed(getApplicationContext()));
                obj.put("exactAlarmsAllowed", ReminderScheduler.exactAllowed(getApplicationContext()));
                obj.put("ttsReady", ttsReady);
                obj.put("android", Build.VERSION.SDK_INT);
                obj.put("wakeSession",WakeWordService.isActive());
                obj.put("microphoneAllowed", Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
                return obj.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface public void requestNotifications() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
                } else {
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(intent);
                }
            });
        }

        @JavascriptInterface public void requestExactAlarms() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !ReminderScheduler.exactAllowed(MainActivity.this)) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        showMessage("افتح إعدادات التطبيق واسمح بالمنبّهات والتذكيرات الدقيقة");
                    }
                } else {
                    showMessage("المنبّهات الدقيقة مفعّلة");
                }
            });
        }

        @JavascriptInterface public void configureWake(String phrase, boolean autoMic){
            String custom=phrase==null?"":phrase.trim().replaceAll("\\s+"," ");
            if(custom.length()>40)custom=custom.substring(0,40);
            getApplicationContext().getSharedPreferences("mosaedy_wake_v32",MODE_PRIVATE)
                .edit().putString("custom",custom).putBoolean("autoMic",autoMic).apply();
        }
        @JavascriptInterface public void startWakeSession(){
            runOnUiThread(()->{
                if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_WAKE_MIC);
                }else beginWakeService();
            });
        }
        @JavascriptInterface public void stopWakeSession(){runOnUiThread(()->{
            Intent i=new Intent(MainActivity.this,WakeWordService.class);i.setAction(WakeWordService.ACTION_STOP);
            try{startService(i);}catch(Exception ignored){stopService(new Intent(MainActivity.this,WakeWordService.class));}
        });}
        @JavascriptInterface public void startVoiceInput() {
            runOnUiThread(() -> {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                String lang = getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("language", "ar");
                String locale = "en".equals(lang) ? "en-US" : "ar-EG";
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale);
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "en".equals(lang) ? "Tell Mosaedy what you need" : "اتفضل، قول لمساعدي طلبك");
                try { startActivityForResult(intent, REQ_SPEECH); }
                catch (ActivityNotFoundException e) {
                    callJs("window.onVoiceError && window.onVoiceError('خدمة التعرف على الصوت غير متوفرة على الجهاز')");
                }
            });
        }

        @JavascriptInterface public void speak(String text) {
            if (text == null || text.length() > 600) return;
            runOnUiThread(() -> {
                if (tts != null && ttsReady) {
                    String lang = getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("language", "ar");
                    tts.setLanguage("en".equals(lang) ? Locale.US : new Locale("ar", "EG"));
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mosaedy_reply");
                }
            });
        }

        @JavascriptInterface public void exportBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "mosaedy-backup.json");
                startActivityForResult(intent, REQ_SAVE);
            });
        }

        @JavascriptInterface public void importBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, REQ_IMPORT);
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_SPEECH) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                callJs("window.onSpeechCandidates && window.onSpeechCandidates(" + new JSONArray(results).toString() + ")");
            }
        } else if (requestCode == REQ_RINGTONE) {
            Uri picked = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String value = picked == null ? "" : picked.toString();
            getSharedPreferences("mosaedy_settings", MODE_PRIVATE).edit().putString("alarm_tone_uri", value).apply();
            showMessage("تم حفظ نغمة الألارم");
            callJs("window.onAlarmToneSaved && window.onAlarmToneSaved()");
        } else if (requestCode == REQ_SAVE) {
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream == null) throw new Exception("No output");
                JSONObject root = new JSONObject();
                root.put("format", "mosaedy-native-backup");
                root.put("version", 1);
                root.put("reminders", ReminderStore.all(this));
                root.put("history", ReminderStore.history(this));
                root.put("settings", new JSONObject().put("language", getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("language", "ar"))
                    .put("snooze_options", getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("snooze_options", "5,10,15"))
                    .put("time_format", getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("time_format", "12"))
                    .put("alarm_tone_uri", getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("alarm_tone_uri", ""))
                    .put("default_mode", getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("default_mode", "alarm"))
                    .put("wake_phrase", getSharedPreferences("mosaedy_wake_v32", MODE_PRIVATE).getString("custom", ""))
                    .put("wake_auto_mic", getSharedPreferences("mosaedy_wake_v32", MODE_PRIVATE).getBoolean("autoMic", true)));
                stream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
                showMessage("تم تصدير المواعيد بنجاح");
            } catch (Exception e) { showMessage("تعذر حفظ النسخة الاحتياطية"); }
        } else if (requestCode == REQ_IMPORT) {
            try (InputStream stream = getContentResolver().openInputStream(data.getData())) {
                if (stream == null) throw new Exception("No input");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] bytes = new byte[4096];
                int read;
                while ((read = stream.read(bytes)) != -1) {
                    if (buffer.size() + read > 1024 * 1024) throw new Exception("File too large");
                    buffer.write(bytes, 0, read);
                }
                JSONObject json = new JSONObject(buffer.toString("UTF-8"));
                boolean nativeBackup = "mosaedy-native-backup".equals(json.optString("format"));
                boolean legacyBackup = "Mosaedy".equals(json.optString("app"));
                if (!nativeBackup && !legacyBackup) throw new Exception("Wrong format");
                JSONArray items = nativeBackup ? json.getJSONArray("reminders") : json.getJSONArray("tasks");
                if (nativeBackup && json.has("history")) ReminderStore.replaceHistory(this, json.optJSONArray("history"));
                if (nativeBackup && json.has("settings")) {
                    JSONObject settings = json.optJSONObject("settings");
                    if (settings != null) getSharedPreferences("mosaedy_settings", MODE_PRIVATE).edit()
                        .putString("language", settings.optString("language", "ar"))
                        .putString("snooze_options", settings.optString("snooze_options", "5,10,15"))
                        .putString("time_format", settings.optString("time_format", "12"))
                        .putString("alarm_tone_uri", settings.optString("alarm_tone_uri", ""))
                        .putString("default_mode", settings.optString("default_mode", "alarm")).apply();
                    if (settings != null) getSharedPreferences("mosaedy_wake_v32", MODE_PRIVATE).edit()
                        .putString("custom", settings.optString("wake_phrase", ""))
                        .putBoolean("autoMic", settings.optBoolean("wake_auto_mic", true)).apply();
                }
                int count = 0;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject r = items.optJSONObject(i);
                    if (r == null || (legacyBackup && r.optBoolean("done", false))) continue;
                    try {
                        JSONObject candidate = new JSONObject();
                        candidate.put("id", r.optString("id", "imported-" + i));
                        candidate.put("title", r.optString("title"));
                        candidate.put("repeat", r.optString("repeat", "none"));
                        candidate.put("mode", r.optString("mode", "alarm"));
                        candidate.put("notes", r.optString("notes", ""));
                        long due;
                        if (nativeBackup) due = r.optLong("dueAt");
                        else due = java.time.Instant.parse(r.optString("when")).toEpochMilli();
                        if (due <= System.currentTimeMillis()) {
                            String repeat = candidate.optString("repeat");
                            if (!"daily".equals(repeat) && !"weekly".equals(repeat) && !"monthly".equals(repeat)) continue;
                            due = ReminderScheduler.nextOccurrence(due, repeat, System.currentTimeMillis());
                        }
                        candidate.put("dueAt", due);
                        ReminderStore.put(this, candidate);
                        ReminderScheduler.cancel(this, candidate.optString("id"));
                        ReminderScheduler.schedule(this, candidate);
                        count++;
                    } catch (Exception ignored) { }
                }
                showMessage("تم استيراد " + count + " موعد");
                callJs("window.nativeSettingsChanged && window.nativeSettingsChanged()");
                callJs("window.nativeRefresh && window.nativeRefresh()");
            } catch (Exception e) { showMessage("الملف غير صالح أو تعذر قراءته"); }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WAKE_MIC){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED)beginWakeService();
            else showMessage("لا يمكن الاستماع بدون السماح للميكروفون");
        }
        if (requestCode == REQ_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                showMessage("تم السماح بالإشعارات");
            else showMessage("الإشعارات غير مفعّلة؛ لن تظهر التذكيرات حتى تسمح بها");
            callJs("window.nativeRefresh && window.nativeRefresh()");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        visible=true;
        if (ReminderScheduler.exactAllowed(this)) ReminderScheduler.restoreAll(this);
        callJs("window.nativeRefresh && window.nativeRefresh()");
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if(intent.getBooleanExtra("open_assistant",false))openFromWake(intent);
        if (intent.getData() != null) callJs("window.openTab && window.openTab('tasks')");
    }

    @Override protected void onPause(){visible=false;super.onPause();}

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        visible=false;if(instance.get()==this)instance=new WeakReference<>(null);
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (web != null) { web.removeJavascriptInterface("AndroidBridge"); web.destroy(); web = null; }
        super.onDestroy();
    }
}
