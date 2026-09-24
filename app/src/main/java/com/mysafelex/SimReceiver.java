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
        if (intent == null || !"android.intent.action.SIM_STATE_CHANGED".equals(intent.getAction())) return;
        // goAsync : on garde le receiver en vie pendant l'écriture Firestore.
        final PendingResult pending = goAsync();
        try {
            SharedPreferences prefs = context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
            if (prefs.getString("matricule", "").isEmpty()) { pending.finish(); return; }

            String savedFp = prefs.getString("sim_fp", "");
            String legacySerial = prefs.getString("sim_serial", "");
            if (savedFp.isEmpty() && legacySerial.isEmpty()) { pending.finish(); return; }

            String currentFp = buildSimFingerprint(context);
            if (currentFp.isEmpty()) { pending.finish(); return; }

            boolean changed;
            if (!savedFp.isEmpty()) {
                changed = !currentFp.equals(savedFp);
            } else {
                // Ancien format : compare le serial si on arrive encore à le lire.
                String currentSerial = readSimSerial(context);
                changed = currentSerial != null && !currentSerial.isEmpty()
                        && !currentSerial.equals(legacySerial);
                if (currentSerial == null) { pending.finish(); return; }
            }

            if (!changed) { pending.finish(); return; }

            Log.e("SimReceiver", "CARTE SIM CHANGÉE ! VOL DÉTECTÉ !");
            String matricule = prefs.getString("matricule", "unknown");

            Map<String, Object> alert = new HashMap<>();
            alert.put("status", "vole");
            alert.put("simChanged", true);
            alert.put("alertAt", System.currentTimeMillis());

            AuthManager.ensureSignedIn(new AuthManager.Callback() {
                @Override
                public void onReady(String uid) {
                    try {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("devices").document(matricule)
                                .update(alert)
                                .addOnFailureListener(e -> Log.e("SimReceiver", "Erreur Firestore: " + e.getMessage()))
                                .addOnCompleteListener(t -> pending.finish());
                    } catch (Exception e) {
                        Log.e("SimReceiver", "update a levé: " + e.getMessage());
                        pending.finish();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e("SimReceiver", "Auth anonyme impossible: " + e.getMessage());
                    pending.finish();
                }
            });

            // Déclencher l'alarme locale aussi !
            try {
                Intent serviceIntent = new Intent(context, TheftService.class);
                serviceIntent.setAction("START_THEFT");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e("SimReceiver", "Démarrage alarme impossible: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e("SimReceiver", "Erreur: " + e.getMessage());
            try { pending.finish(); } catch (Exception ignored) {}
        }
    }

    private static String readSimSerial(Context context) {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.READ_PHONE_STATE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return null;
            return tm.getSimSerialNumber();
        } catch (SecurityException se) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildSimFingerprint(Context context) {
        try {
            String serial = readSimSerial(context);
            if (serial != null && !serial.isEmpty()) return "serial:" + serial;
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String op = "", iso = "";
            try { if (tm != null) op = tm.getSimOperator(); } catch (Exception ignored) {}
            try { if (tm != null) iso = tm.getSimCountryIso(); } catch (Exception ignored) {}
            String androidId = android.provider.Settings.Secure.getString(
                    context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            return "op:" + (op != null ? op : "") + "|iso:" + (iso != null ? iso : "")
                    + "|id:" + (androidId != null ? androidId : "");
        } catch (Exception e) {
            return "";
        }
    }
    }
}
