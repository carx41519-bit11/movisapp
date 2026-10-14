# Faster stock-in: 0.8.2

Deploy the backend from this branch using the existing Dockerfile.yolo and Neon
configuration. `/health` reports version `0.8.2`. Install the 0.8.2 Android APK.
No database migration or model replacement is required.

Updated Android clients request an action snapshot in the mutation response.
Starting, scanning, reviewing, deleting, and submitting normally take one HTTP
request each instead of a write followed by another GET. The snapshot is fetched
after the write commits. If that refresh fails, the successful receipt is retained
and the client falls back to fetching the task. Existing idempotency keys remain
unchanged, so retries cannot apply stock twice.

The client lists the immutable image IDs it already holds. The server still checks
authorization and returns current metadata, revisions, quantities and deletion
status, but omits known photo bytes. New photos are downloaded once. Images are
retained only with the current in-memory task, and are cleared on logout.

Old clients work without the new headers. The new APK falls back to the old flow
against an older backend. Deploy both components to obtain the request savings.

This reduces network work; it does not eliminate model inference, database writes,
network latency or service startup time. Validate a stock-in with a test account:
start, take a photo, review, submit, reopen and confirm stock increased exactly once.
