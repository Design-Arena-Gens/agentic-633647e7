# Kitoko Packer

Kitoko Packer is an Android application focused on ultra-fast scan-to-pack workflows for high-order-volume fulfillment teams. The app guides packers from invoice QR capture through product verification and provides instant "Order Packed" feedback with text-to-speech, while logging every scan to Firebase and supporting CSV report exports.

## Highlights
- **Instant QR Scanning** powered by CameraX `LifecycleCameraController` and ML Kit (QR-format only).
- **Invoice ➜ Checklist ➜ Packet Flow** enforcing invoice-first scans, sku validation, live counters, and packed overlay.
- **Firebase Integration** using Email/Password authentication and Firestore logging via the Firebase BOM (v33).
- **Packed Order Safety** preventing re-processing of completed orders via Jetpack DataStore persistence.
- **CSV Export** generating shareable scan history files through Android `FileProvider`.
- **Modern Android Stack**: Jetpack Compose Material 3 UI, Kotlin 2.0, AGP 8.6, minSdk 23 / targetSdk 34.
- **CI Pipeline**: GitHub Actions workflow builds `assembleDebug` on every push/PR.

## Requirements
- Android Studio Jellyfish (or newer) with JDK 17.
- Android SDK 34 with build tools.
- Firebase project configured for Email/Password Auth and Firestore (replace the placeholder `app/google-services.json`).

## Getting Started
1. **Open the project** in Android Studio.
2. **Add Firebase config**: download `google-services.json` from the Firebase console and replace `app/google-services.json`.
3. **Sync Gradle** and run on a device (real hardware recommended for camera + ML Kit).

## Build & Test
```bash
./gradlew assembleDebug
```

CI runs the same command (see `.github/workflows/android.yml`).

## Stack Reference
| Component | Version |
|-----------|---------|
| Kotlin | 2.0.0 |
| Compose BOM | 2024.10.00 |
| Material3 | 1.3.0 |
| CameraX | 1.3.4 |
| ML Kit Barcode | 17.3.0 |
| Firebase BOM | 33.3.0 |
| Coroutines | 1.8.1 |

## Project Structure
```
app/
  src/main/
    kotlin/com/kitoko/packer/   # Application, ViewModel, UI, camera components
    res/                        # Compose theme, icons, xml providers
  build.gradle.kts              # Android module configuration
.gradle/                        # Gradle wrapper & config
.github/workflows/android.yml   # CI pipeline
```

## Firebase Setup Notes
- Enable Email/Password auth in Firebase Authentication.
- Create a Firestore database in production mode.
- Optionally secure Firestore with rules scoped to authenticated users.

## CSV Exports
CSV files are written to the app cache (`cache/export`) and shared via the Android Sharesheet. Each row includes `timestamp`, `orderId`, `sku`, `quantity`, `source`.

## License
MIT (see `LICENSE` if present) or update to your preferred license.
