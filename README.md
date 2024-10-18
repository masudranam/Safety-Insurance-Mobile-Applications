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

- **Frontend**: Java for Android
- **Backend**: Firebase (optional for notifications), SMS functionality
- **Location Services**: Google Maps API for reverse geocoding
- **Storage**: SharedPreferences for saving user preferences and contact details
- **Messaging**: Android's SMSManager for sending SMS alerts
- **Foreground Services**: To handle emergency triggers even when the app is in the background

## Installation

1. **Clone the repository**:
    ```bash
    git clone https://github.com/masudranam/Safety-Insurance-Mobile-Applications.git
    cd Safety-Insurance-Mobile-Applications

2. **Set up the project:**
    - *Open the project in **Android Studio:***
    - *Sync the Gradle files.*
    - *Ensure you have the required permissions (SMS, Location) enabled on your device.*
4. **Run the App:**
   - *Connect an Android device or start an emulator.*
   - *Click on "Run" to install and launch the app.*

## Usage
1. **First Time Setup:**
  - *Accept the terms and conditions.*
  - *Add emergency contacts and customize the emergency message.*

2. **Trigger Emergency SMS:**

  - *In case of emergency, press the volume button three times when the screen is off. This will automatically send an SMS with your current location to the predefined contacts.

3. **Burglar Alarm:** 
 - *When the emergency is triggered, a custom burglar alarm will also sound to alert people nearby.*

## Architecture
The project follows a simple **Model-View-Controller (MVC)** architecture, with:

- **Activities:** Managing different screens like splash, main activity, and terms and conditions.
- **Services:** Background services such as the volume button detection and SMS sending.
- **BroadcastReceivers:** Detects volume button events and triggers the emergency actions.

