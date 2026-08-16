    private void triggerAlarmAndGPS() {
        if (isTheftActive) return;
        isTheftActive = true;

        // 1. PRENDRE LA PHOTO EN PREMIER (avant que l'écran ne se verrouille !)
        CameraHelper.takeSecretPhoto(this, deviceId);

        // 2. VERROUILLER L'ÉCRAN ENSUITE
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.lockNow();
        }

        // 3. DÉCLENCHER L'ALARME
        if (ringtone == null) {
            try {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0);
                }
                Uri alarmSound = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
                if (alarmSound == null) {
                    alarmSound = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
                }
                ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmSound);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                ringtone.setAudioAttributes(audioAttributes);
                ringtone.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 4. DÉMARRER LE GPS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                    .setMinUpdateIntervalMillis(2000)
                    .setMinUpdateDistanceMeters(1)
                    .build();
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }
