# Android and trained YOLO update — 14 October 2026

## What changed

- Redesigned Android login, inventory, transaction selection, camera, photo
  review, pending inspection and movement history with a teal rice-mill theme.
- Added annotated photo review with bounding boxes and confidence labels.
- Added a server connection setting and a visible trained-model identity check.
- Included your trained YOLO26n weights in `models/best.pt` for backend inference.
- Added backend scanner status, supported-product flags and exact class mapping.
- Registered Magnolia rice as a separate product when its trained model loads.
  Existing products and balances are preserved; the model does not distinguish
  Sinandomeng, Jasmine and Buko Pandan.
- Generalized product selection, checked quantities and administrator product
  settings to accommodate Magnolia alongside existing products.
- Configured the YOLO Docker image and Render blueprint for the bundled weights.
- Retained the earlier removal of storage locations from the database schemas
  and backend; the supplied migration must still run on any existing old database.

The APK sends photos to your server; it contains model identity metadata rather
than a local PyTorch engine. Capturing or reviewing a photo never updates stock.

## Verification

- 119 backend tests: 74 passed, 45 PostgreSQL checks skipped because no disposable
  PostgreSQL database was configured.
- Real model checks verified a held-out Magnolia photo produces detections mapped
  to the Magnolia product, leaves stock unchanged until review and submission,
  and records an identical retry only once.
- Existing accounts/products remain available and model startup is idempotent.
- Java sources compiled against Android API 35 with Java 17 bytecode, including
  the new camera and review interface. D8 conversion completed.
- APK package/version, camera and internet permissions, launcher, 4-byte resource
  alignment, and APK v2/v3 signatures were verified.
- Windows AAPT2 failed during ZIP finalization. The APK was assembled from complete
  resource entries whose sizes and CRCs were checked, compiled DEX and model
  metadata, then signed and independently verified. A normal Gradle/Android Studio
  build on an unrestricted machine remains to be checked.
- Emulator launch could not proceed because hardware acceleration was unavailable.
  Real-phone installation, layout and camera behavior remain unverified.

## Enable the model on the hosted server

1. Back up the current hosted database. If it still contains `locations`, stop
   backend writers and run `python backend/migrate_remove_locations.py` with the
   existing private `DATABASE_URL` environment variable. PostgreSQL requires
   `pg_dump`; choose a safe backup filename with `--backup`. The tool does not
   print the database connection string. Never reset the production database.
2. Publish this updated source, including `models/best.pt` and `models/model.json`,
   to the repository used by your Render service. No push was performed here.
3. Build that service using `Dockerfile.yolo`.
4. Preserve existing account and database environment variables. Set:

   ```text
   MOVIS_MODE=yolo
   MOVIS_WEIGHTS=/app/models/best.pt
   MOVIS_WEIGHTS_SHA256=43e6da5c87ecb697cef7878abcb8aa17133ab40a8b1cabf08c00baf770e80810
   MOVIS_SEED_DEMO=0
   MOVIS_HTTPS_HOSTING=1
   ```

5. Install `MOVIS-0.7.0-Magnolia.apk`. Sign in. The inventory screen should show
   "MAGNOLIA YOLO · CONNECTED". Demo or different weights do not get this badge.
6. Start Stock in and select Magnolia rice. Capture a new photo, review the boxes
   and quantity, then submit only after checking the physical sacks.
7. Verify the administrator website shows the same product balance and movement.

The default address is https://movis-1fxf.onrender.com. If your deployment has
another HTTPS URL, change it from the app's account menu. Your existing hosted
service and production database have not been modified.

The detector recognizes the single class `magnoliaRice` at confidence 0.35.
Always verify missed, duplicated or misidentified sacks before posting stock.
This app is a debug-signed test build, not a Play Store release.
