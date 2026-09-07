package com.mcubes.safety.emergency;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.mcubes.safety.R;

/**
 * The burglar alarm that sounds alongside the emergency SMS.
 *
 * <p>Plays on the alarm stream so it is audible even when the phone is silenced, and stops
 * itself after {@link #MAX_DURATION_MS} so a stray trigger cannot run the battery down.
 */
public final class AlarmPlayer {

    private static final long MAX_DURATION_MS = 60_000L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable AUTO_STOP = AlarmPlayer::stop;

    private static MediaPlayer player;
    private static Vibrator vibrator;
    private static int previousAlarmVolume = -1;

    private AlarmPlayer() {
    }

    public static synchronized boolean isPlaying() {
        return player != null;
    }

    public static synchronized void start(Context context) {
        if (player != null) {
            return;
        }
        Context app = context.getApplicationContext();
        AudioManager audioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        int sessionId = audioManager == null
                ? AudioManager.AUDIO_SESSION_ID_GENERATE
                : audioManager.generateAudioSessionId();

        MediaPlayer created = MediaPlayer.create(app, R.raw.burglur_ringtone_alarm, attributes, sessionId);
        if (created == null) {
            return;
        }

        // An alarm nobody can hear is no alarm. Remember the user's level so we can put it back.
        if (audioManager != null) {
            previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        }

        created.setLooping(true);
        created.start();
        player = created;

        vibrate(app);

        HANDLER.removeCallbacks(AUTO_STOP);
        HANDLER.postDelayed(AUTO_STOP, MAX_DURATION_MS);
    }

    public static synchronized void stop() {
        HANDLER.removeCallbacks(AUTO_STOP);
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            // The waveform repeats indefinitely, so it has to be cancelled explicitly.
            vibrator.cancel();
            vibrator = null;
        }
    }

    /** Restores the alarm stream volume we raised in {@link #start(Context)}. */
    public static synchronized void stopAndRestoreVolume(Context context) {
        stop();
        if (previousAlarmVolume >= 0) {
            AudioManager audioManager =
                    (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0);
            }
            previousAlarmVolume = -1;
        }
    }

    private static void vibrate(Context app) {
        Vibrator device = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        if (device == null || !device.hasVibrator()) {
            return;
        }
        long[] pattern = {0, 500, 500};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.vibrate(VibrationEffect.createWaveform(pattern, 0));
        } else {
            device.vibrate(pattern, 0);
        }
        vibrator = device;
    }
}
