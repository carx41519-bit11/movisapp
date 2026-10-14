# MOVIS 0.7.0 — Magnolia YOLO Android update

The Android staff interface has been rebuilt with a rice inventory summary,
product cards, transaction cards, a camera screen, annotated quantity review,
inspection status and movement history. Inventory is tracked by product without
storage locations.

The supplied trusted `models/best.pt` is your trained YOLO26n detector for the
single class `magnoliaRice`. Its SHA256 and operating threshold are recorded in
`models/model.json`. The model runs on the backend. The Android APK includes its
identity metadata and compares it with the server before showing the model as
connected. An internet connection is required; the APK does not run PyTorch.

When these weights are loaded, the backend registers a separate Magnolia rice
product if no product already maps to `magnoliaRice`. The original three rice
products and their balances are preserved. They require manually verified
quantities with this single-class model. Magnolia pack size is not assumed to
be 25 kg: verify it in the administrator product settings.

Scanning and image review leave stock unchanged. Stock-in/out require explicit
submission; complete counts require a separate authorized correction. Return
and damage decisions remain with the administrator website.

## Local setup

Install `backend/requirements-yolo.txt` in a development Python environment.
The bundled weights are selected automatically unless `MOVIS_MODE` is set.
Use `MOVIS_MODE=demo` only for explicitly synthetic demonstrations.

```powershell
python -m pip install -r backend/requirements-yolo.txt
python backend/server.py --db movis.db --init
python backend/server.py --db movis.db --host 127.0.0.1 --port 8080
```

Do not initialize an existing database again. If it contains a legacy locations
table, stop writers and run `backend/migrate_remove_locations.py` first. That
tool backs up SQLite with its backup API or PostgreSQL with `pg_dump` and changes
the database transactionally. Existing stock rows combine by item; archived
audit quantities retain their original values. The SQLite-to-PostgreSQL importer
normalizes a private snapshot and leaves its source database untouched.

## Android and deployment

Open `android/` in Android Studio or build `:app:assembleDebug` with Gradle.
Version: 0.7.0 (7), Android API 26 minimum, target/compile API 35.
The prepared APK is debug signed for testing. Install it on a test phone and
check camera permission, capture, review, stock synchronization and retry behavior.

The default server remains `https://movis-1fxf.onrender.com`. The account menu
contains Server connection to use another HTTPS deployment. Deploy the updated
backend using `Dockerfile.yolo` and the private existing database configuration
before expecting real Magnolia detections. See `ANDROID-YOLO-UPDATE.md`.

Historical 0.6.0 reports describe that version only. No hosted deployment or
production database changes were performed for this update.
