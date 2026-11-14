# Google Play release later

Intended owner account: **pettifordo@gmail.com**. No Google account credentials belong in this repository, and no Play Console account has been created or accessed by this implementation.

1. Sign in to Google Play Console using that Google account. Enrol/verify the appropriate developer account and complete any identity/device/account requirements shown by Google.
2. Decide whether to retain `com.pacecraft.android` as the permanent application ID before the first Play upload. Once published, that ID identifies the app. Check availability in your Play account.
3. Create and securely back up a production upload key; enable Play App Signing. Keep the upload keystore and passwords outside Git and give CI access only through encrypted secrets. The included development APK/key is for sideload testing only.
4. Configure the release signing block or sign through Android Studio's **Generate Signed Bundle / APK** flow. Build an Android App Bundle (`./gradlew :app:bundleRelease` after configuring signing). Increment `versionCode` on every upload. Production signing is deliberately not configured with a disposable test key.
5. Recheck the current Play target-API requirement and update compile/target SDK if required. This first testing build targets API 36; do not assume that meets the requirement on the day you publish.
6. Publish a public privacy policy, complete Data safety and the Health apps declaration, and request/justify each Health Connect permission in Play Console. Review whether older-history access is needed. The app's rationale screen is included, but it is not a substitute for Google's review or a hosted policy.
7. Test the signed bundle on an internal/closed track. Follow the testing requirements displayed for this developer account before requesting production access. Complete content rating, screenshots, store listing, support contact and release notes.
8. Test first-run permissions, denied/revoked/partial access, missing provider, cross-vendor workout data, offline operation, daylight-saving dates, accessibility/large text, upgrades and data deletion on real phones before widening the rollout.

A Play-signed production build may require uninstalling this debug-signed test app first. This clears local CoachMeSimple data; health originals can be imported again. A proper upgrade path requires consistent production signing from the first release.

## Wear and health permissions

Create a Wear OS form-factor release using `:wear:bundleRelease`, signed with the same production certificate/package as the phone app. Use distinct, increasing version codes for phone and Wear uploads (the test uses phone `6`, Wear `100006`), and increment them for each release and review Wear quality requirements. The phone targets API 36; the Wear companion currently targets API 35. Recheck current form-factor-specific Play deadlines before publishing.

Review the new optional resting-HR/HRV and background Health Connect permissions, notifications, foreground health/location service declarations and Wear sensor/location permissions. The public draft [privacy policy](PRIVACY.md) must match the final release and Play Data safety answers; complete any required Health Connect/background access review. Physical Pixel Watch/Galaxy Watch checks are still required.
