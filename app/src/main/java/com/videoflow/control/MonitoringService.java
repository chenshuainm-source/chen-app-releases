package com.videoflow.control;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonitoringService extends Service {
    static final int FOREGROUND_ID = 2401;
    private volatile boolean running;
    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.ensureChannels(this);
        startForeground(FOREGROUND_ID, NotificationHelper.monitoringNotification(this));
        running = true;
        executor = Executors.newSingleThreadExecutor();
        executor.execute(this::monitorLoop);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void monitorLoop() {
        while (running) {
            try {
                SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
                String baseUrl = prefs.getString(MainActivity.KEY_BASE_URL, "");
                String token = prefs.getString(MainActivity.KEY_TOKEN, "");
                if (baseUrl.isEmpty() || token.isEmpty()) {
                    stopSelf();
                    break;
                }
                JSONObject state = ApiClient.getState(baseUrl, token);
                NotificationHelper.dispatchHumanAlerts(this, state);
            } catch (Exception ignored) {}
            try { Thread.sleep(60_000L); }
            catch (InterruptedException ignored) { break; }
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
