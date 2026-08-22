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
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class CameraHelper {

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

        private volatile boolean photoHandled = false;

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

                // Boucle de réessai si la caméra est occupée par le voleur
                int retryCount = 0;
                while (camera == null && retryCount < 3) {
                    try {
                        camera = Camera.open(cameraId);
                    } catch (Exception e) {
                        Log.e("CameraHelper", "Caméra occupée, réessai... (" + retryCount + ")");
                        Thread.sleep(1000);
                    }
                    retryCount++;
                }

                if (camera == null) return null;

                Camera.Parameters params = camera.getParameters();
                params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(params);

                try {
                    SurfaceTexture dummySurface = new SurfaceTexture(0);
                    camera.setPreviewTexture(dummySurface);
                } catch (Exception e) {
                    Log.e("CameraHelper", "Erreur SurfaceTexture: " + e.getMessage());
                }

                camera.startPreview();

                final Camera finalCamera = camera;
                camera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        photoHandled = true;
                        try {
                            uploadPhotoToStorage(appContext, data, deviceId);
                        } catch (Exception e) {
                            Log.e("CameraHelper", "Erreur upload: " + e.getMessage());
                        } finally {
                            finalCamera.stopPreview();
                            finalCamera.release();
                        }
                    }
                });

                int waitCount = 0;
                while (waitCount < 10 && !photoHandled) {
                    Thread.sleep(1000);
                    waitCount++;
                }

                if (!photoHandled && camera != null) {
                    try {
                        camera.stopPreview();
                        camera.release();
                    } catch (Exception ignored) {}
                }

            } catch (Exception e) {
                Log.e("CameraHelper", "Erreur caméra: " + e.getMessage());
                if (camera != null) camera.release();
            }
            return null;
        }
    }

    private static void uploadPhotoToStorage(Context context, byte[] data, String deviceId) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) return;

            Matrix matrix = new Matrix();
            matrix.postRotate(270);
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 640, 480, false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            byte[] compressedData = baos.toByteArray();

            StorageReference storageRef = FirebaseStorage.getInstance()
                    .getReference("devices/" + deviceId + "/photo.jpg");

            storageRef.putBytes(compressedData)
                    .addOnSuccessListener(taskSnapshot -> {
                        // On garde aussi une miniature Base64 pour l'aperçu rapide dans Firestore
                        String thumbBase64 = Base64.encodeToString(compressedData, Base64.DEFAULT);
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("photoBase64", thumbBase64);
                        updates.put("photoUrl", "devices/" + deviceId + "/photo.jpg");
                        updates.put("lastPhotoAt", System.currentTimeMillis());

                        FirebaseFirestore.getInstance().collection("devices").document(deviceId)
                                .update(updates)
                                .addOnFailureListener(e -> Log.e("CameraHelper", "Erreur Firestore: " + e.getMessage()));
                    })
                    .addOnFailureListener(e -> Log.e("CameraHelper", "Erreur Storage: " + e.getMessage()));

        } catch (Exception e) {
            Log.e("CameraHelper", "Erreur photo: " + e.getMessage());
        }
    }
}
