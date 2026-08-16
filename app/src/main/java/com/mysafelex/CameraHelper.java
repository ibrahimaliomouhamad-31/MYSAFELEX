package com.mysafelex;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;

public class CameraHelper {

    private static Camera camera;

    public static void takeSecretPhoto(Context context) {
        try {
            // Ouvrir la caméra frontale
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            Camera.Parameters params = camera.getParameters();
            
            // Désactiver le son et le flash
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(params);

            camera.startPreview();
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    // Photo prise ! On la sauvegarde.
                    savePhoto(context, data);
                    camera.release();
                }
            });
        } catch (Exception e) {
            Log.e("CameraHelper", "Erreur: " + e.getMessage());
        }
    }

    private static void savePhoto(Context context, byte[] data) {
        try {
            File file = new File(context.getFilesDir(), "thief_photo.jpg");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
            Toast.makeText(context, "Photo du voleur prise !", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
