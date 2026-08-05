package com.mysafelex;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class TheftService extends Service implements LocationListener {

    private Ringtone ringtone;
    private LocationManager locationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "lex_channel", "MYSAFELEX", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, "lex_channel")
                .setContentTitle("MYSAFELEX Actif")
                .setContentText("Suivi anti-vol en cours...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
        startForeground(1, notification);

        // 1. Forcer le volume au max
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0);
            maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVol, 0);
        }

        // 2. Utiliser la méthode native Ringtone (infaillible)
        if (ringtone == null) {
            try {
                Uri alarmSound = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
                if (alarmSound == null) {
                    alarmSound = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
                }
                
                ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmSound);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }
                
                // Forcer le son à sortir même en silencieux
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                ringtone.setAudioAttributes(audioAttributes);
                
                ringtone.play();
                Toast.makeText(this, "Alarme lancée!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        // 3. GPS
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 5, this);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
            ringtone = null;
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    @Override
    public void onLocationChanged(Location location) {}
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override
    public void onProviderEnabled(String provider) {}
    @Override
    public void onProviderDisabled(String provider) {}

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
