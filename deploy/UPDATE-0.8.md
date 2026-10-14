# Update MOVIS to 0.8

## Changes

- Inventory, Tasks, and Activity replace the previous four-tab layout.
- Fixed HTTPS endpoint: https://movisapp.onrender.com. Server selection removed.
- Cleaner typography, spacing, product cards, navigation, and task rows.
- Inspections are inside Tasks; completed tasks remain accessible in Activity.
- Matching identity for the new training run (best checkpoint: epoch 77).
- Offline local sign-out, redirect rejection, bounded responses, sampled camera
  decoding, and support for all eight EXIF orientations.
- Legacy activity and unused external-camera provider removed.
- Authorization and scan admission happen before expensive image decoding.
- One scan per backend process, an 8-megapixel input limit, and inference images
  resized to fit 1600 pixels. Existing uploaded photos remain readable.
- CPU thread limits, PostgreSQL lock/query deadlines, and startup-stage logging.

## Install

Install the supplied internal-test APK on a test phone. It is a release build
with debugging disabled, signed with a development certificate. If Android
rejects it as an incompatible update, use the installed app's original signing
key to build an update, or explicitly uninstall the old test app before installing.
Unuploaded photos will be lost on uninstall. Server inventory stays on Neon.

Sign in with an existing account. Camera scanning requires the exact supplied
model and a mapped Magnolia product on the server.

## Deploy backend changes

Upload or merge this source to the branch connected to Render. Auto-deploy may
run on push. Use the repository root and Dockerfile.yolo. Keep these settings:

```text
MOVIS_MODE=yolo
MOVIS_WEIGHTS=/app/models/best.pt
MOVIS_WEIGHTS_SHA256=b9d4bddf0a595c9a597990435c9f204b041d2e49888f04ce4cac8af8056fb33f
MOVIS_SEED_DEMO=0
MOVIS_HTTPS_HOSTING=1
```

Preserve DATABASE_URL, administrator settings, and image-storage settings.
No database reset or model upload to Neon is needed. Use /health for health
checks: an updated deployment reports version 0.8.0 and mode yolo.

Startup logs distinguish inference import, model loading, database initialization,
and account lookup. The resource changes do not guarantee that a particular
Render instance has sufficient RAM; check deployment logs and memory metrics.

## Device acceptance checks

1. Sign in; verify all three screens and the absence of a server picker.
2. Check small screens, large fonts, rotation, keyboard, and TalkBack labels.
3. Capture a fresh Magnolia photo, inspect boxes, and verify quantities.
4. Cancel a review and confirm that stock did not change.
5. Submit a verified test transaction only in a test account/database.
6. Interrupt a request, refresh the task, and retry; verify only one movement.
7. Sign out while offline and confirm that the local session is removed.
8. Check inspections, count corrections, and restricted account permissions.

Live PostgreSQL checks, real-phone camera testing, and production signing remain
release gates. This update is not a claim that all vulnerabilities are eliminated.
