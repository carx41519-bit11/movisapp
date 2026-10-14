# MOVIS on Neon

The configured project is `shy-darkness-48557854`, organization
`org-shy-sea-38487102`, branch `production` in `aws-us-east-2`.
`neon.ts` declares a private `assets` bucket. The branch supplies PostgreSQL.
Neon provisions backend services; the Python website still needs a Python host.

## Install and run

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r backend/requirements-cloud.txt
npm ci
npx neon@latest login
npx neon@latest init -y --no-agent-setup --org-id org-shy-sea-38487102 --project-id shy-darkness-48557854 --branch production --services postgres,object-storage
npx neon@latest deploy
```

Deployment pulls `DATABASE_URL`, `DATABASE_URL_UNPOOLED`, `NEON_BRANCH`,
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_ENDPOINT_URL_S3`, and
`AWS_REGION` into `.env.local` (or an existing `.env`). Keep these files private.
The local startup loads `.env.local` before `.env`; hosting environment values win.

For a fresh database, set `MOVIS_ADMIN_USERNAME` and a new private
`MOVIS_ADMIN_PASSWORD` of at least 12 characters in `.env.local` before starting.
The existing startup creates the schema and bootstraps that administrator only
when there are no users. Existing accounts are preserved. This integration's
live tests do not initialize production accounts or change production inventory.

For real detection, install `backend/requirements-yolo.txt`. The existing model
configuration remains in use. For an explicitly synthetic local demonstration,
set `MOVIS_MODE=demo`; this does not validate the YOLO detector.

```powershell
npm run build
npm start
```

Open http://127.0.0.1:8080. The start command uses the local virtual environment,
or the `PYTHON` environment override. On Linux, create `.venv` and install using
`.venv/bin/python`. Production hosting keeps the existing Gunicorn command
`gunicorn --chdir backend 'webapp:create_app()'`; inject the Neon environment
variables into that host's private environment settings. Do not expose them in
browser JavaScript or the Android APK.

## Integration

- Existing `psycopg` driver and transaction semantics are retained. Application
  traffic uses pooled `DATABASE_URL`; use `DATABASE_URL_UNPOOLED` for migration
  tools, backups and schema-isolated tests.
- `boto3` uses Neon's AWS environment variables with SigV4 and path addressing.
- New scan photos are uploaded after account/action validation. PostgreSQL stores
  a `neon-s3:bucket/key` reference. Authorized image reads retrieve the bytes from
  the private bucket and return the same base64 format Android/web already use.
- Existing inline images remain readable. They are not bulk-migrated.
- Upload retry keys are deterministic. Storage failures fail the request with
  503. Deletion hides the image in PostgreSQL before deleting the object; if
  storage deletion fails, retry the same request to complete cleanup.
- An upload that succeeds in S3 but subsequently fails its database commit can
  leave an unreferenced object. There is no automatic orphan sweep yet.
- With no S3 endpoint configured, local SQLite workflows keep storing inline
  images. Partially configured S3 credentials fail startup.

## Verification

`npm run build` compiles the Python source, checks dashboard JavaScript syntax,
and verifies required static assets. This app has no frontend bundling step;
this command does not build the Android APK.

`npm test` runs the repository's Python suite. Tests requiring an explicit
PostgreSQL test URL or YOLO dependencies are skipped if unavailable.

`npm run test:neon` is the live service proof. It uses a uniquely named disposable
PostgreSQL schema on the configured branch, starts the HTTP app, logs in, uploads
and retrieves an image through real private S3, checks unauthorized access,
restarts the app, submits inventory, and deletes another image through the API.
It removes its schema and test objects afterward. Inference is synthetic, but
PostgreSQL, S3, authentication and HTTP requests are real. Existing production
accounts and inventory are not touched.
