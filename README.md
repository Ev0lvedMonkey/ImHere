# ImHere

ImHere is an Android app for quickly sharing your location with a few trusted
contacts via SMS. The app itself needs no account, server, or internet connection:
coordinates are resolved on the device and sent only to the recipients you choose.

The main scenario is the home-screen SOS widget: tap it twice and ImHere obtains
a fresh location fix and sends the SMS without opening the app.

## Download

[Download the latest release](https://github.com/Ev0lvedMonkey/ImHere/releases/latest).

Requires Android 8.0 (API 26) or newer, an active SIM card, and location services.

## Features

- Large SOS button and a separate "Send my location" action with its own template.
- SOS widget with accidental-tap protection: the second tap must follow within 2 seconds.
- One SMS per recipient; a failure for one contact does not stop the others.
- "Sent to the operator" and "delivered" are reported separately.
- SIM selection; the app never switches SIMs automatically.
- Templates with `{address}`, `{latitude}`, `{longitude}`, `{map_link}`, and `{time}`.
- Contacts, templates, and SIM choice stay on the device; no SOS or location history.
- Russian and English UI.

## How it works

Before sending, ImHere checks permissions, SOS contacts, and the selected SIM.
It requests a fresh location fix for up to 20 seconds and accepts only a fix that
is at most 30 seconds old with accuracy of 100 m or better. If no suitable fix is
available, no SMS is sent. If the template contains `{address}`, reverse
geocoding is attempted for up to 2 seconds; otherwise it is skipped entirely.
Each recipient receives a separate SMS, and the result notification shows the
status per recipient. Delivery reports depend on the carrier and are best effort.

## Permissions

| Permission | Purpose |
| --- | --- |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Location for the message. |
| `SEND_SMS` | Send SMS without opening the system messaging app. |
| `READ_PHONE_STATE` | List active SIMs and select the sending SIM. |
| `READ_CONTACTS` | Optional; used only when picking a recipient from system contacts. |
| `POST_NOTIFICATIONS` | Progress and result notifications (Android 13+). |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `WAKE_LOCK` | Finish the operation while the screen is off. |

The app does **not** request background location, internet access, or SMS history,
and contains no analytics or advertising SDKs.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.
Release builds are signed with a local keystore configured in `keystore.properties`,
which is not tracked by Git.

## Privacy

- Contacts, templates, and the selected SIM are stored on the device only.
- ImHere has no server and no internet permission. Coordinates leave the device
  only in the SMS you send. If a template uses `{address}`, the system `Geocoder`
  may send the coordinates to your device's geocoding provider (often Google);
  templates without `{address}` never use the geocoder.
- No SOS or movement history is stored.
- The widget's double-tap state is kept in `SharedPreferences` and is not history.

## Notes

- On MIUI/Xiaomi, enable autostart and disable aggressive battery optimization for ImHere.
- If the process is killed, late delivery reports are lost and the last shown status remains.
