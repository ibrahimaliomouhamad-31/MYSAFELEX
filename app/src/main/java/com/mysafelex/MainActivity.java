package com.mysafelex;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
    private String currentMatricule = "";
    private long lastClickTime = 0;

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

        currentMatricule = prefs.getString("matricule", "");
        if (!currentMatricule.isEmpty()) {
            editMatricule.setText(currentMatricule);
            editMatricule.setEnabled(false);
        }

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

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.POST_NOTIFICATIONS
                }, 101);
            }
        }

        Intent serviceIntent = new Intent(this, TheftService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Initialisation sécurité...");

        if (currentToken.isEmpty()) {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            txtToken.setText("Erreur de token.");
                            btnLogin.setText("Réessayer");
                            btnLogin.setOnClickListener(v -> startAppSystems());
                            btnLogin.setEnabled(true);
                            return;
                        }
                        currentToken = task.getResult();
                        txtToken.setText("Système de sécurité: ACTIF");
                        btnLogin.setEnabled(true);
                        btnLogin.setText("SAUVEGARDER");
                        setupLoginClickListener();
                    });
        } else {
            txtToken.setText("Système de sécurité: ACTIF");
            btnLogin.setEnabled(true);
            btnLogin.setText("SAUVEGARDER");
            setupLoginClickListener();
        }
    }

    private void setupLoginClickListener() {
        btnLogin.setOnClickListener(v -> {
            if (System.currentTimeMillis() - lastClickTime < 2000) return;
            lastClickTime = System.currentTimeMillis();

            String matricule = editMatricule.getText().toString();
            String code = editCode.getText().toString();

            if(matricule.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
                return;
            }

            String savedPin = prefs.getString("pin_code", "");
            // FAILLE 9 : Vérifier que le PIN correspond AVANT d'arrêter l'alarme
            if (!savedPin.isEmpty() && code.equals(savedPin) && matricule.equals(currentMatricule)) {
                // FAILLE 4 : Arrêt LOCAL en plus de l'arrêt distant (Mode Avion)
                Intent stopIntent = new Intent(this, TheftService.class);
                stopIntent.setAction("STOP_THEFT");
                startService(stopIntent); 
                
                stopTheftRemotely(matricule);
                return;
            } else if (!savedPin.isEmpty()) {
                Toast.makeText(this, "Code PIN incorrect.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentToken.isEmpty()) {
                Toast.makeText(this, "Erreur: Token non initialisé.", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit().putString("matricule", matricule).putString("pin_code", code).apply();
            currentMatricule = matricule;
            enableDeviceAdmin();
            registerStudentInDatabase(matricule);
        });
    }

    private void stopTheftRemotely(String matricule) {
        db.collection("devices").document(matricule)
                .update("status", "securise")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Alarme arrêtée !", Toast.LENGTH_SHORT).show();
                    editCode.setText("");
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Erreur d'arrêt réseau, mais arrêtée localement.", Toast.LENGTH_SHORT).show());
    }

    private void registerStudentInDatabase(String matricule) {
        // FAILLE 1 : Utiliser merge pour ne pas écraser le statut si l'élève existe déjà
        Map<String, Object> studentData = new HashMap<>();
        studentData.put("token", currentToken);

        db.collection("devices").document(matricule)
                .set(studentData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Inscription réussie ! Vous êtes protégé.", Toast.LENGTH_SHORT).show();
                    editMatricule.setEnabled(false);
                })
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
