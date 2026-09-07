package com.mcubes.safety;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.mcubes.safety.emergency.EmergencyDispatcher;
import com.mcubes.safety.location.LocationHelper;
import com.mcubes.safety.service.MessageService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private EditText phoneNumberEditText_1, phoneNumberEditText_2, phoneNumberEditText_3, smsContentEditText;
    private Button saveButton, getlocationButton, sendSMSButton;

    private SharedPreferences sharedPreferences;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        askRequiredPermissions();
        startMessageService();

        //phone number textEdit
        phoneNumberEditText_1 = findViewById(R.id.phone_number_edit_text_1);
        phoneNumberEditText_2 = findViewById(R.id.phone_number_edit_text_2);
        phoneNumberEditText_3 = findViewById(R.id.phone_number_edit_text_3);
        smsContentEditText = findViewById(R.id.sms_content_edit_text);

        //button
        saveButton = findViewById(R.id.save_button);
        getlocationButton = findViewById(R.id.getLocation_button);
        sendSMSButton = findViewById(R.id.send_button);

        //initialize
        sharedPreferences = getSharedPreferences(PreferenceKeys.PREFS_NAME, MODE_PRIVATE);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String phoneNumber1 = phoneNumberEditText_1.getText().toString().trim();
                String phoneNumber2 = phoneNumberEditText_2.getText().toString().trim();
                String phoneNumber3 = phoneNumberEditText_3.getText().toString().trim();
                String smsContent = smsContentEditText.getText().toString().trim();

                // Save data to SharedPreferences
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(PreferenceKeys.PHONE_NUMBER_KEY_1, phoneNumber1);
                editor.putString(PreferenceKeys.PHONE_NUMBER_KEY_2, phoneNumber2);
                editor.putString(PreferenceKeys.PHONE_NUMBER_KEY_3, phoneNumber3);
                editor.putString(PreferenceKeys.SMS_CONTENT_KEY, smsContent);
                editor.apply();

                showToast(R.string.saved);
            }
        });

        // show text feild
        phoneNumberEditText_1.setText(sharedPreferences.getString(PreferenceKeys.PHONE_NUMBER_KEY_1, ""));
        phoneNumberEditText_2.setText(sharedPreferences.getString(PreferenceKeys.PHONE_NUMBER_KEY_2, ""));
        phoneNumberEditText_3.setText(sharedPreferences.getString(PreferenceKeys.PHONE_NUMBER_KEY_3, ""));
        smsContentEditText.setText(sharedPreferences.getString(PreferenceKeys.SMS_CONTENT_KEY, ""));

        getlocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!LocationHelper.hasLocationPermission(MainActivity.this)) {
                    showToast(R.string.location_permission_missing);
                    askRequiredPermissions();
                    return;
                }
                getlocationButton.setEnabled(false);
                // Open the details screen only once the fix has landed — the old code
                // navigated immediately and showed the previous location.
                LocationHelper.refresh(MainActivity.this, message -> {
                    getlocationButton.setEnabled(true);
                    startActivity(new Intent(MainActivity.this, GetLocationActivity.class));
                });
            }
        });

        sendSMSButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!EmergencyDispatcher.hasSmsPermission(MainActivity.this)) {
                    showToast(R.string.sms_permission_missing);
                    askRequiredPermissions();
                    return;
                }
                if (EmergencyDispatcher.savedRecipients(MainActivity.this).isEmpty()) {
                    showToast(R.string.no_recipients);
                    return;
                }
                sendSMSButton.setEnabled(false);
                // Send the message built from *this* fix, not the one left over from last time.
                LocationHelper.refresh(MainActivity.this, message -> {
                    sendSMSButton.setEnabled(true);
                    announce(EmergencyDispatcher.sendToSavedContacts(MainActivity.this, message));
                });
            }
        });
    }

    private void startMessageService() {
        ContextCompat.startForegroundService(this, new Intent(this, MessageService.class));
    }

    private void askRequiredPermissions() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        // Without this the emergency SMS throws at send time and the user finds out too late.
        addIfMissing(missing, Manifest.permission.SEND_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }
        for (int i = 0; i < permissions.length; i++) {
            boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                continue;
            }
            // Degrade instead of quitting: an SMS without a map link is still worth sending,
            // and a location-only setup is still worth keeping.
            if (Manifest.permission.SEND_SMS.equals(permissions[i])) {
                showToast(R.string.sms_permission_missing);
            } else if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])) {
                showToast(R.string.location_permission_missing);
            }
        }
    }

    private void announce(EmergencyDispatcher.Result result) {
        switch (result) {
            case SENT:
                showToast(R.string.sms_sent);
                break;
            case NO_RECIPIENTS:
                showToast(R.string.no_recipients);
                break;
            case NO_PERMISSION:
                showToast(R.string.sms_permission_missing);
                break;
            default:
                showToast(R.string.sms_failed);
                break;
        }
    }

    private void showToast(@StringRes int message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }
}
