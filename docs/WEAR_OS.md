# Wear OS testing

Use the phone and watch APKs from the **same release**. Android package `com.pacecraft.android` and signing certificate must match for the Data Layer to work. The release workflow builds both together with one development key. A watch APK belongs on the watch, not on the phone.

1. Pair Pixel Watch / Galaxy Watch / another Wear OS 3+ watch using its normal Android companion app.
2. Install the phone APK normally. Install the Wear APK on the watch using Android Studio's device selector or Wear OS wireless debugging: enable Developer options → ADB debugging / Wireless debugging, pair using the displayed pairing port (`adb pair WATCH_IP:PAIR_PORT`), connect to its debugging port (`adb connect WATCH_IP:DEBUG_PORT`), then `adb -s WATCH_IP:DEBUG_PORT install CoachMeSimple-Wear-0.4.0-test.apk`. These are different ports on many watches. Disable debugging afterwards if no longer needed.
3. Open both CoachMeSimple apps. On the phone, leave demo, create/import a plan and tap **Send workout to Wear OS**. Reconnection delivers a queued workout. The watch can also use **Sync with phone**.
4. Review the step list and start outdoors. Physical activity permission enables exercise tracking. Location supplies actual distance; body sensors optionally supply heart rate. Pace targets switch with each timed step and the watch vibrates on transitions. Target pace is guidance; on hills use effort.
5. Scroll for Pause/Resume/Finish. Finishing commits a measured completed run to a durable outbox; reconnection transfers it. Open phone History or refresh. No measured distance means the watch asks you to add the run manually; it never substitutes prescribed distance.
6. Starting a newer workout does not replace one already running. A killed process restores the saved exercise paused, requiring Resume from the visible activity.

The watch emulator checks synthetic structured input, restoration, timing, permissions/foreground-service start, pause/end and refusal to invent distance. It cannot establish GPS accuracy, physiological sensor quality, battery consumption or end-to-end physical pairing. Validate those on Pixel Watch and Galaxy Watch before Play release. Third-party workouts can still enter history independently through Fitbit/Samsung Health → Health Connect, even without installing this companion.
