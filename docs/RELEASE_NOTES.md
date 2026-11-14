Android testing build of Pacecraft. Download the **APK** below and send it directly to your tester.

- Requires Android 9 or later. Allow installation from the browser/file app opening the APK when Android asks.
- Start with **Explore with sample history**, or exit demo and connect Health Connect / enter completed runs manually.
- Native Next run, Progress, History and Coach screens; deterministic fitness/load/planning engine; local rule-based coaching comments.
- No account, subscription, cloud AI, analytics or app network permission.

Validation: 34 engine tests (including 360 goal/constraint combinations), Android lint, APK signature verification, and GitHub Actions build checks passed. Real-phone Health Connect interoperability still needs tester feedback. This is an initial port, not complete iOS prescription parity.

See [testing instructions](https://github.com/pettifordo/pacecraft-android/blob/main/docs/TESTING.md) and [porting limitations](https://github.com/pettifordo/pacecraft-android/blob/main/docs/PORTING.md). Google Play publication and production signing are separate later steps; [release guide](https://github.com/pettifordo/pacecraft-android/blob/main/docs/PLAY_RELEASE.md) is included.

This APK uses a development signing key. A future build signed with a different key requires uninstalling this one first, which clears local Pacecraft data; Health Connect originals remain intact.

Version 0.1.1 improves status-bar contrast on the dark theme and corrects singular effort-count wording.
