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
        // FAILLE 2 : Vérifier si la caméra frontale existe avant de l'ouvrir
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                break;
            }
        }

        if (cameraId == -1) {
            Log.e("CameraHelper", "Pas de caméra frontale trouvée.");
            return; // Ne pas crasher, juste abandonner
        }

        try {
            camera = Camera.open(cameraId);
            if (camera == null) return;

            Camera.Parameters params = camera.getParameters();
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(params);

            camera.startPreview();
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    try {
                        uploadPhotoToFirestore(context, data, deviceId);
                    } catch (Exception e) {
                        Log.e("CameraHelper", "Erreur upload: " + e.getMessage());
                    } finally {
                        if (camera != null) {
                            camera.release();
                            CameraHelper.camera = null;
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e("CameraHelper", "Erreur caméra: " + e.getMessage());
            if (camera != null) {
                camera.release();
                camera = null;
            }
        }
    }

    private static void uploadPhotoToFirestore(Context context, byte[] data, String deviceId) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 400, 300, false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            byte[] compressedData = baos.toByteArray();
            String photoBase64 = Base64.encodeToString(compressedData, Base64.DEFAULT);

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("devices").document(deviceId)
                    .update("photoBase64", photoBase64)
                    .addOnSuccessListener(aVoid -> Toast.makeText(context, "Photo du voleur envoyée !", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Log.e("CameraHelper", "Erreur envoi: " + e.getMessage()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
