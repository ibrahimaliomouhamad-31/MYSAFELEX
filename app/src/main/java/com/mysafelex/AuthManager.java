package com.mysafelex;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Centralise l'authentification Firebase (anonyme).
 *
 * Chaque appareil obtient un UID stable (persisté par le SDK Firebase tant que
 * les données de l'app ne sont pas effacées). Cet UID sert de clé de propriété
 * ("ownerUid") sur le document Firestore de l'appareil, et les règles de
 * sécurité (voir firestore.rules) n'autorisent la lecture/écriture d'un
 * document que si ownerUid correspond à l'utilisateur authentifié.
 *
 * Sans cet appel, toutes les écritures Firestore/Storage sont refusées par les
 * nouvelles règles (avant : elles étaient acceptées de n'importe où, voir
 * l'audit de sécurité).
 */
public class AuthManager {

    public interface Callback {
        void onReady(@NonNull String uid);
        void onError(@NonNull Exception e);
    }

    public static void ensureSignedIn(Callback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            callback.onReady(current.getUid());
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task -> {
            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                callback.onReady(auth.getCurrentUser().getUid());
            } else {
                Log.e("AuthManager", "Échec de l'authentification anonyme", task.getException());
                if (task.getException() != null) {
                    callback.onError(task.getException());
                }
            }
        });
    }

    public static String getCurrentUidOrNull() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }
}
