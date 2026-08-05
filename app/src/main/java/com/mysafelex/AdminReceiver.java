package com.mysafelex;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // Message affiché si le voleur essaie de désactiver la protection
        return "Attention : Désactiver cette protection supprime la sécurisation de l'appareil.";
    }
}
