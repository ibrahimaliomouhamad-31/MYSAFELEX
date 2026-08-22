package com.mysafelex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class SimReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.SIM_STATE_CHANGED")) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
                String savedSimSerial = prefs.getString("sim_serial", "");

                if (savedSimSerial.isEmpty()) return;

                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    String currentSimSerial = tm.getSimSerialNumber();

                    // Si la carte SIM a changé
                    if (currentSimSerial != null && !currentSimSerial.equals(savedSimSerial)) {
                        Log.e("SimReceiver", "CARTE SIM CHANGÉE ! VOL DÉTECTÉ !");

                        String matricule = prefs.getString("matricule", "unknown");
                        String simState = tm.getSimState() + "";
                        Map<String, Object> alert = new HashMap<>();
                        alert.put("status", "vole");
                        alert.put("simChanged", true);
                        alert.put("alertAt", System.currentTimeMillis());

                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("devices").document(matricule)
                                .update(alert)
                                .addOnFailureListener(e -> Log.e("SimReceiver", "Erreur Firestore: " + e.getMessage()));

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
