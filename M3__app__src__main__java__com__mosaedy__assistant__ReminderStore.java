package com.mosaedy.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Local reminder storage. Timestamps are epoch milliseconds; full notes are preserved. */
public final class ReminderStore {
    private static final String PREF = "mosaedy_native_v1";
    private static final String KEY = "reminders";
    private static final String HISTORY_KEY = "history";
    private ReminderStore() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public static synchronized JSONArray all(Context context) {
        try { return new JSONArray(prefs(context).getString(KEY, "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }
    public static synchronized JSONArray history(Context context) {
        try { return new JSONArray(prefs(context).getString(HISTORY_KEY, "[]")); }
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
            !(repeat.equals("none") || repeat.equals("daily") || repeat.equals("weekly") || repeat.equals("monthly")) ||
            !(mode.equals("notification") || mode.equals("alarm"))) throw new JSONException("Invalid reminder");
        JSONObject clean = new JSONObject();
        clean.put("id", id); clean.put("title", title); clean.put("dueAt", due);
        clean.put("repeat", repeat); clean.put("mode", mode);
        clean.put("notes", reminder.optString("notes", ""));
        clean.put("status", reminder.optString("status", "upcoming"));
        clean.put("createdAt", reminder.optLong("createdAt", System.currentTimeMillis()));
        JSONArray current = all(context), next = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject r = current.optJSONObject(i);
            if (r != null && !id.equals(r.optString("id"))) next.put(r);
        }
        next.put(clean); prefs(context).edit().putString(KEY, next.toString()).commit();
        removeFromHistory(context, id);
    }
    public static synchronized void updateTime(Context context, String id, long dueAt, String status) {
        JSONArray arr = all(context);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r != null && id.equals(r.optString("id"))) {
                try { r.put("dueAt", dueAt); r.put("status", status == null ? "upcoming" : status); }
                catch (JSONException ignored) { }
                break;
            }
        }
        prefs(context).edit().putString(KEY, arr.toString()).commit();
    }
    public static synchronized void setStatus(Context context, String id, String status) {
        JSONArray arr = all(context);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r != null && id.equals(r.optString("id"))) {
                try { r.put("status", status); } catch (JSONException ignored) { }
                break;
            }
        }
        prefs(context).edit().putString(KEY, arr.toString()).commit();
    }
    public static synchronized void archive(Context context, String id, String status) {
        JSONObject found = find(context, id);
        if (found == null) return;
        try { found.put("status", status); found.put("finishedAt", System.currentTimeMillis()); }
        catch (JSONException ignored) { }
        JSONArray hist = history(context), next = new JSONArray();
        for (int i = 0; i < hist.length(); i++) {
            JSONObject item = hist.optJSONObject(i);
            if (item != null && !id.equals(item.optString("id"))) next.put(item);
        }
        next.put(found);
        while (next.length() > 500) {
            JSONArray trimmed = new JSONArray();
            for (int i = 1; i < next.length(); i++) trimmed.put(next.opt(i));
            next = trimmed;
        }
        prefs(context).edit().putString(HISTORY_KEY, next.toString()).commit();
        remove(context, id);
    }
    public static synchronized void remove(Context context, String id) {
        JSONArray current = all(context), next = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject r = current.optJSONObject(i);
            if (r != null && !id.equals(r.optString("id"))) next.put(r);
        }
        prefs(context).edit().putString(KEY, next.toString()).commit();
    }
    public static synchronized void removeFromHistory(Context context, String id) {
        JSONArray current = history(context), next = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item != null && !id.equals(item.optString("id"))) next.put(item);
        }
        prefs(context).edit().putString(HISTORY_KEY, next.toString()).commit();
    }
    public static synchronized void replaceHistory(Context context, JSONArray items) {
        JSONArray safe = items == null ? new JSONArray() : items;
        while (safe.length() > 500) {
            JSONArray trimmed = new JSONArray();
            for (int i = safe.length() - 500; i < safe.length(); i++) trimmed.put(safe.opt(i));
            safe = trimmed;
        }
        prefs(context).edit().putString(HISTORY_KEY, safe.toString()).commit();
    }
    public static synchronized void clearHistory(Context context) {
        prefs(context).edit().putString(HISTORY_KEY, "[]").commit();
    }
}
