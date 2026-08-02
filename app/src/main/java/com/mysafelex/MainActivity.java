package com.mysafelex;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private Button btnStart, btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStartTheft);
        btnStop = findViewById(R.id.btnStopTheft);

        // Demander les permissions au démarrage
        requestPermissions();

        // Action du bouton DÉCLENCHER
        btnStart.setOnClickListener(v -> {
            Toast.makeText(this, "Mode Vol Activé !", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, TheftService.class);
            intent.setAction("START_THEFT");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        });

        // Action du bouton ARRÊTER
        btnStop.setOnClickListener(v -> {
            Toast.makeText(this, "Mode Vol Arrêté.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, TheftService.class);
            stopService(intent);
        });
    }

    // Fonction pour forcer l'élève à accepter le GPS et la Caméra
    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
        };
        
        boolean needRequest = false;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, 101);
        }
    }
}
