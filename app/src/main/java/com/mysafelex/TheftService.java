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
import androidx.core.app.ActivityCompat;
import android.Manifest;

public class TheftService extends Service {

    private Ringtone ringtone;
    private FirebaseFirestore db;
    private String deviceId;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean isTheftActive = false;

    // LE GARDIEN DU VOLUME
    private Handler volumeHandler;
    private Runnable volumeRunnable;

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
        
        SharedPreferences prefs = getSharedPreferences("lex_prefs", MODE_PRIVATE);
        deviceId = prefs.getString("matricule", "unknown_device");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (android.location.Location location : locationResult.getLocations()) {
                    db.collection("devices").document(deviceId)
                            .update("lat", location.getLatitude(), "lng", location.getLongitude());
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, "lex_channel")
                .setContentTitle("Synchronisation...")
                .setContentText("Services système en cours.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
        startForeground(1, notification);

        if (intent != null && intent.getAction() != null && intent.getAction().equals("START_THEFT")) {
            triggerAlarmAndGPS();
        }

        db.collection("devices").document(deviceId)
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

        return START_STICKY;
    }

    private void triggerAlarmAndGPS() {
        if (isTheftActive) return;
        isTheftActive = true;

        // 1. Prendre la photo
        CameraHelper.takeSecretPhoto(this, deviceId);

        // 2. Verrouiller l'écran
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.lockNow();
        }

        // 3. Déclencher l'alarme
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

        // 4. ACTIVER LE GARDIEN DU VOLUME (Contre-attaque)
        volumeHandler = new Handler(Looper.getMainLooper());
        volumeRunnable = new Runnable() {
            @Override
            public void run() {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null && isTheftActive) {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    // Si le voleur a baissé le volume, on le remet à fond immédiatement !
                    if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) < maxVol) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0);
                    }
                    // Revérifier dans 1 seconde
                    volumeHandler.postDelayed(this, 1000);
                }
            }
        };
        volumeHandler.post(volumeRunnable);

        // 5. Démarrer le GPS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                    .setMinUpdateIntervalMillis(2000)
                    .setMinUpdateDistanceMeters(1)
                    .build();
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void stopAlarmAndGPS() {
        isTheftActive = false;

        // Arrêter le Gardien du Volume
        if (volumeHandler != null) {
            volumeHandler.removeCallbacks(volumeRunnable);
        }

        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
            ringtone = null;
        }
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAlarmAndGPS();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
