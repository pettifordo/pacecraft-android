# CoachMeSimple Android feature coverage and parity gaps

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
- [x] Expanded Android regression and phone/Wear core emulator coverage; downloadable phone and Wear APKs.

Platform substitutions: Health Connect replaces HealthKit; Wear OS companion replaces WorkoutKit/Apple Watch; Android sharing/calendar export replaces iOS share/EventKit surfaces. Coaching language remains rules-based per user instruction. Siri-specific surfaces cannot be copied verbatim; Android entry points must use Android platform capabilities.

Publication still requires the owner's Play Console enrolment/verification, production signing, policy declarations and real-phone/watch testing. Account intended for publication: pettifordo@gmail.com.

Verification: 64 pure-engine tests, 2 phone transfer-contract tests and 3 watch state/GPS tests pass locally. Phone and watch APKs compile and pass Android lint. Wear OS emulator checks pass restoration, visible foreground start, timed work transition, pace targets, pause/end and no invented distance. Phone checks pass the whole plan/interval detail, beginner/run-walk onboarding, adjustment preview, PDF/calendar sharing, persistence and exactly-once completed-watch-run import. Smoke records are saved beside the actual screenshots.

Required hardware checks before a production claim: real Health Connect writers (Fitbit/Samsung Health), denied/partial/background permissions, physical paired-watch delivery and return, GPS/HR accuracy, reconnect/process-death behaviour on actual watches, and battery consumption. The Android planner implements the same policy in a smaller model; exact Swift prescription parity remains unproven. Routes/splits/pause-event reconstruction, phone GPS recording, Android Auto/Assistant and cross-platform cloud sync are outside this release. See [porting notes](PORTING.md).

## Not yet full iPhone parity

- [ ] Shared conformance fixtures run against Swift and Kotlin; equal inputs/clock/timezone must produce matching coaching decisions. The current 69 tests are Android tests.
- [ ] Exact planner, template sizing, beginner progression and adjustment-policy alignment, or replacement with one shared engine.
- [ ] iPhone split/cadence-rich run recap analysis and corresponding Android history ingestion.
- [ ] Physical Health Connect / Pixel Watch / Galaxy Watch verification described above.

No automatic synchronization exists between repositories. [ENGINE_SYNC.md](ENGINE_SYNC.md) describes the recommended shared-fixture and single-engine migration; it is not implemented yet. Android 0.4.0 is a testing release, not a completed iPhone parity claim.
