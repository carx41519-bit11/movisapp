# MOVIS 0.8 — Warehouse operations

Android staff app and Python backend for Elder Jay Rice Mill. Three main screens:
Inventory, Tasks, and Activity. Returns and damage inspections are inside Tasks.

The app uses https://movisapp.onrender.com. The server picker and legacy screens
have been removed. Photos and reviewed counts only change stock after explicit
submission. Inventory corrections require separate authorization.

## Build

From android/, with JDK 21 or newer and Android SDK 35:

```
gradlew assembleDebug
gradlew assembleRelease lintRelease
```

Version 0.8.0 (8); Android 8.0 minimum. Configure production signing privately in
android/keystore.properties. Never commit signing keys or passwords.
The delivered internal-test APK uses a development certificate.

## Model

models/best.pt is the new YOLO26n detector for magnoliaRice. Its SHA256 is:

```
b9d4bddf0a595c9a597990435c9f204b041d2e49888f04ce4cac8af8056fb33f
```

models/model.json and android/app/src/main/assets/scanner_model.json identify
these weights. The camera requires a matching, ready server model. Other rice
products use manually verified quantities. Inference runs on the backend.

## Backend

Use Dockerfile.yolo from the repository root. Keep existing private Neon
connection and storage settings. See [the update guide](deploy/UPDATE-0.8.md).
No database reset is needed for this update.

```
python -m pip install -r backend/requirements-yolo.txt
python -m unittest discover -s backend
```

The backend admits one scan at a time before decoding, bounds image size and
PostgreSQL waits, and logs startup stages. PostgreSQL tests require an explicitly
configured disposable test database. Never point tests at production.
