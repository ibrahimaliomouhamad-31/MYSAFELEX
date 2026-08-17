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
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences prefs = context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
                boolean isTheftActive = prefs.getBoolean("is_theft_active", false);
                
                Intent serviceIntent = new Intent(context, TheftService.class);
                if (isTheftActive) {
                    serviceIntent.setAction("START_THEFT");
                }
                
                // FAILLE 4 : Try-catch pour empêcher le crash sur Android 12+
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } catch (Exception e) {
                    Log.e("BootReceiver", "Erreur de lancement service: " + e.getMessage());
                }
            }, 5000);
        }
    }
}
