package com.mysafelex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            
            // Quand le téléphone s'allume, on vérifie si l'alarme doit sonner
            SharedPreferences prefs = context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
            boolean isTheftActive = prefs.getBoolean("is_theft_active", false);
            
            if (isTheftActive) {
                // Si le téléphone était volé, on relance le service d'alarme !
                Intent serviceIntent = new Intent(context, TheftService.class);
                serviceIntent.setAction("START_THEFT");
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                // Si le téléphone est normal, on lance juste l'écoute en fond
                Intent serviceIntent = new Intent(context, TheftService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
