package com.mosaedy.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** All persisted timestamps are epoch milliseconds. */
public final class ReminderStore {
    private static final String PREF = "mosaedy_native_v1";
    private static final String KEY = "reminders";
    private ReminderStore() { }

    public static synchronized JSONArray all(Context context) {
        String raw = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]");
        try { return new JSONArray(raw); }
        catch (JSONException ignored) { return new JSONArray(); }
    }

    public static synchronized JSONObject find(Context context, String id) {
        JSONArray arr = all(context);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r != null && id.equals(r.optString("id"))) return r;
        }
        return null;
    }

    public static synchronized void put(Context context, JSONObject reminder) throws JSONException {
        String id = reminder.optString("id", "");
        String title = reminder.optString("title", "").trim();
        long due = reminder.optLong("dueAt", 0);
        String repeat = reminder.optString("repeat", "none");
        String mode = reminder.optString("mode", "alarm");
        if (id.isEmpty() || id.length() > 100 || title.isEmpty() || title.length() > 160 ||
            due <= System.currentTimeMillis() ||
            !(repeat.equals("none") || repeat.equals("daily") || repeat.equals("weekly")) ||
            !(mode.equals("notification") || mode.equals("alarm"))) {
            throw new JSONException("Invalid reminder");
        }
        JSONObject clean = new JSONObject();
        clean.put("id", id);
        clean.put("title", title);
        clean.put("dueAt", due);
        clean.put("repeat", repeat);
        clean.put("mode", mode);
        JSONArray current = all(context), next = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject r = current.optJSONObject(i);
            if (r != null && !id.equals(r.optString("id"))) next.put(r);
        }
        next.put(clean);
        save(context, next);
    }

    public static synchronized void remove(Context context, String id) {
        JSONArray current = all(context), next = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject r = current.optJSONObject(i);
            if (r != null && !id.equals(r.optString("id"))) next.put(r);
        }
        save(context, next);
    }

    public static synchronized void updateTime(Context context, String id, long dueAt) {
        JSONArray arr = all(context);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r != null && id.equals(r.optString("id"))) {
                try { r.put("dueAt", dueAt); } catch (JSONException ignored) { }
                break;
            }
        }
        save(context, arr);
    }

    private static void save(Context context, JSONArray data) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY, data.toString()).commit();
    }
}
