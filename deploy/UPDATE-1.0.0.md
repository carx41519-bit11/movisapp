# MOVIS 1.0.0: warehouse design and stock movements

## Changes

- Android and website share a forest-green, warm-white inventory design.
- Android Java compilation explicitly uses UTF-8; display symbols use Unicode
  escapes. The website's damaged UTF-8 strings are corrected, with SVG icons.
- Photo quantity review has numbered detections and quantity steppers.
- Smart counting still requires a matching app/server model. When unavailable,
  staff can capture photo evidence and enter explicitly verified manual counts.
  A failed pending scan can also be retried as a manual photo count.
- Administrators can receive or dispatch stock on the website using warehouse
  photo evidence, verified quantities and a before/after confirmation.
- Insufficient stock rejects the entire movement; retries retain request IDs.
  Photos and reviews do not change balances until final submission.
- Product class aliases are normalized when editing product mappings.
- Recent movement totals describe the latest 1,000 movements. CSV reports
  continue to include the complete recorded history.

## Deploy

Merge the reviewed pull request into the branch connected to Render, then deploy
using `Dockerfile.yolo` from the repository root. The model is unchanged:

```text
MOVIS_MODE=yolo
MOVIS_WEIGHTS=/app/models/best.pt
MOVIS_WEIGHTS_SHA256=a9c52df8fa40ab14501ae36b9f17be34129eee896fd32202b5dd6276e425b2ce
MOVIS_SEED_DEMO=0
MOVIS_HTTPS_HOSTING=1
```

Keep the existing private database, administrator and image-storage settings.
No database reset or schema migration is required. A successful deployment's
`/health` response reports version `1.0.0`. The new public static script
`/movements.js` must be served alongside `/dashboard.js` and `/styles.css`.

Install the new Android APK (version 1.0.0, code 100) using the same signing
certificate as the installed app. The provided APK is a debug-signed test build.
If Android reports an incompatible signature, rebuild with the original key;
do not uninstall an app with unuploaded photos without saving the work first.

The Android server address stays `https://movisapp.onrender.com`.

## Acceptance checks

1. Check readable arrows, separators and buttons on the phone.
2. Capture and verify both supported rice classes. Check real lighting and angles.
3. Test manual photo counting when smart counting is unavailable.
4. In a test database, receive 3 sacks and confirm one increase of 3.
5. Dispatch 2 sacks and confirm one decrease of 2. Retry the same request and
   verify no second movement is posted.
6. Try dispatching more than the current balance. Verify all balances stay intact.
7. Confirm stock-in/out from the website with photo evidence and review the history.
8. Check small-screen layouts, large text, keyboard and TalkBack on a real phone.

The standalone design preview uses sample data in browser memory. Its Android
screen is an interactive design sample, not an emulator screenshot. No production
balances are present in the preview. Production deployment and real-phone checks
are separate from the local automated and browser checks.
