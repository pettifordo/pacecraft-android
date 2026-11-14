# CoachMeSimple Android feature parity

Target: the working iPhone app at source commit 496e1c19287ca55956a32da2df2f4c4501d9991f. User selected Wear OS / Pixel Watch / Galaxy Watch for the watch integration. “Complete” requires implementation and relevant checks; hardware-only verification is identified separately.

Already implemented: on-device deterministic fitness/race/load models; Health Connect history and manual runs; next run; whole-plan projection and weekly calendar; prescribed paces and interval work/recovery steps; goals/availability/limits; structured feedback; run reconciliation; private persistence; demo isolation; APK release and emulator workflows.

Implemented in 0.4.0 (verification notes below):

- [x] Beginner/bootstrap onboarding, run/walk progression and graduation to historical fitness.
- [x] Goal feasibility, target trajectory, adherence causes and actionable progress options.
- [x] Complete session families and structured training templates (hills, strides, progression, threshold blocks, interval variants).
- [x] Session adjustments (length, speed, day) with constraints/safety/load consequences and reset.
- [x] One-off unavailable dates, optional cross-training load, physiological baseline signals.
- [x] Plan PDF, calendar export, workout export and Android sharing.
- [x] Morning workout reminders, daily background history refresh and completion notifications.
- [x] Settings/help/glossary and richer history details/recaps.
- [x] Wear OS companion: delivered structured workout, step timer/pace guidance, exercise tracking and completed-run return.
- [ ] Expanded regression and emulator coverage; downloadable phone and Wear APKs.

Platform substitutions: Health Connect replaces HealthKit; Wear OS companion replaces WorkoutKit/Apple Watch; Android sharing/calendar export replaces iOS share/EventKit surfaces. Coaching language remains rules-based per user instruction. Siri-specific surfaces cannot be copied verbatim; Android entry points must use Android platform capabilities.

Publication still requires the owner's Play Console enrolment/verification, production signing, policy declarations and real-phone/watch testing. Account intended for publication: pettifordo@gmail.com.

Verification: 64 pure-engine tests, 2 phone transfer-contract tests and 3 watch state/GPS tests pass locally. Phone and watch APKs compile and pass Android lint. Updated phone/Wear emulator checks are pending their workflow runs; their smoke records will be saved beside the actual screenshots.

Required hardware checks before a production claim: real Health Connect writers (Fitbit/Samsung Health), denied/partial/background permissions, physical paired-watch delivery and return, GPS/HR accuracy, reconnect/process-death behaviour on actual watches, and battery consumption. The Android planner implements the same policy in a smaller model; exact Swift prescription parity remains unproven. Routes/splits/pause-event reconstruction, phone GPS recording, Android Auto/Assistant and cross-platform cloud sync are outside this release. See [porting notes](PORTING.md).
