# Buko Pandan and Magnolia rice detector

`best.pt` is the supplied YOLO26n detector trained at 640 pixels for
`bukopandan_rice` (class 0) and `magnolia_rice` (class 1). The backend maps them
to existing product classes `buko_pandan` and `magnoliaRice` respectively.
`model.json` identifies the exact weights by SHA256. Existing products and
balances are retained. Sinandomeng and Jasmine require manual verification.

Use `Dockerfile.yolo` and `MOVIS_MODE=yolo`. Update the Render service checksum
to match the manifest. Inference runs on the backend, not inside the Android APK.
See [the update guide](../deploy/UPDATE-0.8.3.md).
