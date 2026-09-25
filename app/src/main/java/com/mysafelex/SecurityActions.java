package com.mysafelex;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Nouvelles fonctions v1.4 : historique GPS (track/), verrouillage
 * a distance (champ lock), SMS de secours (numero local, sans serveur).
 */
public final class SecurityActions {

    private static final String TAG = "SecurityActions";
    private static final int MAX_TRACK_POINTS = 200;

    private SecurityActions() {}

    /** Enregistre la position dans l'historique track/ (sans faire echouer le reste). */
    public static void saveTrackPoint(String deviceId, Location location) {
        if (deviceId == null || deviceId.isEmpty() || location == null) return;
        try {
            long now = System.currentTimeMillis();
            Map<String, Object> point = new HashMap<>();
            point.put("lat", location.getLatitude());
            point.put("lng", location.getLongitude());
            point.put("at", now);
            try { point.put("acc", location.getAccuracy()); } catch (Exception ignored) {}

            FirebaseFirestore.getInstance()
                    .collection("devices").document(deviceId)
                    .collection("track").document(String.valueOf(now))
                    .set(point)
                    .addOnFailureListener(e -> Log.w(TAG, "track refuse: " + e.getMessage()));
            pruneTrack(deviceId);
        } catch (Exception e) {
            Log.w(TAG, "saveTrackPoint: " + e.getMessage());
        }
    }

    private static void pruneTrack(String deviceId) {
        try {
            FirebaseFirestore.getInstance()
                    .collection("devices").document(deviceId)
                    .collection("track").orderBy("at").limitToLast(MAX_TRACK_POINTS + 20)
                    .get()
                    .addOnSuccessListener(snap -> {
                        try {
                            if (snap.size() > MAX_TRACK_POINTS) {
                                int overflow = snap.size() - MAX_TRACK_POINTS;
                                for (int i = 0; i < overflow && i < snap.size(); i++) {
                                    String id = snap.getDocuments().get(i).getId();
                                    FirebaseFirestore.getInstance()
                                            .collection("devices").document(deviceId)
                                            .collection("track").document(id).delete();
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "nettoyage track: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "lecture track: " + e.getMessage()));
        } catch (Exception e) {
            Log.w(TAG, "pruneTrack: " + e.getMessage());
        }
    }

    /** Commandes de la direction : lock=true verrouille l'ecran, puis acquitte. */
    public static void handleRemoteCommands(Context context, Map<String, Object> data) {
        if (context == null || data == null) return;
        try {
            Object lock = data.get("lock");
            if (lock instanceof Boolean && (Boolean) lock) {
                lockNow(context);
                clearCommand(context, "lock");
            }
        } catch (Exception e) {
            Log.w(TAG, "handleRemoteCommands: " + e.getMessage());
        }
    }

    public static void lockNow(Context context) {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager)
                            context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin =
                    new android.content.ComponentName(context, AdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(admin)) {
                dpm.lockNow();
            } else {
                Log.w(TAG, "lockNow ignore : admin inactif");
            }
        } catch (SecurityException se) {
            Log.w(TAG, "lockNow refuse: " + se.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "lockNow: " + e.getMessage());
        }
    }

    private static void clearCommand(Context context, String field) {
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
            String deviceId = prefs.getString("matricule", "");
            if (deviceId.isEmpty()) return;
            Map<String, Object> updates = new HashMap<>();
            updates.put(field, false);
            FirebaseFirestore.getInstance().collection("devices").document(deviceId)
                    .update(updates)
                    .addOnFailureListener(e -> Log.w(TAG, "acquit " + field + ": " + e.getMessage()));
        } catch (Exception e) {
            Log.w(TAG, "clearCommand: " + e.getMessage());
        }
    }

    /** SMS de secours : envoye quand Firestore est injoignable (zero serveur). */
    public static void sendEmergencySms(Context context, Location location, String status) {
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences("lex_prefs", Context.MODE_PRIVATE);
            String number = prefs.getString("emergency_number", "").trim();
            if (number.isEmpty()) return;
            // Anti-spam : un SMS toutes les 10 minutes maximum.
            long lastSms = prefs.getLong("last_sms_at", 0);
            if (System.currentTimeMillis() - lastSms < 10 * 60 * 1000) return;
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "SMS secours ignore : permission SEND_SMS manquante");
                return;
            }
            String matricule = prefs.getString("matricule", "?");
            String pos = (location != null)
                    ? location.getLatitude() + "," + location.getLongitude()
                    + " (https://maps.google.com/?q="
                    + location.getLatitude() + "," + location.getLongitude() + ")"
                    : "position inconnue";
            String message = "MYSAFELEX [" + matricule + "] " + status
                    + " - Position : " + pos;
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(message);
            if (parts == null || parts.isEmpty()) {
                sms.sendTextMessage(number, null, message, null, null);
            } else {
                sms.sendMultipartTextMessage(number, null, parts, null, null);
            }
            prefs.edit().putLong("last_sms_at", System.currentTimeMillis()).apply();
        } catch (SecurityException se) {
            Log.w(TAG, "SMS refuse: " + se.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "SMS secours: " + e.getMessage());
        }
    }