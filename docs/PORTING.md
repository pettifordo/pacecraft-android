# Port scope and provenance

Based on `pettifordo/pacecraft` commit `496e1c19287ca55956a32da2df2f4c4501d9991f` (inspected 14 November 2026). Source licence is retained in `LICENSE`. Feature coverage and hardware verification are tracked in [PARITY.md](PARITY.md).

## Analysis

The Kotlin analysis follows CoachKit's actual implementation: 90% shorter-record overlap duplicate test; weighted linear CS/D′ fit with 50–500 m D′ guard and R² ≥ .95; best-effort prior fallback; maximal-only endurance fit; sparse-effort threshold of three; continuous goal demands; capped durability and sigma; 42-day mean chronic load and three-placement seven-day acute mean; complete calendar weeks for structural ramp; overload thresholds of 1.30/1.50 and .12/.20. Underload is not an overload flag. Equal-performance frontier ties retain the newest observation.

Time and timezone are injected and session IDs are deterministic. Tests verify analytical outputs and regression scenarios; they do not establish byte-for-byte equivalence with the Swift suite, which has not been run here.

## Planning and starting plans

Android uses a smaller implementation of the same policy than the source's roughly 3,000 lines of planning. Scheduling, limits, safety/check-ins, weekly budgets, long-run reservation, goal demands, phase scoring, taper, recovery and down weeks apply. Session families include hills, strides, progression, threshold cruise blocks, uniform intervals and peak ladders, with work/recovery paces and all bookends inside the budget. Template sizing and scoring can choose different prescriptions from Swift. Recent long/hard runs can be inferred from distance/pace without a matched prescription; layoff handling is simpler.

Self-report supplies a provisional starting fitness and conservative load prior, never synthetic history or a race prediction. Starting run/walk samples have a separate low-speed cleaning boundary for load/scheduling. Race-fitness evidence retains the ordinary cleaning boundary. The full history sufficiency gate and outdoor fitness evidence graduate the runner to measured coaching. Reported ability does not override recovery flags.

Goal feasibility uses bounded improvement scenarios and uncertainty credit; trajectory uses a saved baseline, target and race date. These are training scenarios, not promises. Session adjustments preview date, time, weekly distance/count, recent-session ceilings, hard-run spacing and projected load. Raw and adjusted prescriptions remain separately resettable.

## Forecast and history

The whole plan repeatedly assumes each next prescription is completed and recalculates fatigue at fixed current fitness. Virtual runs stay inside the projection. Future race plans end on race day, capped at 210 days; undated/past goals show a fortnight. Weekly charts include completed current-week runs and exclude race distance from training volume. Only the first session is a current prescription.

History matches prescriptions by date and distance (30%, minimum 1 km tolerance). Real-vendor data needs validation. The Health Connect adapter paginates 26 weeks, uses source-scoped distance/HR/elevation aggregates and imports supported aerobic/strength cross-training. Optional resting HR/HRV use three recent days against at least seven baseline days; manual changes override them. Revoked required read permissions remove the imported cache. Routes, kilometre splits and pause events are not imported; elapsed session time is used. Source apps writing distance under another package produce reported skipped sessions, never guessed distance.

## Android and Wear OS

Android sharing exports PDF, a seven-day calendar snapshot and versioned structured-workout JSON. WorkManager provides approximate morning reminders and daily refresh if background history access is granted; Android can delay execution. Foreground imports also produce opted-in completion notifications.

The Wear companion replaces Apple WorkoutKit with a CoachMeSimple step timer, pace targets, foreground GPS/optional heart-rate capture and durable paired-device transfer. Both packages use `com.pacecraft.android` and must share their signing certificate. The selected workout is frozen for an active run. Process death preserves a paused snapshot and requires visible resume. GPS distance is measured, never copied from the prescription; missing distance requires manual entry on the phone. Watch data commits to a durable phone inbox before acknowledgement, then imports by stable ID. The phone app does not record its own GPS route.

No AI backend, accounts or analytics. Explicit sharing and Wear Data Layer transfer can move selected data off the phone. Android cloud backup/device transfer is excluded on both modules. Apple-only Siri/iCloud/WorkoutKit surfaces are replaced where relevant by Android sharing, notifications and the Wear companion; Android Auto/Assistant, maps/splits, and cross-platform cloud sync are outside this release. Physical watch pairing, GPS/HR quality, battery behaviour and vendor Health Connect interoperability remain hardware checks before publication.
