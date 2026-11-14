# Keeping iPhone and Android aligned

## Current status

There are two independent implementations. The iPhone CoachKit source at `496e1c19287ca55956a32da2df2f4c4501d9991f` is the reference for the Android port. Android 0.4.0 has broad feature coverage, but its smaller Kotlin planner is **not proven to produce identical coaching decisions**. Its 69 tests validate Android behaviour; they are not a shared Swift/Kotlin conformance suite. Changes to one repository do not automatically propagate to the other.

Feature coverage, behavioural equivalence and device integration are separate checks. The remaining differences include planner/scoring/sizing and bootstrap/tweak details, richer split/cadence-based iPhone recaps, and device-specific Health Connect/Wear validation. See [PARITY.md](PARITY.md) and [PORTING.md](PORTING.md).

## Recommended sequence

1. **Freeze the reference policy and capture shared fixtures first.** Export inputs and expected outputs from the existing Swift CoachKit, with fixed clock/timezone and synthetic run history. Cover fitness, uncertainty, fatigue, safety/check-in priority, weekly/time limits, dates, every session family, beginner graduation, adjustments, reconciliation and goal progress. Record deliberate platform substitutions separately.
2. **Run those fixtures against both engines in CI.** Compare numbers within documented tolerances, but compare dates, session types, work/recovery structure and safety decisions exactly. A passing Android-only test suite cannot substitute for this gate. Add a fixture whenever a coaching bug is fixed. The present repositories do not yet have this shared gate.
3. **Consolidate the deterministic core into one library.** Kotlin Multiplatform is a practical choice for the current native apps: Android consumes the Kotlin library; Swift consumes an iOS framework through Swift Package Manager. Keep HealthKit/Health Connect, watch APIs, storage, notifications and UI in their native apps. Move rules only after the shared fixtures establish the reference behaviour; do not make the current smaller Android planner authoritative by accident.
4. **Release the engine as a versioned dependency.** Publish the Android artifact and iOS XCFramework from the same engine tag/commit, with a policy/schema version and checksums. Each app pins an engine version and exposes it in Settings. UI releases can differ; coaching policy upgrades should land on both apps together, or be explicitly marked as different engine versions.
5. **Remove the duplicate rules after cutover.** Native apps map platform records into one shared input schema and render one shared result schema. Do not keep an independently maintained Swift planner beside the shared engine. Both native CI suites still test adapter behaviour and build the app; physical health/watch checks remain necessary.

The current JVM engine uses `java.time`, Java UUID generation and JVM formatting. These must be replaced or isolated before it can compile for iOS. Swift-facing API design, the original CoachKit regression cases, schema migration, numeric tolerances and macOS/Xcode CI are part of the migration, not work already completed by changing a Gradle plugin.

## Release rule

Until the shared engine is live, a training-rule change needs linked changes in both implementations and the same conformance fixture. Both suites must pass before claiming matching behaviour. Afterwards, change the rule once in the shared library, publish an engine version, and update both pinned dependencies. Do not automatically copy commits or translate source files between the application repositories.
