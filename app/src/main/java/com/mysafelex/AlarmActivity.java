package com.mysafelex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AlarmActivity extends AppCompatActivity {

    private EditText editPin;
    private Button btnStop;
    private TextView txtInfo;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        prefs = getSharedPreferences("lex_prefs", MODE_PRIVATE);
        editPin = findViewById(R.id.editAlarmPin);
        btnStop = findViewById(R.id.btnStopAlarm);
        txtInfo = findViewById(R.id.txtAlarmInfo);

        String matricule = prefs.getString("matricule", "—");
        txtInfo.setText("Matricule : " + matricule + "\nEntrez votre code PIN pour arrêter l'alarme.");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        btnStop.setOnClickListener(v -> {
            String pin = editPin.getText().toString().trim();
            String savedPinHash = prefs.getString("pin_hash", "");
            String legacyPin = prefs.getString("pin_code", "");
            // Migration une seule fois : PIN clair -> hash.
            if (savedPinHash.isEmpty() && !legacyPin.isEmpty()) {
                try {
                    savedPinHash = SecurityUtils.hashPin(this, legacyPin);
                    prefs.edit().putString("pin_hash", savedPinHash).remove("pin_code").apply();
                } catch (Exception e) {
                    android.util.Log.e("AlarmActivity", "Migration PIN impossible", e);
                }
            }

            if (savedPinHash.isEmpty()) {
                Toast.makeText(this, "Aucun code enregistré. Contactez la direction.", Toast.LENGTH_LONG).show();
                return;
            }

            if (SecurityUtils.verifyPin(this, pin, savedPinHash)) {
                Intent stopIntent = new Intent(this, TheftService.class);
                stopIntent.setAction("STOP_THEFT");
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(stopIntent);
                    } else {
                        startService(stopIntent);
                    }
                } catch (Exception e) {
                    android.util.Log.e("AlarmActivity", "STOP_THEFT impossible: " + e.getMessage());
                }

                // Prévenir la console Firebase aussi
                try {
                    String stopMatricule = prefs.getString("matricule", "");
                    if (!stopMatricule.isEmpty()) {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("devices").document(stopMatricule)
                                .update("status", "securise")
                                .addOnFailureListener(e -> android.util.Log.w("AlarmActivity",
                                        "MAJ Firestore refusée: " + e.getMessage()));
                    }
                } catch (Exception e) {
                    android.util.Log.e("AlarmActivity", "Firestore: " + e.getMessage());
                }

                Toast.makeText(this, "Alarme arrêtée ✅", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Code PIN incorrect ❌", Toast.LENGTH_SHORT).show();
                editPin.setText("");
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Empêcher de fermer l'écran pendant l'alarme
    }
}
