package com.mysafelex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action);
        if (!boot) return;

        final PendingResult pending = goAsync();
        try {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    SharedPreferences prefs = context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
                    if (prefs.getString("matricule", "").isEmpty()) {
                        pending.finish();
                        return;
                    }
                    boolean theftActive = prefs.getBoolean("is_theft_active", false);
                    Intent serviceIntent = new Intent(context, TheftService.class);
                    if (theftActive) {
                        serviceIntent.setAction("START_THEFT");
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent);
                        } else {
                            context.startService(serviceIntent);
                        }
                    } catch (Exception e) {
                        Log.e("BootReceiver", "Erreur de lancement service: " + e.getMessage());
                    } finally {
                        try { pending.finish(); } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    Log.e("BootReceiver", "Erreur: " + e.getMessage());
                    try { pending.finish(); } catch (Exception ignored) {}
                }
            }, 5000);
        } catch (Exception e) {
            Log.e("BootReceiver", "Erreur: " + e.getMessage());
            try { pending.finish(); } catch (Exception ignored) {}
        }
    }
}
