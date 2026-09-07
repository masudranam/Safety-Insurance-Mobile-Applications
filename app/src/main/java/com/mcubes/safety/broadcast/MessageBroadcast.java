package com.mcubes.safety.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import com.mcubes.safety.service.MessageService;

/**
 * Counts volume-key presses and hands off to {@link MessageService} on the third one inside
 * {@link #TIME_WINDOW_MS}.
 */
public class MessageBroadcast extends BroadcastReceiver {

    private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";

    private static final int REQUIRED_PRESS_COUNT = 3;
    private static final long TIME_WINDOW_MS = 2000L;

    private static MessageBroadcast instance;

    private long windowStartedAt;
    private int pressCount;

    private MessageBroadcast() {
    }

    public static synchronized MessageBroadcast getInstance() {
        if (instance == null) {
            instance = new MessageBroadcast();
        }
        return instance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !VOLUME_CHANGED_ACTION.equals(intent.getAction())) {
            return;
        }
        // Our own alarm raises the alarm stream, which broadcasts a volume change. Counting
        // that would let the alarm re-trigger itself.
        if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_ALARM) {
            return;
        }

        if (!countPress()) {
            return;
        }

        Intent trigger = new Intent(context.getApplicationContext(), MessageService.class)
                .setAction(MessageService.ACTION_TRIGGER_EMERGENCY);
        ContextCompat.startForegroundService(context.getApplicationContext(), trigger);
    }

    /**
     * Sliding window anchored on the first press.
     *
     * <p>The previous implementation posted a fresh reset callback on every press without
     * cancelling the earlier ones, so a reset scheduled by press #1 would fire midway through
     * the sequence and zero the counter — a genuine three-press gesture frequently did
     * nothing. Comparing timestamps removes the callbacks, and the race, entirely.
     *
     * @return true when this press completes the gesture
     */
    private synchronized boolean countPress() {
        long now = SystemClock.elapsedRealtime();
        if (now - windowStartedAt > TIME_WINDOW_MS) {
            windowStartedAt = now;
            pressCount = 1;
        } else {
            pressCount++;
        }

        if (pressCount >= REQUIRED_PRESS_COUNT) {
            pressCount = 0;
            windowStartedAt = 0L;
            return true;
        }
        return false;
    }
}
