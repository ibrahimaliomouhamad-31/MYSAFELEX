package com.mysafelex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;

public class CameraHelper {

    private static Camera camera;

     public static void takeSecretPhoto(Context context, String deviceId) {
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            Camera.Parameters params = camera.getParameters();
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(params);

            camera.startPreview();
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    camera.release();
                    // Convertir la photo en texte et l'envoyer
                    uploadPhotoToFirestore(context, data, deviceId);
                }
            });
        } catch (Exception e) {
            Log.e("CameraHelper", "Erreur caméra: " + e.getMessage());
        }
    }

     private static void uploadPhotoToFirestore(Context context, byte[] data, String deviceId) {
        try {
            // 1. Réduire la taille de la photo pour qu'elle rentre dans la base de données
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 400, 300, false);

            // 2. Convertir en texte (Base64)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            byte[] compressedData = baos.toByteArray();
            String photoBase64 = Base64.encodeToString(compressedData, Base64.DEFAULT);

            // 3. Envoyer le texte dans Firestore (Gratuit !)
            FirebaseFirestore db = FirebaseFirestore.getInstance();
                     db.collection("devices").document(deviceId)
                    .update("photoBase64", photoBase64)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(context, "Photo du voleur envoyée !", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Log.e("CameraHelper", "Erreur envoi: " + e.getMessage()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
