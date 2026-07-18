package com.videoflow.control;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

final class NotificationHelper {
    static final String ALERT_CHANNEL = "video_flow_alerts";
    static final String MONITOR_CHANNEL = "video_flow_monitor";
    private static final String PREFS = "video_flow_client";
    private static final String KEY_SEEN = "seen_notification_ids";
    private static final String KEY_INITIALIZED = "notification_baseline_initialized";

    private NotificationHelper() {}

    static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel alerts = new NotificationChannel(ALERT_CHANNEL, "需要人工处理", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("登录、验证码、风控、实名、封禁和额度提醒");
        alerts.enableVibration(true);
        alerts.setLightColor(Color.rgb(65, 216, 201));
        NotificationChannel monitor = new NotificationChannel(MONITOR_CHANNEL, "后台监控", NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription("保持陳的异常监控连接");
        manager.createNotificationChannel(alerts);
        manager.createNotificationChannel(monitor);
    }

    static Notification monitoringNotification(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, MONITOR_CHANNEL)
                : new Notification.Builder(context);
        return builder.setContentTitle("陳正在后台监控")
                .setContentText("仅在需要人工处理时提醒")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    static void dispatchHumanAlerts(Context context, JSONObject state) {
        JSONArray notes = state.optJSONArray("notifications");
        if (notes == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(prefs.getStringSet(KEY_SEEN, new HashSet<>()));
        boolean initialized = prefs.getBoolean(KEY_INITIALIZED, false);
        int sent = 0;
        for (int i = Math.min(notes.length(), 60) - 1; i >= 0; i--) {
            JSONObject note = notes.optJSONObject(i);
            if (note == null) continue;
            String id = note.optString("id", "");
            if (id.isEmpty() || seen.contains(id)) continue;
            seen.add(id);
            if (initialized && sent < 3 && isHuman(note)) {
                showAlert(context, note, id.hashCode());
                sent++;
            }
        }
        if (seen.size() > 300) {
            Set<String> trimmed = new HashSet<>();
            for (int i = 0; i < Math.min(notes.length(), 200); i++) {
                JSONObject note = notes.optJSONObject(i);
                if (note != null && !note.optString("id", "").isEmpty()) trimmed.add(note.optString("id"));
            }
            seen = trimmed;
        }
        prefs.edit().putStringSet(KEY_SEEN, seen).putBoolean(KEY_INITIALIZED, true).apply();
    }

    static void showTest(Context context) {
        JSONObject note = new JSONObject();
        try {
            note.put("title", "陳通知测试");
            note.put("body", "通知已由陳 Android 应用直接发出，不需要 Bark 或 ntfy。");
        } catch (Exception ignored) {}
        showAlert(context, note, 2402);
    }

    private static boolean isHuman(JSONObject note) {
        String action = note.optString("action", "");
        if ("login_required".equals(action) || "captcha_required".equals(action) || "quota_exhausted".equals(action)) return true;
        String text = note.optString("title", "") + " " + note.optString("body", "");
        return text.matches(".*(登录.*(失效|过期|需要)|验证码|平台验证|风控|实名|封禁|额度.*(耗尽|用完)|免费次数.*用完).*" );
    }

    private static void showAlert(Context context, JSONObject note, int notificationId) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, ALERT_CHANNEL)
                : new Notification.Builder(context);
        Notification notification = builder.setContentTitle(note.optString("title", "陳需要处理"))
                .setContentText(note.optString("body", "请打开应用查看"))
                .setStyle(new Notification.BigTextStyle().bigText(note.optString("body", "请打开应用查看")))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(notificationId, notification);
    }
}
