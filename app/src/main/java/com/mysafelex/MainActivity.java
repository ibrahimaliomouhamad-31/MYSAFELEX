package com.mysafelex;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText editMatricule, editCode;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Relier les éléments de l'écran au code
        editMatricule = findViewById(R.id.editMatricule);
        editCode = findViewById(R.id.editInviteCode);
        btnLogin = findViewById(R.id.btnLogin);

        // Action quand on clique sur "SE CONNECTER"
        btnLogin.setOnClickListener(v -> {
            String matricule = editMatricule.getText().toString();
            String code = editCode.getText().toString();

            // Vérifier si les champs ne sont pas vides
            if(matricule.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
            } else {
                // Pour l'instant, on affiche juste un message. 
                // Plus tard, on vérifiera dans la base de données.
                Toast.makeText(this, "Connexion en cours...", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
