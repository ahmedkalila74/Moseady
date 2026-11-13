package com.mosaedy.assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Alarm screen with user-configurable quick snooze intervals. */
public class AlarmActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(0x00080000 | 0x00200000);
        getWindow().setStatusBarColor(Color.rgb(16,25,55));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER); root.setPadding(28,28,28,28); root.setBackgroundColor(Color.rgb(18,27,60));
        boolean en = "en".equals(getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("language", "ar"));
        TextView title = new TextView(this); title.setText(en ? "⏰ Mosaedy — Reminder time" : "⏰ مساعدي — حان موعد التذكير"); title.setTextColor(Color.WHITE); title.setTextSize(23); title.setGravity(Gravity.CENTER);
        TextView text = new TextView(this); text.setText(getIntent().getStringExtra("title")); text.setTextColor(Color.WHITE); text.setTextSize(19); text.setGravity(Gravity.CENTER); text.setPadding(0,28,0,28);
        root.addView(title); root.addView(text);
        TextView label = new TextView(this); label.setText(en ? "Snooze reminder" : "تأجيل التذكير"); label.setTextColor(Color.LTGRAY); label.setTextSize(14); label.setGravity(Gravity.CENTER); label.setPadding(0,8,0,8); root.addView(label);
        for (int minutes : snoozeOptions()) {
            Button snooze = new Button(this); snooze.setText(en ? "In " + minutes + " minutes" : "بعد " + minutes + " دقائق");
            snooze.setOnClickListener(v -> { send(AlarmRingingService.ACTION_SNOOZE, minutes); finish(); }); root.addView(snooze);
        }
        Button stop = new Button(this); stop.setText(en ? "Stop alarm" : "إيقاف الألارم");
        stop.setOnClickListener(v -> { send(AlarmRingingService.ACTION_STOP, 0); finish(); }); root.addView(stop);
        setContentView(root);
    }
    private ArrayList<Integer> snoozeOptions() {
        ArrayList<Integer> out = new ArrayList<>();
        String raw = getSharedPreferences("mosaedy_settings", MODE_PRIVATE).getString("snooze_options", "5,10,15");
        for (String s : raw.split(",")) try { int n = Integer.parseInt(s.trim()); if (n >= 1 && n <= 180 && !out.contains(n)) out.add(n); } catch (Exception ignored) { }
        if (out.isEmpty()) out.add(5); return out;
    }
    private void send(String action, int minutes) {
        Intent i = new Intent(this, AlarmRingingService.class); i.setAction(action);
        i.putExtra("id", getIntent().getStringExtra("id")); i.putExtra("title", getIntent().getStringExtra("title")); i.putExtra("minutes", minutes);
        startService(i);
    }
}
