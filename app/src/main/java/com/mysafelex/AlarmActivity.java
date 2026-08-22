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
            String pin = editPin.getText().toString();
            String savedPin = prefs.getString("pin_code", "");

            if (savedPin.isEmpty() || pin.equals(savedPin)) {
                Intent stopIntent = new Intent(this, TheftService.class);
                stopIntent.setAction("STOP_THEFT");
                startService(stopIntent);

                // Prévenir la console Firebase aussi
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("devices").document(matricule)
                        .update("status", "securise");

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
