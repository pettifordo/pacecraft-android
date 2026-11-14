CoachMeSimple Android **0.4.0 test build** adds starting plans, plan controls, exports and a Wear OS companion.

Download **CoachMeSimple-0.4.0-test.apk** for an Android phone. **CoachMeSimple-Wear-0.4.0-test.apk** is a separate companion for a paired Pixel Watch / Galaxy Watch / Wear OS watch.

- Start without six weeks of history: ability-based run/walk and continuous plans, recovery weeks, provisional pace bands and graduation to historical fitness. Slow run/walk sessions affect starting-plan load without creating race fitness evidence.
- Whole plan with prescribed paces, uniform intervals or peak ladders, threshold cruise blocks, hills, progression runs and strides, subject to phase, workload and recovery.
- Adjust next-session length, pace and day with a load/limits preview; reset to the engine recommendation. Add one-off unavailable dates.
- Goal feasibility, target trajectory and recent adherence, with practical options when behind the target.
- Share the full projected plan as PDF, next week as calendar events, or a structured workout as JSON.
- Optional morning reminders, completion notifications and daily history refresh where Health Connect background access is granted.
- Optional resting-HR and HRV baseline changes from Health Connect; cross-training fatigue toggle; help and richer run recaps.
- Send a structured workout to the matching Wear companion. The watch has timed steps, separate work/recovery pace targets, pause/resume, GPS and optional heart rate, durable completion transfer and phone-history import. No measured distance means no invented run.

Phone: Android 9+. Watch: Wear OS 3+ with Google Play services. Install both APKs from this release so their development signatures match. Test signatures may differ from older releases; uninstall an older test build if Android reports a conflict. Uninstalling clears its local data, retaining Health Connect originals.

The pure engine and transfer/state tests cover 69 cases; both modules pass Android lint. Emulator results and any device-only gaps are tracked in [PARITY.md](https://github.com/pettifordo/pacecraft-android/blob/main/docs/PARITY.md). **Paired physical-watch transfer, real GPS/heart rate, cross-vendor Health Connect and battery behaviour still require hardware testing.** This is a sideload testing release; it is not published on Google Play. The Android planner follows the same policy with documented implementation differences, not byte-for-byte Swift prescription parity.
