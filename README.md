# HRV IB (Polar H10 Resonance Breathing)

Android app for real-time HR + RR interval collection, breathing session guidance, and resonance-frequency analysis.

## Tech stack

- Kotlin, Compose, MVVM, Coroutines/Flow
- Room (offline-first local persistence)
- Hilt DI
- BLE abstraction:
  - `RealBleClient` for production BLE GATT
  - `FakeBleClient` for emulator/demo/testing

## Setup

1. Open `hrv_ib/` in Android Studio (JDK 17).
2. Sync Gradle.
3. Run `app` on device/emulator.
4. Default is **Demo Mode ON** so you can test without Polar hardware.

## R0 release gate (authoritative in CI)

R0 is validated in GitHub Actions so local Android SDK is not required for release decisions.

Workflow: `.github/workflows/android-ci.yml`

It runs on push/PR:

- `./gradlew assembleDebug assembleRelease`
- `./gradlew test`
- `./gradlew lint`
- `./gradlew connectedDebugAndroidTest` (headless emulator, Fake BLE only)

### Artifacts from CI

Download from each workflow run:

- release APK (`**/build/outputs/apk/release/*.apk`)
- lint/unit/androidTest reports (`**/build/reports/**`, `**/build/test-results/**`)
- androidTest outputs (`**/build/outputs/androidTest-results/**`)

### Local optional runner

If you still want to run R0 locally:

- macOS/Linux: `scripts/run_r0.sh`
- Windows: `scripts/run_r0.bat`

Both scripts perform preflight checks for SDK path (`sdk.dir`, `ANDROID_SDK_ROOT`, `ANDROID_HOME`) and print clear setup instructions instead of failing with a raw Gradle SDK error.

## BLE notes

- Uses Heart Rate Service `0x180D` and Heart Rate Measurement characteristic `0x2A37`.
- RR intervals from `2A37` are parsed from `1/1024 sec` to `ms`.
- Android 12+ permissions: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`.
- If RR is absent, HR still displays and rolling HRV displays `—`.

## HRV math and cleaning rules

### Real-time HRV

- Rolling 3-second window.
- Recomputed every 1 second.
- RMSSD:
  - `diffs = rr[i] - rr[i-1]`
  - `RMSSD = sqrt(mean(diffs^2))`
- If fewer than 3 RR intervals in window: display `—`.
- UI displays smoothed HRV with EMA (`alpha=0.2`), raw value is still stored.

### Session summary

1. Exclude first 60 seconds from summary.
2. Artifact rejection on RR:
   - Keep only RR in `[300, 2000] ms`.
   - Remove sudden changes `>20%` vs previous RR unless surrounding points are stable.
3. Build per-epoch rolling 3s RMSSD series every 1 second.
4. Outlier removal with MAD filter:
   - `m = median(x)`
   - `mad = median(|x-m|)`
   - `scaled_mad = 1.4826 * mad`
   - Keep values where `|x-m| <= 3.5 * scaled_mad`
5. Summary:
   - `avgHRV = mean(cleaned_epoch_hrv)`
   - `peakHRV = max(cleaned_epoch_hrv)`
   - `avgHR = mean(60000 / rr_ms)` after artifact cleaning

## Screens

- Home: Garmin-like range selector (`day/week/month/year`), scatter + time-series charts
- Device: scan/connect/status
- Session Setup: metronome/inhale/exhale/duration controls
- Live Session: phase, wave-like RR chart, HR, rolling HRV
- Session Summary
- Session Detail with soft delete/restore

## Fake BLE test vectors

Assets in `app/src/main/assets/vectors/`:

1. `hr_only.json`
2. `hr_rr_valid.json`
3. `disconnect_reconnect.json`
4. `artifact_rr.json`
5. `sparse_rr.json`

## Test commands

- `./gradlew test`
- `./gradlew connectedDebugAndroidTest`
- `./gradlew lint`
- `./gradlew assembleDebug assembleRelease`
