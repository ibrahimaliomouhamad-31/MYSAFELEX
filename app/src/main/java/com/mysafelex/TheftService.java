package com.mysafelex;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class TheftService extends Service implements LocationListener {

    private MediaPlayer alarmPlayer;
    private LocationManager locationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // Créer le canal de notification (obligatoire pour Android moderne)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "lex_channel", "LEX Sécurité", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Démarrer en tant que service de premier plan (pour ne pas être tué)
        Notification notification = new NotificationCompat.Builder(this, "lex_channel")
                .setContentTitle("MYSAFELEX Actif")
                .setContentText("Suivi anti-vol en cours...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
        startForeground(1, notification);

        // 1. Déclencher l'alarme sonore en boucle
        if (alarmPlayer == null) {
            alarmPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_URI);
            if (alarmPlayer != null) {
                alarmPlayer.setLooping(true);
                alarmPlayer.setVolume(1.0f, 1.0f);
                alarmPlayer.start();
            }
        }

        // 2. Démarrer le GPS
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 5, this); // 30 sec, 5 metres
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Arrêter l'alarme
        if (alarmPlayer != null) {
            alarmPlayer.stop();
            alarmPlayer.release();
            alarmPlayer = null;
        }
        // Arrêter le GPS
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // ICI : Le code pour envoyer la position à l'admin (Firebase)
        // double lat = location.getLatitude();
        // double lng = location.getLongitude();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
