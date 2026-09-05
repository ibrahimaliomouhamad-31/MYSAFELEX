package com.mysafelex;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Prend une photo discrète avec la caméra frontale via CameraX, puis la
 * stocke directement dans Firestore en Base64 (et non dans Firebase Storage,
 * qui nécessite le forfait payant Blaze — voir README.md).
 *
 * Un document Firestore est limité à 1 Mo : la photo est donc fortement
 * compressée (640x480, JPEG qualité 60), ce qui donne généralement quelques
 * dizaines de Ko, largement sous la limite.
 *
 * Note : depuis Android 9 (caméra) et Android 12 (micro), le système affiche
 * un indicateur visuel obligatoire pendant l'utilisation de la caméra — la
 * capture n'est donc pas invisible pour quelqu'un qui regarde l'écran à ce
 * moment précis.
 */
public class CameraHelper {

    private static final String TAG = "CameraHelper";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // Marge de sécurité sous la limite de 1 Mo par document Firestore
    // (le Base64 gonfle la taille d'environ 33% par rapport aux octets bruts).
    private static final int MAX_PHOTO_BYTES = 700_000;

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
                            processAndSave(appContext, photoFile, deviceId);
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

    private static void processAndSave(Context appContext, File photoFile, String deviceId) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            if (bitmap == null) {
                Log.e(TAG, "Impossible de décoder la photo capturée.");
                return;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 640, 480, false);

            // On réduit la qualité JPEG progressivement si besoin pour rester
            // sous la limite de taille d'un document Firestore.
            byte[] jpegBytes = null;
            for (int quality : new int[]{60, 45, 30, 20}) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                byte[] candidate = baos.toByteArray();
                if (candidate.length <= MAX_PHOTO_BYTES) {
                    jpegBytes = candidate;
                    break;
                }
                jpegBytes = candidate; // garde la dernière tentative même si trop grande
            }

            if (jpegBytes == null || jpegBytes.length > MAX_PHOTO_BYTES) {
                Log.e(TAG, "Photo trop volumineuse même après compression, abandon.");
                return;
            }

            String base64Photo = Base64.encodeToString(jpegBytes, Base64.NO_WRAP);
            saveToFirestore(deviceId, base64Photo);
        } catch (Exception e) {
            Log.e(TAG, "Erreur traitement photo: " + e.getMessage());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            photoFile.delete();
        }
    }

    private static void saveToFirestore(String deviceId, String base64Photo) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("photoBase64", base64Photo);
        updates.put("lastPhotoAt", System.currentTimeMillis());

        FirebaseFirestore.getInstance().collection("devices").document(deviceId)
                .update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Erreur Firestore: " + e.getMessage()));
    }
}
