package com.mysafelex;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.Map;

public class TheftService extends Service {

    private Ringtone ringtone;
    private FirebaseFirestore db;
    private String deviceId;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean isTheftActive = false;
    private Handler volumeHandler;
    private Runnable volumeRunnable;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;
    private ListenerRegistration registration;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "lex_channel", "MYSAFELEX", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        db = FirebaseFirestore.getInstance();
        
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build();
        db.setFirestoreSettings(settings);
        
        prefs = getSharedPreferences("lex_prefs", MODE_PRIVATE);
        deviceId = prefs.getString("matricule", "unknown_device");

        // FAILLE 2 : Blindage du GPS (Try-catch pour les téléphones sans Google Play)
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    for (android.location.Location location : locationResult.getLocations()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("lat", location.getLatitude());
                        data.put("lng", location.getLongitude());
                        db.collection("devices").document(deviceId).update(data);
                    }
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, "lex_channel")
                .setContentTitle("Synchronisation...")
                .setContentText("Services système en cours.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
        startForeground(1, notification);

        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("START_THEFT")) {
                triggerAlarmAndGPS();
            } else if (intent.getAction().equals("STOP_THEFT")) {
                stopAlarmAndGPS();
                return START_NOT_STICKY;
            }
        }

        if (registration == null) {
            registration = db.collection("devices").document(deviceId)
                    .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                        @Override
                        public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                            if (e != null || snapshot == null || !snapshot.exists()) return;
                            String status = snapshot.getString("status");
                            if (status != null && status.equals("vole")) {
                                triggerAlarmAndGPS();
                            } else if (status != null && status.equals("securise")) {
                                stopAlarmAndGPS();
                            }
                        }
                    });
        }

        return START_STICKY;
    }

    private void triggerAlarmAndGPS() {
        if (isTheftActive) return;
        isTheftActive = true;
        
        prefs.edit().putBoolean("is_theft_active", true).apply();

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "MYSAFELEX::AlarmWakeLock");
            wakeLock.acquire();
        }

        CameraHelper.takeSecretPhoto(this, deviceId);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                dpm.lockNow();
            }
        }, 500);

        if (ringtone == null) {
            try {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0);
                }
                Uri alarmSound = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
                if (alarmSound == null) {
                    alarmSound = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
                }
                ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmSound);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                ringtone.setAudioAttributes(audioAttributes);
                ringtone.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        volumeHandler = new Handler(Looper.getMainLooper());
        volumeRunnable = new Runnable() {
            @Override
            public void run() {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null && isTheftActive) {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) < maxVol) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0);
                    }
                    volumeHandler.postDelayed(this, 1000);
                }
            }
        };
        volumeHandler.post(volumeRunnable);

        // FAILLE 2 : Sécuriser le lancement du GPS
        if (fusedLocationClient != null && locationCallback != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000)
                        .setMinUpdateIntervalMillis(10000)
                        .setMinUpdateDistanceMeters(5)
                        .build();
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            } catch (SecurityException | Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void stopAlarmAndGPS() {
        isTheftActive = false;
        prefs.edit().putBoolean("is_theft_active", false).apply();

        if (volumeHandler != null) {
            volumeHandler.removeCallbacks(volumeRunnable);
        }
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
            ringtone = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAlarmAndGPS();
        
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
