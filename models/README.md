# Trained Magnolia rice detector

`best.pt` is the supplied YOLO26n detection model trained for `magnoliaRice`.
`model.json` identifies the exact weights by SHA256. The backend loads the model
and maps that class to a distinct Magnolia product. Other rice classes require
manual verification or a separately trained multiclass model.

Use `Dockerfile.yolo` and `MOVIS_MODE=yolo`; the Render blueprint supplies the
embedded file path and checksum. This model executes on the backend, not inside
the Android APK. See `../ANDROID-YOLO-UPDATE.md` for deployment and validation.
