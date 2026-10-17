# MOVIS Singapore production

The production website remains `https://movisapp.onrender.com` and the Android
application ID remains `com.movis.warehouse`. Version 1.0.1 (101) carries the
tested 180-second request timeout for the first YOLO request after the free
server wakes. Logout retains its five-second timeout. This timeout prevents
premature failure; it does not make inference faster.

The original Render service runs in Singapore. Its private environment uses
Neon project `ancient-credit-99027857`, branch `production`, database
`movis_production` in `ap-southeast-1`, with the private `assets` object bucket.
The test server continues to use database `neondb`; do not point production at
that test database. Both databases share branch compute and the object bucket.

Set `DATABASE_URL` to the pooled **movis_production** connection. Configure
`AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`
and `NEON_BRANCH` from the Singapore branch. Preserve the original administrator
configuration, model path/checksum, YOLO mode and HTTPS setting. Keep secrets in
the hosting environment, never Git, JavaScript or the APK.

The migration copies the original Ohio database's public schema, all table
records and sequences. It preserves passwords, accounts, stock, history and
photo references. Active S3 photos are copied and verified by content hash;
already deleted image records remain deleted. The test account and test stock
are not imported into production.

Before cutover, pause writes on the Ohio database, take a `pg_dump` backup,
restore into the empty destination and compare every table's row count and
content fingerprint. Change Render's database/storage variables only after
verification. Retain the Ohio database and the private environment backup for
recovery. The Ohio copy must remain read-only after cutover to avoid split
inventory. If production has accepted new writes, a rollback requires copying
those changes back first; merely changing the URL would lose recent updates.

Use existing production accounts. Do not distribute the test credentials as
production credentials. Free Render/Neon wake-up delays still apply.
