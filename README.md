# Safety Insurance Mobile Application using JAVA

This repository contains the source code for the **Safety Insurance Mobile Application**, developed in Java for Android, aimed at enhancing personal safety by offering quick and effective emergency communication. The app allows users to save important contact information, compose emergency messages, and trigger alerts through the volume button during critical situations.

## Features

- **Emergency SMS Trigger**: Users can press the volume button three times to send an automatic SMS to pre-saved contacts containing their current location and a Google Maps link.
- **Location Services**: The app retrieves the user's current location using GPS and reverse geocoding and sends it in the emergency message.
- **Customizable Emergency Messages**: Users can customize the emergency message content and save multiple contacts.
- **Burglar Alarm**: The app features a custom alarm that is triggered along with the emergency SMS.
- **Google Maps Integration**: The SMS includes a clickable link to the user's location on Google Maps.
- **User-Friendly Interface**: The app provides an easy way to manage emergency contacts and configure messages.
- **Background Services**: The app includes a foreground service to detect volume button presses even when the screen is off.

## Technologies Used

- **Frontend**: Java for Android (minSdk 25, target/compileSdk 34)
- **Location Services**: Play Services `FusedLocationProviderClient` for the fix, Android's `Geocoder` for the optional street address
- **Storage**: SharedPreferences for saving user preferences and contact details
- **Messaging**: Android's `SmsManager` for sending SMS alerts (multipart, so long messages are not rejected)
- **Foreground Services**: To handle emergency triggers even when the app is in the background

## Installation

1. **Clone the repository**:
    ```bash
    git clone https://github.com/masudranam/Safety-Insurance-Mobile-Applications.git
    cd Safety-Insurance-Mobile-Applications

2. **Set up the project:**
    - *Open the project in **Android Studio** (Giraffe or newer — the build uses AGP 8.1 and needs JDK 17).*
    - *Sync the Gradle files. Studio generates `gradle/wrapper/gradle-wrapper.jar` on first sync; it is not checked in.*
    - *Grant the SMS, Location and Notification permissions when the app asks on first launch.*
3. **Run the App:**
   - *Connect an Android device or start an emulator.*
   - *Click on "Run" to install and launch the app.*
   - *SMS sending does not work on most emulators — use a real device with a SIM to test the alert.*

## Usage
1. **First Time Setup:**
  - *Accept the terms and conditions.*
  - *Add emergency contacts and customize the emergency message.*

2. **Trigger Emergency SMS:**

  - *In case of emergency, press the volume button three times when the screen is off. This will automatically send an SMS with your current location to the predefined contacts.

3. **Burglar Alarm:** 
 - *When the emergency is triggered, a custom burglar alarm sounds on the alarm stream — audible even when the phone is silenced — along with a vibration pattern.*
 - *It stops from the "Stop alarm" action on the service notification, and auto-stops after 60 seconds.*

## Architecture

- **Activities:** Splash, terms and conditions, main setup screen, and the location detail screen.
- **`service/MessageService`:** Foreground service. Hosts the volume-key receiver and owns the
  emergency itself — the location fix is asynchronous and a `BroadcastReceiver` is dead long
  before it returns, so the work cannot live in the receiver.
- **`broadcast/MessageBroadcast`:** Counts volume-key presses against a 2-second sliding window
  and hands off to the service on the third.
- **`emergency/EmergencyDispatcher`:** The single SMS send path, shared by the manual button and
  the gesture so they cannot drift apart.
- **`emergency/AlarmPlayer`:** The burglar alarm.
- **`location/LocationHelper`:** Builds the message from a fresh fix, with a timeout and
  last-known fallback so an emergency always sends *something*.

### Known limitations

- The trigger listens for `android.media.VOLUME_CHANGED_ACTION`, which the system does not
  broadcast when the volume is already at its minimum or maximum. At those extremes the gesture
  will not fire. A reliable key-level trigger needs an accessibility service.
- Reverse geocoding needs network access; without it the message still carries a correct Google
  Maps link built from the raw coordinates.

