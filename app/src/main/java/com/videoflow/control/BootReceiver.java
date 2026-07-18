package com.videoflow.control;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String baseUrl = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).getString(MainActivity.KEY_BASE_URL, "");
        if (baseUrl.isEmpty()) return;
        Intent service = new Intent(context, MonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
        else context.startService(service);
    }
}
