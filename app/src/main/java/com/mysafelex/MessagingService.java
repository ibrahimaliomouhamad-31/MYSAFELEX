package com.mysafelex;

import android.content.Intent;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Si on reçoit un message avec le titre "VOL", on déclenche l'alarme !
        if (remoteMessage.getNotification() != null && remoteMessage.getNotification().getTitle() != null) {
            String title = remoteMessage.getNotification().getTitle();
            if (title.equals("VOL")) {
                Intent intent = new Intent(this, TheftService.class);
                intent.setAction("START_THEFT");
                
                // LA LIGNE MAGIQUE POUR REVEILLER L'APPLI FERMEE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            }
        }
    }
}
