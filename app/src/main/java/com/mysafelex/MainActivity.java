package com.mysafelex;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText editMatricule, editCode;
    private Button btnLogin;
    private TextView txtToken;
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    private SharedPreferences prefs;
    private FirebaseFirestore db;
    private String currentToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("lex_prefs", MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();

        editMatricule = findViewById(R.id.editMatricule);
        editCode = findViewById(R.id.editInviteCode);
        btnLogin = findViewById(R.id.btnLogin);
        txtToken = findViewById(R.id.txtToken);

        boolean isLocked = prefs.getBoolean("is_app_locked", false);
        if (!isLocked) {
            showMandatoryLockGuide();
        } else {
            startAppSystems();
        }
    }

    private void showMandatoryLockGuide() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ CONFIGURATION OBLIGATOIRE")
                .setMessage("Pour que l'alarme anti-vol fonctionne, Android exige que vous verrouilliez cette application.\n\n" +
                        "ÉTAPE 1 : Cliquez sur 'Ouvrir les applications récentes' (le carré en bas de votre écran).\n" +
                        "ÉTAPE 2 : Restez appuyé sur 'Bloc-note'.\n" +
                        "ÉTAPE 3 : Cliquez sur l'icône du Cadenas 🔒.\n\n" +
                        "Tant que vous n'aurez pas fait cela, l'application refusera de s'activer.")
                .setCancelable(false)
                .setPositiveButton("J'ai mis le cadenas", (dialog, which) -> {
                    prefs.edit().putBoolean("is_app_locked", true).apply();
                    Toast.makeText(this, "Merci ! L'application s'active.", Toast.LENGTH_SHORT).show();
                    startAppSystems();
                })
                .show();
    }

    private void startAppSystems() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        Intent serviceIntent = new Intent(this, TheftService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        txtToken.setText("Erreur de token");
                        return;
                    }
                    currentToken = task.getResult();
                    txtToken.setText("Token: " + currentToken);
                });

        btnLogin.setOnClickListener(v -> {
            String matricule = editMatricule.getText().toString();
            String code = editCode.getText().toString();

            if(matricule.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
            } else {
                // Sauvegarder les infos
                prefs.edit().putString("matricule", matricule).putString("pin_code", code).apply();
                enableDeviceAdmin();
                
                // INSCRIRE L'ÉLÈVE DANS LA BASE DE DONNÉES
                registerStudentInDatabase(matricule);
            }
        });
    }

    private void registerStudentInDatabase(String matricule) {
        Map<String, Object> studentData = new HashMap<>();
        studentData.put("status", "securise");
        studentData.put("lat", null);
        studentData.put("lng", null);
        studentData.put("photoBase64", null);
        studentData.put("token", currentToken);

        db.collection("devices").document(matricule)
                .set(studentData)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Inscription réussie ! Vous êtes protégé.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Erreur d'inscription.", Toast.LENGTH_SHORT).show());
    }

    private void enableDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);

        if (!dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
        } else {
            Toast.makeText(this, "Protection active !", Toast.LENGTH_SHORT).show();
        }
    }
}
