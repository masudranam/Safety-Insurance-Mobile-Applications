package com.mcubes.safety.location;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.mcubes.safety.PreferenceKeys;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds the emergency message from a fresh location fix.
 *
 * <p>Everything here is best-effort by design: in an emergency a message with a stale
 * position beats no message at all, so every failure path still delivers something.
 */
public final class LocationHelper {

    public interface Callback {
        void onMessageReady(String fullSmsContents);
    }

    /** Never make a panicking user wait longer than this for a fix. */
    private static final long FIX_TIMEOUT_MS = 8000L;

    private static final Executor GEOCODER_EXECUTOR = Executors.newSingleThreadExecutor();

    private LocationHelper() {
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** The last message we managed to build, or the bare user text if we never built one. */
    public static String lastKnownMessage(Context context) {
        SharedPreferences prefs = prefs(context);
        String saved = prefs.getString(PreferenceKeys.FULL_SMS_CONTENTS, "");
        if (!TextUtils.isEmpty(saved)) {
            return saved;
        }
        return prefs.getString(PreferenceKeys.SMS_CONTENT_KEY, "");
    }

    /**
     * Asks for a current fix and hands the composed message to {@code callback} on the main
     * thread, exactly once, within {@link #FIX_TIMEOUT_MS}.
     */
    public static void refresh(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        final Delivery delivery = new Delivery(app, callback);
        delivery.arm(FIX_TIMEOUT_MS);

        if (!hasLocationPermission(app)) {
            delivery.deliver(lastKnownMessage(app));
            return;
        }

        final FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(app);
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            compose(app, location, delivery);
                        } else {
                            fallBackToLastLocation(app, client, delivery);
                        }
                    })
                    .addOnFailureListener(e -> fallBackToLastLocation(app, client, delivery));
        } catch (SecurityException e) {
            delivery.deliver(lastKnownMessage(app));
        }
    }

    private static void fallBackToLastLocation(Context app,
                                               FusedLocationProviderClient client,
                                               Delivery delivery) {
        try {
            client.getLastLocation()
                    .addOnSuccessListener(last -> {
                        if (last != null) {
                            compose(app, last, delivery);
                        } else {
                            delivery.deliver(lastKnownMessage(app));
                        }
                    })
                    .addOnFailureListener(e -> delivery.deliver(lastKnownMessage(app)));
        } catch (SecurityException e) {
            delivery.deliver(lastKnownMessage(app));
        }
    }

    private static void compose(Context app, Location location, Delivery delivery) {
        GEOCODER_EXECUTOR.execute(() -> {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String mapLink = "https://maps.google.com/?q=" + latitude + "," + longitude;

            String city = "";
            String country = "";
            String address = "";
            if (Geocoder.isPresent()) {
                try {
                    Geocoder geocoder = new Geocoder(app, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address first = addresses.get(0);
                        city = orEmpty(first.getLocality());
                        country = orEmpty(first.getCountryName());
                        address = orEmpty(first.getAddressLine(0));
                    }
                } catch (IOException | IllegalArgumentException e) {
                    // Reverse geocoding needs the network and often fails outdoors. The map
                    // link is built from the raw fix, so it stays correct either way.
                    e.printStackTrace();
                }
            }

            StringBuilder locationContents = new StringBuilder("My current location");
            if (!TextUtils.isEmpty(address)) {
                locationContents.append("\n").append(address);
            }
            locationContents.append("\nMapLink: ").append(mapLink);

            SharedPreferences prefs = prefs(app);
            String smsContent = prefs.getString(PreferenceKeys.SMS_CONTENT_KEY, "");
            String fullSmsContents = TextUtils.isEmpty(smsContent)
                    ? locationContents.toString()
                    : smsContent + "\n" + locationContents;

            prefs.edit()
                    .putString(PreferenceKeys.LATTITUDE_KEY, String.valueOf(latitude))
                    .putString(PreferenceKeys.LONGITUDE_KEY, String.valueOf(longitude))
                    .putString(PreferenceKeys.CITY_KEY, city)
                    .putString(PreferenceKeys.COUNTRY_KEY, country)
                    .putString(PreferenceKeys.ADDRESS_KEY, address)
                    .putString(PreferenceKeys.MAP_LINK_KEY, mapLink)
                    .putString(PreferenceKeys.FULL_LOCATION_CONTENTS, locationContents.toString())
                    .putString(PreferenceKeys.FULL_SMS_CONTENTS, fullSmsContents)
                    .apply();

            delivery.deliver(fullSmsContents);
        });
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Guarantees the callback runs once, on the main thread, fix or no fix. */
    private static final class Delivery implements Runnable {
        private final Context app;
        private final Callback callback;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final AtomicBoolean done = new AtomicBoolean(false);

        Delivery(Context app, Callback callback) {
            this.app = app;
            this.callback = callback;
        }

        void arm(long timeoutMs) {
            main.postDelayed(this, timeoutMs);
        }

        @Override
        public void run() {
            deliver(lastKnownMessage(app));
        }

        void deliver(String message) {
            if (done.getAndSet(true)) {
                return;
            }
            main.removeCallbacks(this);
            main.post(() -> callback.onMessageReady(message));
        }
    }
}
