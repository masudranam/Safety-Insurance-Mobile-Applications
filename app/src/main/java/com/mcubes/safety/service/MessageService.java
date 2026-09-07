package com.mcubes.safety.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.mcubes.safety.MainActivity;
import com.mcubes.safety.R;
import com.mcubes.safety.broadcast.MessageBroadcast;
import com.mcubes.safety.emergency.AlarmPlayer;
import com.mcubes.safety.emergency.EmergencyDispatcher;
import com.mcubes.safety.location.LocationHelper;

/**
 * Keeps the volume-key listener alive while the app is backgrounded, and owns the emergency
 * itself. The receiver only counts presses; the work happens here because a
 * {@link android.content.BroadcastReceiver} dies as soon as {@code onReceive} returns, which
 * is far too early for an asynchronous location fix.
 */
public class MessageService extends Service {

    public static final String ACTION_TRIGGER_EMERGENCY = "com.mcubes.safety.TRIGGER_EMERGENCY";
    public static final String ACTION_STOP_ALARM = "com.mcubes.safety.STOP_ALARM";

    private static final int NOTIFICATION_ID = 79345541;
    private static final String CHANNEL_ID = "safety_service";

    private boolean receiverRegistered;

    @Override
    public void onCreate() {
        super.onCreate();
        startInForeground();

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        // VOLUME_CHANGED_ACTION is a protected system broadcast, so NOT_EXPORTED still
        // receives it while keeping other apps out.
        ContextCompat.registerReceiver(this, MessageBroadcast.getInstance(), filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(MessageBroadcast.getInstance());
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            receiverRegistered = false;
        }
        AlarmPlayer.stopAndRestoreVolume(this);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TRIGGER_EMERGENCY.equals(action)) {
            triggerEmergency();
        } else if (ACTION_STOP_ALARM.equals(action)) {
            AlarmPlayer.stopAndRestoreVolume(this);
        }
        return START_STICKY;
    }

    private void triggerEmergency() {
        // Sound first: the alarm is instant, the location fix is not.
        AlarmPlayer.start(this);
        LocationHelper.refresh(this, message -> {
            EmergencyDispatcher.Result result =
                    EmergencyDispatcher.sendToSavedContacts(MessageService.this, message);
            announce(result);
        });
    }

    private void announce(EmergencyDispatcher.Result result) {
        int message;
        switch (result) {
            case SENT:
                message = R.string.sms_sent;
                break;
            case NO_RECIPIENTS:
                message = R.string.no_recipients;
                break;
            case NO_PERMISSION:
                message = R.string.sms_permission_missing;
                break;
            default:
                message = R.string.sms_failed;
                break;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void startInForeground() {
        Notification notification = createNotification();
        // specialUse only exists from API 34; on older releases the untyped call is correct,
        // since the manifest type would not be recognised there anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // LOW, not DEFAULT: this notification is permanent, so it must stay silent.
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
                manager.createNotificationChannel(channel);
            }
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent stopAlarm = PendingIntent.getService(
                this, 1, new Intent(this, MessageService.class).setAction(ACTION_STOP_ALARM), flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setSmallIcon(R.drawable.notification_icon)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(0, getString(R.string.stop_alarm), stopAlarm)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
