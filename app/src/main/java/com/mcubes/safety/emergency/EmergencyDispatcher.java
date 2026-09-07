package com.mcubes.safety.emergency;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.mcubes.safety.PreferenceKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single place where an emergency actually goes out, shared by the manual button and the
 * volume-key gesture so the two paths cannot drift apart.
 */
public final class EmergencyDispatcher {

    /** What happened, so callers can tell the user. */
    public enum Result {
        SENT,
        NO_RECIPIENTS,
        NO_PERMISSION,
        FAILED
    }

    private EmergencyDispatcher() {
    }

    public static boolean hasSmsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** The saved numbers, skipping blank slots. */
    public static List<String> savedRecipients(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE);
        String[] keys = {
                PreferenceKeys.PHONE_NUMBER_KEY_1,
                PreferenceKeys.PHONE_NUMBER_KEY_2,
                PreferenceKeys.PHONE_NUMBER_KEY_3
        };
        List<String> recipients = new ArrayList<>(keys.length);
        for (String key : keys) {
            String number = prefs.getString(key, "");
            if (number != null && !TextUtils.isEmpty(number.trim())) {
                recipients.add(number.trim());
            }
        }
        return Collections.unmodifiableList(recipients);
    }

    /** Sends {@code message} to every saved contact and sounds the alarm. */
    public static Result dispatch(Context context, String message) {
        Result result = sendToSavedContacts(context, message);
        AlarmPlayer.start(context);
        return result;
    }

    public static Result sendToSavedContacts(Context context, String message) {
        if (!hasSmsPermission(context)) {
            return Result.NO_PERMISSION;
        }
        List<String> recipients = savedRecipients(context);
        if (recipients.isEmpty()) {
            return Result.NO_RECIPIENTS;
        }
        if (TextUtils.isEmpty(message)) {
            message = "Emergency. Please help.";
        }

        SmsManager smsManager = smsManager(context);
        boolean anySent = false;
        for (String recipient : recipients) {
            if (send(smsManager, recipient, message)) {
                anySent = true;
            }
        }
        return anySent ? Result.SENT : Result.FAILED;
    }

    private static boolean send(SmsManager smsManager, String recipient, String message) {
        try {
            // The location line pushes most messages past the 160-character single-part
            // limit, which sendTextMessage rejects outright.
            ArrayList<String> parts = smsManager.divideMessage(message);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(recipient, null, message, null, null);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static SmsManager smsManager(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getSystemService(SmsManager.class);
        }
        return SmsManager.getDefault();
    }
}
