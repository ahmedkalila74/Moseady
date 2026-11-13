package com.mosaedy.assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Optional lock-screen screen for an alarm; the notification actions also work without it. */
public class AlarmActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        else getWindow().addFlags(0x00080000 | 0x00200000);
        getWindow().setStatusBarColor(Color.rgb(16,25,55));
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(30,30,30,30);
        root.setBackgroundColor(Color.rgb(18,27,60));
        TextView title=new TextView(this);title.setText("⏰ مساعدي — ألارم");title.setTextColor(Color.WHITE);title.setTextSize(26);title.setGravity(Gravity.CENTER);
        TextView text=new TextView(this);text.setText(getIntent().getStringExtra("title"));text.setTextColor(Color.WHITE);text.setTextSize(20);text.setGravity(Gravity.CENTER);text.setPadding(0,30,0,35);
        Button stop=new Button(this);stop.setText("إيقاف الألارم");stop.setOnClickListener(v -> {send(AlarmRingingService.ACTION_STOP);finish();});
        Button snooze=new Button(this);snooze.setText("تأجيل 10 دقائق");snooze.setOnClickListener(v -> {send(AlarmRingingService.ACTION_SNOOZE);finish();});
        root.addView(title);root.addView(text);root.addView(stop);root.addView(snooze);setContentView(root);
    }
    private void send(String action){Intent i=new Intent(this,AlarmRingingService.class);i.setAction(action);i.putExtra("id",getIntent().getStringExtra("id"));i.putExtra("title",getIntent().getStringExtra("title"));startService(i);}
}
