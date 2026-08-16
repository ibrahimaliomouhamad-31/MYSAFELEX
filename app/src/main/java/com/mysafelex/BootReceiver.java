package com.mysafelex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            
            // FAILLE 3 : Attendre 5 secondes que le réseau s'initialise avant de lancer le service
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences prefs = context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
                boolean isTheftActive = prefs.getBoolean("is_theft_active", false);
                
                Intent serviceIntent = new Intent(context, TheftService.class);
                if (isTheftActive) {
                    serviceIntent.setAction("START_THEFT");
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }, 5000); // 5 secondes de délai
        }
    }
}
