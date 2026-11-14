# Pacecraft for Android

An on-device running coach for Android, adapted from [pettifordo/pacecraft](https://github.com/pettifordo/pacecraft). Native Kotlin + Jetpack Compose. The deterministic training engine and run history are the product; coaching comments use local rules, with no AI dependency.

## Try the APK

Download `pacecraft-0.2.0-test.apk` from this repository's Releases and send the file to your tester. On their Android phone, open the downloaded file, allow **Install unknown apps** for the app opening it if prompted, and install Pacecraft. Android 9 or later is required. The test APK is signed with a development key and is not a Google Play release.

Start with **Explore with sample history** to inspect all five tabs without granting access. Demo history is isolated from real history and edits are disabled while it is active. Exit demo before connecting data or editing settings.

For real data, install/update Health Connect where needed, enable sharing from the watch/recording app into Health Connect, and allow Pacecraft to read **exercise sessions and distance**. Heart rate and elevation are optional. On supported devices, allow **history** access to import the 26-week window. Without that permission, the platform may only expose the 30 days before first permission grant plus subsequent records; Pacecraft will not invent missing history. Manual run entry works independently of Health Connect.

## Implemented

- Whole-plan tab: weekly projection through race day (up to 30 weeks), expandable days and session steps, weekly training-volume chart, and a fortnight for goals without a race date. Later sessions are forecasts; they adapt after real runs and check-ins.
- Next-run prescription, including pace bands, actual structured-step totals, reasons, skip-day and check-in actions.
- Health Connect running and treadmill sessions, distance, optional heart rate/elevation, plus supported cycling/swimming/rowing/strength cross-training for load.
- Paginated 26-week import, source-scoped aggregates, duplicate-run cleaning, refresh on foreground, manual history and run detail.
- Critical Speed / D′ regression, weighted effort frontier, endurance exponent, uncertainty bands and race-distance durability requirements.
- Rolling acute/chronic load, calendar-week ramp checks, cross-training fatigue and headroom, safety priority, feedback restrictions, taper/down weeks, race recovery.
- Goal distance/date/time, session count, unavailable weekdays, preferred long-run day, intensity, weekly/time caps, metric/imperial display and optional heart-rate profile.
- Local atomic persistence, run-to-prescription matching, local-data deletion and permission-revocation handling. No network permission, analytics, account or cloud AI.
- Pure JVM engine tests, Android lint and CI APK artifacts.

## Build

Install JDK 17 and Android SDK platform 36/build-tools 35.0.0 (or open in Android Studio). Set `ANDROID_HOME`, or put `sdk.dir=/path/to/Android/sdk` in untracked `local.properties`.

```sh
./gradlew :engine:test :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. CI builds are downloadable under Actions → Android checks and test APK → Artifacts. CI development signatures can differ from this first APK; uninstall the older build before installing one signed by a different development key (uninstalling clears local data).

## Structure

- `engine/`: Android-independent Kotlin models, analysis, planner and rules comments.
- `app/`: Compose UI, Health Connect adapter, lifecycle refresh and private JSON store.
- `docs/PORTING.md`: source revision, parity boundaries and known gaps.
- `docs/TESTING.md`: friend-testing instructions.
- `docs/PLAY_RELEASE.md`: publishing later using `pettifordo@gmail.com`.

This is an initial Android testing release, not a claim of complete iOS feature parity. Read the porting notes before comparing prescriptions across platforms. Real-device Health Connect interoperability and coaching calibration need tester feedback.

## Emulator screenshots

[View the actual Android screenshots](docs/screenshots) captured on a standard Android 15 emulator with demo history. The [emulator workflow](.github/workflows/screenshots.yml) exercises all five tabs and verifies that demo history does not enter the real local cache. It can be rerun from Actions.

| Next run | Progress |
| --- | --- |
| ![Next run](docs/screenshots/next-run.png) | ![Progress](docs/screenshots/progress.png) |
