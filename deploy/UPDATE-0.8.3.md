# Buko Pandan and Magnolia model update (0.8.3)

The bundled YOLO26n model now distinguishes `bukopandan_rice` (class 0) and
`magnolia_rice` (class 1), trained at 640 pixels. The backend maps these labels
to the existing Buko Pandan and Magnolia products. Existing balances and
transactions remain in the current database. No database reset is required.

## Render

Deploy the reviewed commit using the repository root and `Dockerfile.yolo`.
Before deployment, update the service's Environment setting:

```text
MOVIS_MODE=yolo
MOVIS_WEIGHTS=/app/models/best.pt
MOVIS_WEIGHTS_SHA256=a9c52df8fa40ab14501ae36b9f17be34129eee896fd32202b5dd6276e425b2ce
MOVIS_SEED_DEMO=0
MOVIS_HTTPS_HOSTING=1
```

Keep the existing private database, account and image-storage settings.
If the service defines `MOVIS_WEIGHTS_URL`, verify that it is not overriding
the bundled file with older weights. The bundled file at the path above takes
precedence when it exists. A manually configured checksum in Render must match
the new value even though `render.yaml` also contains it.

After deployment, `/health` should report version `0.8.3` and mode `yolo`.
After signing in, `/v2/state` should report a ready scanner, the checksum above,
classes `buko_pandan` and `magnoliaRice`, and no unmapped classes.

## Android

Build and install Android version 0.8.3 (version code 11) with the same signing
key used for the installed app. The app keeps the existing server address,
https://movisapp.onrender.com. The new bundled scanner metadata is required:
older APKs expect a different model checksum and disable camera scanning with
the new server model. Photos are still processed by Render, not on the phone.

## Verify

Test fresh photos of Buko Pandan alone, Magnolia alone, and both together.
Check each label and count. Cancel a review and confirm that balances are
unchanged. Use a test database for posting test stock movements. Sinandomeng
and Jasmine still require manually verified quantities.

The automated suite exercises both label mappings, product and balance
preservation, matching deployment metadata, and a real Magnolia fixture.
A real Buko Pandan photo and real-phone testing are needed to assess detection
accuracy and camera behavior in the deployment environment.
