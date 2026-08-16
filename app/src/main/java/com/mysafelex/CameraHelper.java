package com.mysafelex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;

public class CameraHelper {

    // REMPLACE PAR L'EMAIL OU TU VEUX RECEVOIR LES PHOTOS (Si tu as créé le gmail)
    private static final String ADMIN_EMAIL = "TON_EMAIL@gmail.com";

    public static void takeSecretPhoto(Context context, String deviceId) {
        new CameraTask(context.getApplicationContext(), deviceId).execute();
    }

    private static class CameraTask extends AsyncTask<Void, Void, Void> {
        private final Context appContext;
        private final String deviceId;

        CameraTask(Context appContext, String deviceId) {
            this.appContext = appContext;
            this.deviceId = deviceId;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Camera camera = null;
            try {
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

                if (cameraId == -1) return null;

                camera = Camera.open(cameraId);
                if (camera == null) return null;

                Camera.Parameters params = camera.getParameters();
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(params);

                camera.startPreview();
                
                // FAILLE 1 : La caméra est relâchée DANS le callback, plus de Thread.sleep !
                camera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        try {
                            uploadPhotoToFirestore(appContext, data, deviceId);
                        } catch (Exception e) {
                            Log.e("CameraHelper", "Erreur upload: " + e.getMessage());
                        } finally {
                            if (camera != null) {
                                camera.stopPreview();
                                camera.release();
                            }
                        }
                    }
                });
                
                // Laisser la tâche en vie jusqu'à ce que le callback soit appelé (max 5 sec)
                Thread.sleep(5000); 
            } catch (Exception e) {
                Log.e("CameraHelper", "Erreur caméra: " + e.getMessage());
                if (camera != null) camera.release();
            }
            return null;
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
                    .addOnSuccessListener(aVoid -> Toast.makeText(context, "Photo envoyée !", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Log.e("CameraHelper", "Erreur envoi: " + e.getMessage()));

            // ENVOYER PAR EMAIL (Si l'email est configuré)
            if (!ADMIN_EMAIL.equals("TON_EMAIL@gmail.com")) {
                String emailBody = "<h1>🚨 Alerte MYSAFELEX !</h1>" +
                                   "<p><b>Matricule :</b> " + deviceId + "</p>" +
                                   "<img src='data:image/jpeg;base64," + photoBase64 + "' style='width:400px; border:2px solid red;' />";
                new EmailHelper(ADMIN_EMAIL, "🚨 VOL DÉTECTÉ AU LEX", emailBody).execute();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
