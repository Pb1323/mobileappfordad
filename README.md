# SMS Exporter Android

Android-only, local-only SMS export app. It reads SMS messages after the user grants permission, then writes shareable files to the phone's Downloads folder.

## What it exports

- `sms-export-YYYYMMDD-HHMMSS.txt` - readable transcript
- `sms-export-YYYYMMDD-HHMMSS.csv` - spreadsheet-friendly export
- `sms-export-YYYYMMDD-HHMMSS.html` - browser-readable document

Each message includes sender/number, direction, timestamp, message text, thread id, and SMS id.

The app does not upload messages to a server. Everything happens locally on the phone.

## GitHub download flow

This project includes `.github/workflows/android-build.yml`. When you upload it to GitHub, the workflow builds a debug APK and stores it as an artifact named `sms-exporter-debug-apk`.

To install on an Android phone:

1. Open the GitHub repository on the phone.
2. Go to `Actions`.
3. Open the latest successful build.
4. Download the APK artifact.
5. Install the APK. Android may ask you to allow installs from the browser or file manager.
6. Open the app, grant SMS permission, then tap `Export SMS`.

For easier public downloading, create a GitHub Release and upload the generated APK there.

## Build locally

Open this folder in Android Studio and run the `app` configuration.

Or from a machine with Android SDK and Gradle installed:

```bash
gradle assembleDebug
```

The APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Privacy notes

SMS content can contain private, financial, medical, or security-sensitive information. Export files are stored in Downloads so the user can see, move, delete, or share them manually.

## Limitations

- Android only.
- Exports SMS database text rows exposed through Android's SMS provider.
- MMS attachments are not exported.
- Requires Android 10 or newer and explicit user permission before SMS can be read.
- This is designed for sideloading from GitHub, not Play Store distribution.
