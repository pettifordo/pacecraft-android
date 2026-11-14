CoachMeSimple Android **0.3.0 test build** makes the prescriptions visible in the whole plan. Download the APK below and send it to your Android tester.

- Every planned run shows its prescribed pace band in min/km or min/mi.
- Interval rows show the actual rep count, work duration and jog recovery, with separate pace bands for work and recovery.
- Tap a run for warm-up, every work/recovery step and cool-down, with times, estimated distances and paces.
- Weeks containing interval sessions are marked **Includes intervals**. The engine already schedules intervals where phase, load and recovery permit; this build exposes those prescriptions.
- Long runs with a steady finish show separate easy and finish paces.

The Plan tab projects through race day (up to 30 weeks), or two weeks for undated goals. Only the next run is the prescription; later sessions adapt with your real history and check-ins. No projected runs enter real history or fitness estimation.

Requires Android 9+. This is a development-signed test build: uninstall an older test build first if Android reports a signing conflict. Uninstalling clears local CoachMeSimple data but leaves Health Connect originals untouched.

Regression checks cover pace conversion, interval totals/recovery placement, and actual interval scheduling in short-race blocks. Emulator checks exercise the plan and an interval detail screen. Real-phone Health Connect interoperability still needs tester feedback. See [porting notes](https://github.com/pettifordo/pacecraft-android/blob/main/docs/PORTING.md), [testing instructions](https://github.com/pettifordo/pacecraft-android/blob/main/docs/TESTING.md), and the [Google Play guide](https://github.com/pettifordo/pacecraft-android/blob/main/docs/PLAY_RELEASE.md).
