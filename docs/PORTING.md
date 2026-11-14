# Port scope and provenance

Based on `pettifordo/pacecraft` commit `496e1c19287ca55956a32da2df2f4c4501d9991f` (inspected 14 November 2026). Source licence is retained in `LICENSE`.

## Analysis port

The Kotlin analysis follows the actual CoachKit implementation, which differs from some older prose specifications: a 90% shorter-record overlap duplicate test; weighted linear CS/D′ fit with 50–500 m D′ guard and R² ≥ .95; best-effort prior fallback; maximal-only endurance fit; current sparse-effort threshold of three; continuous goal demands shared by prediction/planning; capped durability and sigma; 42-day mean chronic load and three-placement seven-day acute mean; complete calendar weeks for structural ramp; overload thresholds of 1.30/1.50 and .12/.20. Underload is not an overload flag.

Equal-distance/equal-speed frontier ties retain the newest observation, so repeated identical performances do not eliminate one another.

Tests verify known analytical outputs and regression scenarios inspired by CoachKit. They do not establish byte-for-byte parity with the Swift suite, which has not been run on this machine. Time and timezone are injected into the engine, and session IDs are derived deterministically.

## Android planner adaptations

The Android planner is a smaller implementation of the same policy, not a line-for-line port of the ~3,000 lines of Swift planning code. It implements scheduling constraints, history reconciliation, safety/feedback priority, weekly budgets, long-run reservation, goal demands, phase scoring, recovery, taper, down weeks and bounded session structures.

Current differences/gaps:

- No hills, strides or progression session families; intervals use three-minute repeats, and threshold uses a bounded continuous block. The full Swift template ladder, session tweaks, full-plan projection and diary export are not ported.
- Completed long runs can be inferred from distance, and recent hard runs from pace, when there is no recorded matched prescription. Matching is same-day and within 30% distance (minimum 1 km tolerance). This is an initial reconciliation policy and needs testing against real watch data.
- Return-to-training budget uses a simpler layoff reduction. Goal-time comments compare the current estimate to the target; the full Swift feasibility and trajectory model is not yet ported. Goal time never forces unsafe training.
- The source's resting-HR/HRV deviation signals are not ingested. The optional heart-rate profile supports maximal-effort classification and TRIMP, not illness detection.
- The Health Connect adapter does not import routes, kilometre splits, pause events or resting-HR/HRV baselines. Elapsed workout time is used. Distance/heart-rate/elevation aggregates are scoped to the workout writer to avoid combining duplicate sources. Apps writing distance under another package may produce skipped sessions; the import reports this instead of guessing distance.
- Cross-training types are explicitly limited to supported aerobic and strength families. No wearable workout export, background sync, notifications, Android Auto or Google Assistant integration.
- No network permission. Local app storage is not backed up. No run data leaves the phone via this app.
- A sufficient history still needs usable outdoor efforts to fit fitness; treadmill-only history is retained for load/history but cannot anchor race estimates. The original bootstrap questionnaire is not ported.

Future work should port additional Swift regression fixtures before claiming prescription parity, then validate cross-vendor Health Connect data and Play Health Connect policy compliance on real devices.
