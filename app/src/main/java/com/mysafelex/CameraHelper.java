package com.mysafelex;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;

public class CameraHelper {

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
                if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return null;
                }

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

                // FAILLE 1 : Ajouter un SurfaceTexture invisible pour empêcher le crash
                try {
                    SurfaceTexture dummySurface = new SurfaceTexture(0);
                    camera.setPreviewTexture(dummySurface);
                } catch (Exception e) {
                    Log.e("CameraHelper", "Erreur SurfaceTexture: " + e.getMessage());
                }

                camera.startPreview();
                
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
                
                // FAILLE 3 : On garde le thread en vie jusqu'à 10 sec max, mais on vérifie si la caméra est encore ouverte
                int waitCount = 0;
                while (camera != null && waitCount < 10) {
                    Thread.sleep(1000);
                    waitCount++;
                }
                
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
            
            Matrix matrix = new Matrix();
            matrix.postRotate(270);
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 400, 300, false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
            byte[] compressedData = baos.toByteArray();
            String photoBase64 = Base64.encodeToString(compressedData, Base64.DEFAULT);

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("devices").document(deviceId)
                    .update("photoBase64", photoBase64)
                    .addOnSuccessListener(aVoid -> Toast.makeText(context, "Photo envoyée !", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Log.e("CameraHelper", "Erreur envoi: " + e.getMessage()));

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
