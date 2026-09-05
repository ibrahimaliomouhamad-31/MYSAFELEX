package com.mysafelex;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.HashMap;
import java.util.Map;

public class MessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (remoteMessage.getData().size() > 0) {
            String action = remoteMessage.getData().get("action");
            if (action != null && action.equals("VOL")) {
                Intent intent = new Intent(this, TheftService.class);
                intent.setAction("START_THEFT");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }
        }
    }

    // FAILLE 3 : Mise à jour du token mort
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        
        SharedPreferences prefs = getSharedPreferences("lex_prefs", MODE_PRIVATE);
        String matricule = prefs.getString("matricule", null);
        
        if (matricule != null) {
            AuthManager.ensureSignedIn(new AuthManager.Callback() {
                @Override
                public void onReady(String uid) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("token", token);

                    FirebaseFirestore.getInstance().collection("devices").document(matricule)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> Log.d("MYSAFELEX", "Token mis à jour"))
                            .addOnFailureListener(e -> Log.e("MYSAFELEX", "Erreur token", e));
                }

                @Override
                public void onError(Exception e) {
                    Log.e("MYSAFELEX", "Auth anonyme impossible", e);
                }
            });
        }
    }
}
