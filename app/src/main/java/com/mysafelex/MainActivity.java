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
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText editMatricule, editCode;
    private Button btnLogin, btnHelp;
    private TextView txtStatus, txtStatusTitle, txtToken;
    private android.view.View dotStatus;
    private TextView txtShieldLoc, txtShieldCam, txtShieldSim, txtShieldAdmin;
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    private static final int REQUEST_CODE_PERMS = 101;
    private static final String TAG = "MainActivity";
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
        btnHelp = findViewById(R.id.btnHelp);
        txtToken = findViewById(R.id.txtToken);
        txtStatus = findViewById(R.id.txtStatusCard);
        txtStatusTitle = findViewById(R.id.txtStatusTitle);
        dotStatus = findViewById(R.id.dotStatus);
        txtShieldLoc = findViewById(R.id.txtShieldLoc);
        txtShieldCam = findViewById(R.id.txtShieldCam);
        txtShieldSim = findViewById(R.id.txtShieldSim);
        txtShieldAdmin = findViewById(R.id.txtShieldAdmin);
        if (btnHelp != null) {
            btnHelp.setOnClickListener(v -> showHelpDialog());
        }

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
            setStatus("ALARME ACTIVE", "Vol signale - alarme et suivi en cours.",
                    R.drawable.dot_red);
        } else if (registered) {
            setStatus("Protege", "Matricule " + prefs.getString("matricule", ""),
                    R.drawable.dot_green);
        } else {
            setStatus("En attente d inscription",
                    "Renseignez votre matricule pour activer la protection.",
                    R.drawable.dot_orange);
        }
        refreshShield();
    }

    private void setStatus(String title, String subtitle, int dotRes) {
        try {
            if (txtStatusTitle != null) txtStatusTitle.setText(title);
            if (txtStatus != null) txtStatus.setText(subtitle);
            if (dotStatus != null) dotStatus.setBackgroundResource(dotRes);
        } catch (Exception e) {
            Log.e(TAG, "setStatus: " + e.getMessage());
        }
    }

    private void refreshShield() {
        try {
            boolean loc = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean cam = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            boolean sim = !prefs.getString("sim_fp", "").isEmpty()
                    || !prefs.getString("sim_serial", "").isEmpty();
            boolean admin = isDeviceAdminActive();
            if (txtShieldLoc != null) txtShieldLoc.setText((loc ? "OK  " : "KO  ") + "Localisation");
            if (txtShieldCam != null) txtShieldCam.setText((cam ? "OK  " : "KO  ") + "Photo secrete");
            if (txtShieldSim != null) txtShieldSim.setText((sim ? "OK  " : "--  ") + "Alerte changement SIM");
            if (txtShieldAdmin != null) txtShieldAdmin.setText((admin ? "OK  " : "KO  ") + "Verrouillage appareil");
            int ok = getColorCompat(R.color.lex_success);
            int ko = getColorCompat(R.color.lex_muted);
            if (txtShieldLoc != null) txtShieldLoc.setTextColor(loc ? ok : ko);
            if (txtShieldCam != null) txtShieldCam.setTextColor(cam ? ok : ko);
            if (txtShieldSim != null) txtShieldSim.setTextColor(sim ? ok : ko);
            if (txtShieldAdmin != null) txtShieldAdmin.setTextColor(admin ? ok : ko);
        } catch (Exception e) {
            Log.e(TAG, "refreshShield: " + e.getMessage());
        }
    }

    private boolean isDeviceAdminActive() {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
            return dpm != null && dpm.isAdminActive(adminComponent);
        } catch (Exception e) {
            return false;
        }
    }

    private int getColorCompat(int resId) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return getColor(resId);
            } else {
                //noinspection deprecation
                return getResources().getColor(resId);
            }
        } catch (Exception e) {
            return 0xFF6B7280;
        }
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Comment ca marche ?")
                .setMessage("1) Inscrivez votre matricule LEX et choisissez un code PIN.\n\n" +
                        "2) Gardez la localisation, la camera et l acces SIM actives : " +
                        "le bouclier ci-dessus doit etre tout vert.\n\n" +
                        "3) En cas de vol, la direction declenche l alarme a distance : " +
                        "sonnerie forte, photo secrete et position envoyees.\n\n" +
                        "4) Seul votre code PIN arrete l alarme, ici ou sur l ecran rouge.")
                .setPositiveButton("Compris", null)
                .show();
    }

    private void showMandatoryLockGuide() {
        new AlertDialog.Builder(this)
                .setTitle("Protection anti-vol LEX")
                .setMessage("Cette application protège votre téléphone en cas de vol : en cas d'alerte, elle peut " +
                        "prendre une photo et suivre la position de l'appareil, pour " +
                        "aider la direction à le récupérer.\n\n" +
                        "Pour empêcher un voleur de désactiver facilement cette protection, nous vous recommandons " +
                        "d'épingler l'application (elle restera au premier plan tant qu'on ne saisit pas votre code) :\n\n" +
                        "ÉTAPE 1 : Ouvrez les applications récentes (le carré en bas de votre écran).\n" +
                        "ÉTAPE 2 : Restez appuyé sur 'MYSAFELEX'.\n" +
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
                Log.e(TAG, "Auth anonyme impossible", e);
                Toast.makeText(MainActivity.this,
                        "Erreur réseau, impossible d'activer la protection pour le moment.",
                        Toast.LENGTH_LONG).show();
                txtToken.setText("Erreur réseau — touchez RÉESSAYER pour réessayer.");
                btnLogin.setEnabled(true);
                btnLogin.setText("RÉESSAYER");
                btnLogin.setOnClickListener(v -> startAppSystems());
            }
        });
    }

    private void startAppSystemsAuthenticated() {
        requestBatteryExemptionIfNeeded();
        requestMissingPermissions();

        Intent serviceIntent = new Intent(this, TheftService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Démarrage TheftService impossible: " + e.getMessage());
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Initialisation sécurité...");

        if (currentToken.isEmpty()) {
            try {
                FirebaseMessaging.getInstance().getToken()
                        .addOnCompleteListener(task -> {
                            if (!task.isSuccessful() || task.getResult() == null) {
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
                            prefillEmergencyNumber();
                            setupLoginClickListener();
                        });
            } catch (Exception e) {
                Log.e(TAG, "getToken a levé", e);
                txtToken.setText("Erreur de token.");
                btnLogin.setText("Réessayer");
                btnLogin.setEnabled(true);
                btnLogin.setOnClickListener(v -> startAppSystems());
            }
        } else {
            txtToken.setText("Système de sécurité: ACTIF ✔");
            btnLogin.setEnabled(true);
            btnLogin.setText("SAUVEGARDER");
            prefillEmergencyNumber();
            setupLoginClickListener();
        }
        updateStatusCard();
    }

    private void requestBatteryExemptionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) return;
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exemption batterie impossible: " + e.getMessage());
        }
    }

    private void prefillEmergencyNumber() {
        try {
            android.widget.EditText editEmergency = findViewById(R.id.editEmergencyNumber);
            if (editEmergency != null) {
                String saved = prefs.getString("emergency_number", "");
                if (!saved.isEmpty()) editEmergency.setText(saved);
            }
        } catch (Exception e) {
            Log.e(TAG, "prefillEmergency: " + e.getMessage());
        }
    }

    private String readEmergencyNumber() {
        try {
            android.widget.EditText editEmergency = findViewById(R.id.editEmergencyNumber);
            if (editEmergency != null && editEmergency.getText() != null) {
                String n = editEmergency.getText().toString().replaceAll("[^0-9+]", "");
                if (n.length() >= 8 && n.length() <= 20) return n;
                if (!n.isEmpty()) {
                    Toast.makeText(this, "Numero de secours invalide (8-20 chiffres).",
                            Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "readEmergency: " + e.getMessage());
        }
        return prefs.getString("emergency_number", "");
    }

    private void requestMissingPermissions() {
        java.util.List<String> needed = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
                && !prefs.getString("emergency_number", "").isEmpty()) {
            // SMS de secours : demandé seulement si un numero est enregistré.
            needed.add(Manifest.permission.SEND_SMS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) {
            try {
                ActivityCompat.requestPermissions(this,
                        needed.toArray(new String[0]), REQUEST_CODE_PERMS);
            } catch (Exception e) {
                Log.e(TAG, "requestPermissions a levé: " + e.getMessage());
            }
        }
    }

    private void maybeRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle("Localisation en arrière-plan")
                        .setMessage("Pour retrouver le téléphone même quand l'app est fermée, " +
                                "autorisez « Tout le temps » à l'étape suivante.")
                        .setPositiveButton("Continuer", (d, w) ->
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 102))
                        .setNegativeButton("Plus tard", null)
                        .show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Demande background location impossible: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMS) {
            java.util.List<String> denied = new java.util.ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                if (i >= grantResults.length || grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    denied.add(permissions[i]);
                }
            }
            if (!denied.isEmpty()) {
                Toast.makeText(this,
                        "Permissions refusées (" + denied.size() + ") : protection incomplète. " +
                                "Réactivez-les dans Réglages > Apps > Bloc-note > Autorisations.",
                        Toast.LENGTH_LONG).show();
            } else {
                maybeRequestBackgroundLocation();
            }
            return;
        }
        if (requestCode == 102) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Localisation arrière-plan activée ✔", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Localisation « Tout le temps » refusée : suivi limité.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupLoginClickListener() {
        btnLogin.setOnClickListener(v -> {
            if (System.currentTimeMillis() - lastClickTime < 2000) return;
            lastClickTime = System.currentTimeMillis();

            String matricule = editMatricule.getText().toString().trim();
            String code = editCode.getText().toString().trim();

            if (matricule.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!SecurityUtils.isValidMatricule(matricule)) {
                Toast.makeText(this, "Matricule invalide (3-30 lettres/chiffres).", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!SecurityUtils.isValidPin(code)) {
                Toast.makeText(this, "Code PIN invalide (4-12 caractères, sans espaces).", Toast.LENGTH_SHORT).show();
                return;
            }

            String savedPinHash = prefs.getString("pin_hash", "");
            String legacyPin = prefs.getString("pin_code", "");
            if (savedPinHash.isEmpty() && !legacyPin.isEmpty()) {
                try {
                    savedPinHash = SecurityUtils.hashPin(this, legacyPin);
                    prefs.edit().putString("pin_hash", savedPinHash).remove("pin_code").apply();
                } catch (Exception e) {
                    Log.e(TAG, "Migration PIN impossible", e);
                }
            }

            if (!savedPinHash.isEmpty() && matricule.equals(currentMatricule)
                    && SecurityUtils.verifyPin(this, code, savedPinHash)) {
                Intent stopIntent = new Intent(this, TheftService.class);
                stopIntent.setAction("STOP_THEFT");
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(stopIntent);
                    } else {
                        startService(stopIntent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "STOP_THEFT impossible: " + e.getMessage());
                }
                stopTheftRemotely(matricule);
                return;
            } else if (!savedPinHash.isEmpty()) {
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
            AuthManager.ensureSignedIn(new AuthManager.Callback() {
                @Override public void onReady(String newUid) {
                    registerStudentInDatabase(matricule, code);
                }
                @Override public void onError(Exception e) {
                    Toast.makeText(MainActivity.this, "Erreur d'authentification, réessayez.", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        Map<String, Object> studentData = new HashMap<>();
        studentData.put("ownerUid", uid);
        studentData.put("token", currentToken);
        studentData.put("status", "securise");
        studentData.put("lat", null);
        studentData.put("lng", null);

        // Anti-détournement de matricule : refuse si déjà possédé par un autre UID.
        db.collection("devices").document(matricule).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists() && snapshot.contains("ownerUid")) {
                        String existingOwner = snapshot.getString("ownerUid");
                        if (existingOwner != null && !existingOwner.equals(uid)) {
                            Toast.makeText(MainActivity.this,
                                    "Ce matricule est déjà enregistré sur un autre appareil. Contactez la direction.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    db.collection("devices").document(matricule)
                            .set(studentData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid -> onRegistered(matricule, code))
                            .addOnFailureListener(e -> Toast.makeText(MainActivity.this,
                                    "Erreur d'inscription réseau.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Erreur réseau (vérification matricule).", Toast.LENGTH_SHORT).show());
    }

    private void onRegistered(String matricule, String code) {
        String emergency = readEmergencyNumber();
        try {
            String pinHash = SecurityUtils.hashPin(this, code);
            prefs.edit().putString("matricule", matricule)
                    .putString("pin_hash", pinHash)
                    .putString("emergency_number", emergency)
                    .remove("pin_code").apply();
        } catch (Exception e) {
            Log.e(TAG, "Hachage PIN impossible", e);
            Toast.makeText(this, "Erreur interne (PIN).", Toast.LENGTH_SHORT).show();
            return;
        }
        currentMatricule = matricule;
        try {
            prefs.edit().putString("sim_fp", getSimFingerprint()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Empreinte SIM impossible: " + e.getMessage());
        }
        Toast.makeText(this, "Inscription réussie ! Vous êtes protégé.", Toast.LENGTH_SHORT).show();
        editMatricule.setEnabled(false);
        updateStatusCard();
    }

    private String getSimFingerprint() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return "";
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                try {
                    String serial = tm.getSimSerialNumber();
                    if (serial != null && !serial.isEmpty()) return "serial:" + serial;
                } catch (SecurityException se) {
                    Log.w(TAG, "getSimSerialNumber refusé: " + se.getMessage());
                }
            }
            String op = "";
            try { op = tm.getSimOperator(); } catch (Exception ignored) {}
            String iso = "";
            try { iso = tm.getSimCountryIso(); } catch (Exception ignored) {}
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            return "op:" + (op != null ? op : "") + "|iso:" + (iso != null ? iso : "")
                    + "|id:" + (androidId != null ? androidId : "");
        } catch (Exception e) {
            return "";
        }
    }

    private void enableDeviceAdmin() {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
            if (dpm != null && !dpm.isAdminActive(adminComponent)) {
                Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "MYSAFELEX utilise l'administration pour verrouiller l'écran en cas de vol.");
                startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
            } else {
                Toast.makeText(this, "Protection active !", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Admin appareil impossible: " + e.getMessage());
        }
    }
}
