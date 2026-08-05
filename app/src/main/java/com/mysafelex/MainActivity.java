package com.mysafelex;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText editMatricule, editCode;
    private Button btnLogin;
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editMatricule = findViewById(R.id.editMatricule);
        editCode = findViewById(R.id.editInviteCode);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> {
            String matricule = editMatricule.getText().toString();
            String code = editCode.getText().toString();

            if(matricule.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
            } else {
                // Demander la protection anti-désinstallation
                enableDeviceAdmin();
            }
        });
    }

    private void enableDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);

        // Si ce n'est pas encore protégé, ouvrir la fenêtre d'activation
        if (!dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
        } else {
            Toast.makeText(this, "Déjà protégé et connecté !", Toast.LENGTH_SHORT).show();
        }
    }
}
