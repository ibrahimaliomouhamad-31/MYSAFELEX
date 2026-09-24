package com.mysafelex;

import android.content.Context;
import android.provider.Settings;
import java.security.MessageDigest;

/**
 * Utilitaires de sécurité : hachage du PIN, sel stable par appareil.
 * Le PIN n'est JAMAIS stocké en clair.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    private static String getSalt(Context context) {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return androidId != null ? androidId : "mysafelex-fallback-salt";
        } catch (Exception e) {
            return "mysafelex-fallback-salt";
        }
    }

    public static String hashPin(Context context, String pin) {
        try {
            String salted = getSalt(context) + "::" + pin;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(salted.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hachage PIN impossible", e);
        }
    }

    public static boolean verifyPin(Context context, String pin, String expectedHash) {
        if (pin == null || expectedHash == null || expectedHash.isEmpty()) return false;
        try {
            return hashPin(context, pin).equals(expectedHash);
        } catch (Exception e) {
            return false;
        }
    }

    /** Valide le format du matricule LEX (lettres/chiffres/tirets, 3-30 car.). */
    public static boolean isValidMatricule(String matricule) {
        if (matricule == null) return false;
        String m = matricule.trim();
        return m.matches("[A-Za-z0-9_-]{3,30}");
    }

    /** Valide le PIN (4-12 chiffres/lettres, pas d'espaces). */
    public static boolean isValidPin(String pin) {
        if (pin == null) return false;
        return pin.matches("[0-9A-Za-z]{4,12}");
    }
}
