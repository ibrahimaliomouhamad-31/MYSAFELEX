package com.mysafelex;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Écran de démarrage : logo + nom + version, puis bascule vers MainActivity.
 * Durée courte (~1,2 s) pour ne pas ralentir l'accès à la protection.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView txtVersion = findViewById(R.id.txtSplashVersion);
            if (txtVersion != null && version != null) {
                txtVersion.setText("v" + version);
            }
        } catch (Exception ignored) {}

        try {
            ImageView logo = findViewById(R.id.imgSplashLogo);
            if (logo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                logo.setAlpha(0f);
                logo.setScaleX(0.7f);
                logo.setScaleY(0.7f);
                logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(700).start();
            }
        } catch (Exception ignored) {}

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            } catch (Exception ignored) {}
            try { finish(); } catch (Exception ignored) {}
        }, SPLASH_DELAY_MS);
    }
}
