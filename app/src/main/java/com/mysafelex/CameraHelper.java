package com.mysafelex;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prend une photo discrète avec la caméra frontale via CameraX.
 *
 * Remplace l'ancienne implémentation basée sur android.hardware.Camera
 * (dépréciée depuis l'API 21, peu fiable sur les appareils récents) et sur
 * AsyncTask (déprécié depuis l'API 30).
 *
 * Note : depuis Android 9 (caméra) et Android 12 (micro), le système affiche
 * un indicateur visuel obligatoire pendant l'utilisation — la capture n'est
 * donc pas invisible pour quelqu'un qui regarde l'écran à ce moment précis.
 */
public class CameraHelper {

    private static final String TAG = "CameraHelper";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public static void takeSecretPhoto(Context context, LifecycleOwner lifecycleOwner, String deviceId) {
        Context appContext = context.getApplicationContext();

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission caméra manquante, capture annulée.");
            return;
        }

        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(appContext);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();

                ImageCapture imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture);

                File photoFile = new File(appContext.getCacheDir(), "secret_photo_" + System.currentTimeMillis() + ".jpg");
                ImageCapture.OutputFileOptions outputOptions =
                        new ImageCapture.OutputFileOptions.Builder(photoFile).build();

                imageCapture.takePicture(outputOptions, EXECUTOR, new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(androidx.camera.core.ImageCapture.OutputFileResults outputFileResults) {
                        try {
                            processAndUpload(appContext, photoFile, deviceId);
                        } finally {
                            provider.unbindAll();
                        }
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        Log.e(TAG, "Erreur de capture: " + exception.getMessage());
                        provider.unbindAll();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erreur d'initialisation CameraX: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(appContext));
    }

    private static void processAndUpload(Context appContext, File photoFile, String deviceId) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            if (bitmap == null) {
                Log.e(TAG, "Impossible de décoder la photo capturée.");
                return;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 640, 480, false);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            uploadPhotoToStorage(appContext, baos.toByteArray(), deviceId);
        } catch (Exception e) {
            Log.e(TAG, "Erreur traitement photo: " + e.getMessage());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            photoFile.delete();
        }
    }

    private static void uploadPhotoToStorage(Context context, byte[] compressedData, String deviceId) {
        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference("devices/" + deviceId + "/photo.jpg");

        storageRef.putBytes(compressedData)
                .addOnSuccessListener(taskSnapshot -> {
                    // On ne duplique plus la photo en Base64 dans Firestore (coût + limite de
                    // taille de document) : seule la référence Storage est stockée.
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("photoUrl", "devices/" + deviceId + "/photo.jpg");
                    updates.put("lastPhotoAt", System.currentTimeMillis());

                    FirebaseFirestore.getInstance().collection("devices").document(deviceId)
                            .update(updates)
                            .addOnFailureListener(e -> Log.e(TAG, "Erreur Firestore: " + e.getMessage()));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Erreur Storage: " + e.getMessage()));
    }
}
