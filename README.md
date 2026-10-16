# MOVIS 1.0.0 — Warehouse operations

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

Version 1.0.0 (100); Android 8.0 minimum. Configure production signing privately in
android/keystore.properties. Never commit signing keys or passwords.
The delivered internal-test APK uses a development certificate.

## Model

models/best.pt is the YOLO26n detector for Buko Pandan and Magnolia rice, trained at 640 pixels. Its raw labels `bukopandan_rice` and `magnolia_rice` map to the existing product classes `buko_pandan` and `magnoliaRice`. Its SHA256 is:

```
a9c52df8fa40ab14501ae36b9f17be34129eee896fd32202b5dd6276e425b2ce
```

models/model.json and android/app/src/main/assets/scanner_model.json identify
these weights. Smart counting requires a matching, ready server model. Manual photo counts remain available when smart counting is unavailable. Website stock-in/out uses administrator-verified photo evidence. Inference runs on the backend.

## Backend

Use Dockerfile.yolo from the repository root. Keep existing private Neon
connection and storage settings. See [the update guide](deploy/UPDATE-1.0.0.md).
No database reset is needed for this update.

```
python -m pip install -r backend/requirements-yolo.txt
python -m unittest discover -s backend
```

The backend admits one scan at a time before decoding, bounds image size and
PostgreSQL waits, and logs startup stages. PostgreSQL tests require an explicitly
configured disposable test database. Never point tests at production.
