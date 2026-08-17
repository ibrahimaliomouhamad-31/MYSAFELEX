package com.mysafelex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.telephony.SmsManager;
import android.util.Log;

public class SimReceiver extends BroadcastReceiver {

    // REMPLACE PAR TON NUMÉRO (Ex: +22788724272)
    private static final String ADMIN_PHONE = "+22788724272";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.SIM_STATE_CHANGED")) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
                String savedSimSerial = prefs.getString("sim_serial", "");
                boolean isTheftActive = prefs.getBoolean("is_theft_active", false);

                if (savedSimSerial.isEmpty()) return;

                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    String currentSimSerial = tm.getSimSerialNumber();
                    
                    // Si la carte SIM a changé
                    if (currentSimSerial != null && !currentSimSerial.equals(savedSimSerial)) {
                        Log.e("SimReceiver", "CARTE SIM CHANGÉE ! VOL DÉTECTÉ !");
                        
                        // Récupérer le numéro de téléphone du voleur si possible
                        String thiefNumber = tm.getLine1Number(); 
                        if (thiefNumber == null || thiefNumber.isEmpty()) {
                            thiefNumber = "Inconnu";
                        }

                        // Envoyer un SMS secret à l'admin
                        if (!ADMIN_PHONE.equals("TON_NUMERO_TELEPHONE")) {
                            SmsManager smsManager = SmsManager.getDefault();
                            String message = "🚨 MYSAFELEX ALERT ! Vol de SIM détecté au LEX.\nMatricule: " + prefs.getString("matricule", "Inconnu") + "\nNouveau Numéro: " + thiefNumber;
                            smsManager.sendTextMessage(ADMIN_PHONE, null, message, null, null);
                        }

                        // Déclencher l'alarme locale aussi !
                        Intent serviceIntent = new Intent(context, TheftService.class);
                        serviceIntent.setAction("START_THEFT");
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent);
                        } else {
                            context.startService(serviceIntent);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("SimReceiver", "Erreur: " + e.getMessage());
            }
        }
    }
}
