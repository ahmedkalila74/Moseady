package com.mosaedy.assistant;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TreeSet;

/** Alarm screen: quick snoozes, progressive +5 snooze, custom duration and same time tomorrow. */
public class AlarmActivity extends Activity {
    private int progressiveMinutes = 15;
    private TextView progressiveLabel;
    private boolean en;
    private int pad;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(0x00080000 | 0x00200000);
        getWindow().setStatusBarColor(Color.rgb(52,36,112));
        getWindow().setNavigationBarColor(Color.rgb(25,20,57));
        en = "en".equals(getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("language", "ar"));
        pad = (int)(20 * getResources().getDisplayMetrics().density);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL); root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(35,27,83));
        TextView title = text(en ? "⏰ Mosaedy — Reminder time" : "⏰ مساعدي — حان موعد التذكير", 22, Color.WHITE, true);
        title.setGravity(Gravity.CENTER); root.addView(title, full());
        TextView reminder = text(getIntent().getStringExtra("title"), 19, Color.WHITE, true);
        reminder.setGravity(Gravity.CENTER); reminder.setPadding(0, 22, 0, 20); root.addView(reminder, full());
        TextView snoozeLabel = text(en ? "Snooze reminder" : "تأجيل التذكير", 14, Color.LTGRAY, true);
        snoozeLabel.setGravity(Gravity.CENTER); root.addView(snoozeLabel, full());

        addButton(root, en ? "In 5 minutes" : "بعد 5 دقائق", () -> { send(AlarmRingingService.ACTION_SNOOZE, 5, 0); finish(); }, true);
        addButton(root, en ? "In 10 minutes" : "بعد 10 دقائق", () -> { send(AlarmRingingService.ACTION_SNOOZE, 10, 0); finish(); }, true);

        for (int minutes : customSnoozeOptions()) {
            addButton(root, en ? "In " + minutes + " minutes" : "بعد " + minutes + " دقيقة", () -> { send(AlarmRingingService.ACTION_SNOOZE, minutes, 0); finish(); }, false);
        }

        TextView progressiveTitle = text(en ? "Progressive snooze" : "تأجيل متدرج", 13, Color.WHITE, true);
        progressiveTitle.setPadding(0, 18, 0, 5); root.addView(progressiveTitle, full());
        LinearLayout progressiveRow = new LinearLayout(this); progressiveRow.setOrientation(LinearLayout.HORIZONTAL);
        progressiveRow.setGravity(Gravity.CENTER); progressiveRow.setPadding(0, 4, 0, 4);
        Button increase = button(en ? "+5 min" : "+5 دقائق", false);
        progressiveLabel = text(progressiveText(), 14, Color.WHITE, true); progressiveLabel.setGravity(Gravity.CENTER);
        Button applyProgressive = button(en ? "Snooze" : "تأجيل", true);
        increase.setOnClickListener(v -> {
            progressiveMinutes = Math.min(10080, progressiveMinutes + 5);
            progressiveLabel.setText(progressiveText());
            applyProgressive.setText(en ? "Snooze " + progressiveMinutes + " min" : "تأجيل " + progressiveMinutes + " دقيقة");
        });
        applyProgressive.setText(en ? "Snooze 15 min" : "تأجيل 15 دقيقة");
        applyProgressive.setOnClickListener(v -> { send(AlarmRingingService.ACTION_SNOOZE, progressiveMinutes, 0); finish(); });
        progressiveRow.addView(increase, weighted()); progressiveRow.addView(progressiveLabel, weighted()); progressiveRow.addView(applyProgressive, weighted());
        root.addView(progressiveRow, full());

        TextView customTitle = text(en ? "Custom snooze" : "مدة مخصصة", 13, Color.WHITE, true);
        customTitle.setPadding(0, 16, 0, 6); root.addView(customTitle, full());
        LinearLayout customRow = new LinearLayout(this); customRow.setOrientation(LinearLayout.HORIZONTAL); customRow.setGravity(Gravity.CENTER);
        EditText hours = numericInput(en ? "Hours" : "ساعات");
        EditText minutes = numericInput(en ? "Minutes" : "دقائق");
        customRow.addView(hours, weighted()); customRow.addView(minutes, weighted()); root.addView(customRow, full());
        addButton(root, en ? "Snooze for this duration" : "تأجيل بالمدة المحددة", () -> {
            int h = parse(hours.getText().toString()), m = parse(minutes.getText().toString());
            if (h < 0 || m < 0 || m > 59 || h > 168 || (h == 0 && m == 0)) {
                hours.setError(en ? "Use 0–168 hours" : "أدخل ساعات من 0 إلى 168");
                minutes.setError(en ? "Use 0–59 minutes" : "أدخل دقائق من 0 إلى 59");
                return;
            }
            long total = h * 60L + m;
            if (total > 10080) return;
            send(AlarmRingingService.ACTION_SNOOZE, (int)total, 0); finish();
        }, true);

        addButton(root, en ? "Tomorrow at the same time" : "بكرة في نفس الموعد", () -> {
            Calendar next = Calendar.getInstance();
            org.json.JSONObject stored = ReminderStore.find(this, getIntent().getStringExtra("id"));
            if (stored != null) {
                Calendar original = Calendar.getInstance();
                original.setTimeInMillis(stored.optLong("dueAt", System.currentTimeMillis()));
                next.set(Calendar.HOUR_OF_DAY, original.get(Calendar.HOUR_OF_DAY));
                next.set(Calendar.MINUTE, original.get(Calendar.MINUTE));
                next.set(Calendar.SECOND, original.get(Calendar.SECOND));
                next.set(Calendar.MILLISECOND, 0);
            }
            next.add(Calendar.DAY_OF_YEAR, 1);
            send(AlarmRingingService.ACTION_SNOOZE_AT, 0, next.getTimeInMillis());
            finish();
        }, false);

        Button stop = button(en ? "Stop alarm" : "إيقاف الألارم", false);
        stop.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(74,61,132)));
        stop.setOnClickListener(v -> { send(AlarmRingingService.ACTION_STOP, 0, 0); finish(); });
        root.addView(stop, full());
        scroll.addView(root); setContentView(scroll);
    }

    private String progressiveText() { return en ? progressiveMinutes + " minutes" : progressiveMinutes + " دقيقة"; }
    private int parse(String value) { try { return Integer.parseInt(value.trim()); } catch (Exception e) { return -1; } }
    private EditText numericInput(String hint) {
        EditText edit = new EditText(this); edit.setSingleLine(true); edit.setHint(hint);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER); edit.setTextColor(Color.WHITE); edit.setHintTextColor(Color.LTGRAY);
        edit.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(165,151,235))); edit.setPadding(12, 10, 12, 10);
        return edit;
    }
    private ArrayList<Integer> customSnoozeOptions() {
        TreeSet<Integer> out = new TreeSet<>();
        String raw = getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("snooze_options", "5,10,15");
        for (String s : raw.split(",")) try {
            int n = Integer.parseInt(s.trim());
            if (n > 10 && n != 15 && n <= 10080) out.add(n);
        } catch (Exception ignored) { }
        return new ArrayList<>(out);
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value == null ? "" : value); t.setTextColor(color); t.setTextSize(size);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }
    private Button button(String label, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? Color.rgb(115,88,220) : Color.rgb(85,70,151)));
        b.setTextColor(Color.WHITE); return b;
    }
    private void addButton(LinearLayout root, String label, Runnable action, boolean primary) {
        Button b = button(label, primary); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = full(); lp.setMargins(0, 6, 0, 6); root.addView(b, lp);
    }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -2, 1f); }
    private void send(String action, int minutes, long dueAt) {
        Intent i = new Intent(this, AlarmRingingService.class); i.setAction(action);
        i.putExtra("id", getIntent().getStringExtra("id"));
        i.putExtra("title", getIntent().getStringExtra("title"));
        i.putExtra("minutes", minutes); i.putExtra("dueAt", dueAt);
        startService(i);
    }
}
