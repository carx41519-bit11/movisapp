# MOVIS 0.9.0 — Monochrome Android redesign

MOVIS now uses a black-and-white interface with clearer inventory totals, searchable products, grouped movements, and full-screen photo review. This is the native Android client, using the existing MOVIS API.

## Changes

- A compact inventory mark, consistent typography, neutral surfaces, large totals, and clear primary actions.
- Search products by name or SKU without another network request.
- Receive, dispatch, count, return, inspection, and damage workflows retain their existing server operations.
- Photos use numbered bounding boxes and a separate name/confidence list, so long labels do not cover the sacks.
- Review quantities in a full-screen view, with increment/decrement controls, editable whole-number counts, a required verification checkbox, and a fixed save action.
- A monochrome camera screen with a large shutter and framing guides. Camera permissions, EXIF handling, preview sizing, upload retries, and stock confirmation remain in place.
- Existing stock changes still require the final transaction submission or authorized adjustment. Saving a reviewed photo does not submit a transaction.

## Build and install

Open `android/` in Android Studio, use Android SDK 35 and the included Gradle wrapper, and build the application. Version is 0.9.0, version code 11, minimum Android 8.0.

The supplied APK is for internal testing, signed with the same development certificate as the previous 0.8.2 test APK. Its signing key is not included in the source archive. Use the original signing key when making a replacement APK that must install over an existing installation.

## Backend and trained-model identity

The API address remains `https://movisapp.onrender.com`. This UI update requires no Neon migration or reset.

The client retains the model identity currently recorded in `android/app/src/main/assets/scanner_model.json`. This redesign does not install new training weights or deploy Render. A new model must be installed on the backend, have its checksum updated there, and have the matching checksum included in the Android asset before rebuilding.

## Verification

- Java sources compiled against Android API 35 to Java 17 bytecode.
- Resources and the manifest compiled and linked with Android SDK build tools.
- D8 generated the application DEX, including the AndroidX EXIF dependency.
- APK version, package, permissions, endpoint, assets, alignment, and v2/v3 signatures verified.
- Signing certificate matches the previous 0.8.2 test APK.
- Standard Gradle execution is blocked by this workspace's directory permission checks. The test APK was assembled using `javac`, AAPT2, D8, zipalign, and apksigner instead.
- On-device rendering, font scaling, camera behavior, and signed-in end-to-end flows remain to be checked on an Android phone. The workspace could not start/connect to ADB.

## Device acceptance checks

Install the test APK as an update. Check sign-in and keyboard behavior, Inventory/Tasks/Activity navigation, product search including an empty result, and account refresh. Take a new photo under an open task, review each numbered detection, correct the quantities, and close/reopen review. Confirm a photo review alone leaves stock unchanged. Exercise final stock-changing submissions only in your test environment. Check small screens and larger system font settings.
