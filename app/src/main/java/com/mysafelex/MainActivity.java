package com.mysafelex;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
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
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private EditText editMatricule, editCode;
    private Button btnLogin;
    private TextView txtStatus, txtToken;
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
        txtStatus = findViewById(R.id.txtStatusCard);

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

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusCard();
    }

    private void updateStatusCard() {
        boolean registered = !prefs.getString("matricule", "").isEmpty();
        boolean theftActive = prefs.getBoolean("is_theft_active", false);
        if (theftActive) {
            txtStatus.setText("🔴 ALARME ACTIVE — Vol signalé");
        } else if (registered) {
            txtStatus.setText("🟢 Protégé — " + prefs.getString("matricule", ""));
        } else {
            txtStatus.setText("🟠 En attente d'inscription");
        }
    }

    private void showMandatoryLockGuide() {
        new AlertDialog.Builder(this)
                .setTitle("Protection anti-vol LEX")
                .setMessage("Cette application protège votre téléphone en cas de vol : en cas d'alerte, elle peut " +
                        "prendre une photo, enregistrer un son bref et suivre la position de l'appareil, pour " +
                        "aider la direction à le récupérer.\n\n" +
                        "Pour empêcher un voleur de désactiver facilement cette protection, nous vous recommandons " +
                        "d'épingler l'application (elle restera au premier plan tant qu'on ne saisit pas votre code) :\n\n" +
                        "ÉTAPE 1 : Ouvrez les applications récentes (le carré en bas de votre écran).\n" +
                        "ÉTAPE 2 : Restez appuyé sur 'Bloc-note'.\n" +
                        "ÉTAPE 3 : Cliquez sur l'icône du Cadenas 🔒.\n\n" +
                        "Vous pouvez continuer sans épingler l'app, mais la protection sera plus facile à désactiver.")
                .setCancelable(false)
                .setNegativeButton("Continuer sans épingler", (dialog, which) -> {
                    prefs.edit().putBoolean("is_app_locked", true).apply();
                    startAppSystems();
                })
                .setPositiveButton("J'ai mis le cadenas", (dialog, which) -> {
                    prefs.edit().putBoolean("is_app_locked", true).apply();
                    Toast.makeText(this, "Merci ! L'application s'active.", Toast.LENGTH_SHORT).show();
                    startAppSystems();
                })
                .show();
    }

    private void startAppSystems() {
        // L'authentification doit être prête avant toute lecture/écriture Firestore :
        // les règles de sécurité refusent désormais les accès non authentifiés.
        AuthManager.ensureSignedIn(new AuthManager.Callback() {
            @Override
            public void onReady(String uid) {
                startAppSystemsAuthenticated();
            }

            @Override
            public void onError(Exception e) {
                Log.e("MainActivity", "Auth anonyme impossible", e);
                Toast.makeText(MainActivity.this,
                        "Erreur réseau, impossible d'activer la protection pour le moment.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startAppSystemsAuthenticated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_PHONE_STATE,
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
                        txtToken.setText("Système de sécurité: ACTIF ✔");
                        btnLogin.setEnabled(true);
                        btnLogin.setText("SAUVEGARDER");
                        setupLoginClickListener();
                    });
        } else {
            txtToken.setText("Système de sécurité: ACTIF ✔");
            btnLogin.setEnabled(true);
            btnLogin.setText("SAUVEGARDER");
            setupLoginClickListener();
        }
        updateStatusCard();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 101) return;

        java.util.List<String> denied = new java.util.ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                denied.add(permissions[i]);
            }
        }
        if (!denied.isEmpty()) {
            Toast.makeText(this,
                    "Certaines permissions ont été refusées : la protection sera incomplète (" + denied.size() + " manquante(s)).",
                    Toast.LENGTH_LONG).show();
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
            if (!savedPin.isEmpty() && code.equals(savedPin) && matricule.equals(currentMatricule)) {
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

            enableDeviceAdmin();
            registerStudentInDatabase(matricule, code);
        });
    }

    private void stopTheftRemotely(String matricule) {
        db.collection("devices").document(matricule)
                .update("status", "securise")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Alarme arrêtée !", Toast.LENGTH_SHORT).show();
                    editCode.setText("");
                    updateStatusCard();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Erreur d'arrêt réseau, mais arrêtée localement.", Toast.LENGTH_SHORT).show());
    }

    private void registerStudentInDatabase(String matricule, String code) {
        String uid = AuthManager.getCurrentUidOrNull();
        if (uid == null) {
            Toast.makeText(this, "Erreur d'authentification, réessayez.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> studentData = new HashMap<>();
        studentData.put("ownerUid", uid);
        studentData.put("token", currentToken);
        studentData.put("status", "securise");
        studentData.put("lat", null);
        studentData.put("lng", null);

        db.collection("devices").document(matricule)
                .set(studentData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    prefs.edit().putString("matricule", matricule).putString("pin_code", code).apply();
                    currentMatricule = matricule;

                    // SAUVEGARDER LE NUMÉRO DE SÉRIE DE LA CARTE SIM LÉGITIME
                    try {
                        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                        if (tm != null && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                            String simSerial = tm.getSimSerialNumber();
                            if (simSerial != null) {
                                prefs.edit().putString("sim_serial", simSerial).apply();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Toast.makeText(this, "Inscription réussie ! Vous êtes protégé.", Toast.LENGTH_SHORT).show();
                    editMatricule.setEnabled(false);
                    updateStatusCard();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Erreur d'inscription réseau.", Toast.LENGTH_SHORT).show());
    }

    private void enableDeviceAdmin() {
        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);

        if (!dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
        } else {
            Toast.makeText(this, "Protection active !", Toast.LENGTH_SHORT).show();
        }
    }
}
