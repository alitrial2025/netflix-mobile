# Trusted membership verification

The phone opens the existing Pay Sub checkout and sends its Firebase ID token, M-Pesa receipt, selected plan and persisted account/plan checkout reference to this Worker. The Worker checks Firebase signature, project, account status and token revocation, verifies exact PayHero evidence, then atomically consumes the receipt and updates the canonical membership and its two mirrors. The phone and TV read that canonical membership; neither app can grant it.

Deployed URL: `https://netflixpro-membership.netflixpro-cca67.workers.dev`
Firebase project: `netflixpro-cca67`

## Secrets and identity

Cloudflare secret bindings (never Kotlin, BuildConfig, git or plaintext Wrangler variables):

- `PAYHERO_API_AUTH`: complete merchant Authorization header, including `Basic `.
- `FIREBASE_SERVICE_ACCOUNT_JSON`: JSON credential for `netflixpro-payments@netflixpro-cca67.iam.gserviceaccount.com`.

The dedicated service account uses custom role `netflixproPaymentsWorker`: `datastore.databases.get`, `datastore.entities.get/create/update`, and `firebaseauth.users.get`. Its assigned IAM role grants no record-deletion or user-administration permissions. Its OAuth access token is cached and refreshed in the Worker. Firestore IAM permissions apply across the database, so keep this key restricted to server secrets.

The initial key was generated through authenticated Firebase/Google IAM APIs, stored outside both repositories with owner-only permissions, uploaded with Wrangler stdin, and checked against live Firestore reads and Firebase account lookup. The merchant authorization was also uploaded through stdin; the temporary local authorization file was removed after the connection checks. Wrangler's normal account credentials remain in its CLI configuration.

To replace either secret, enter it directly in Cloudflare Dashboard → Workers & Pages → netflixpro-membership → Settings → Variables and Secrets, or run:

```sh
npx wrangler secret put PAYHERO_API_AUTH
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON < /secure/path/new-service-account.json
```

Rotate a service-account key by generating a replacement for this account, uploading it, confirming authenticated billing availability and canonical reads, then deleting the previous key in Google IAM. Rotate merchant authorization in PayHero and update the Worker binding. Do not deploy merchant authorization inside an APK.

## Endpoints

| Endpoint | Result |
|---|---|
| `GET /v1/billing/status` | Requires a full signed-in Firebase account and configured secrets; the app enables checkout only after `enabled: true`. |
| `POST /v1/billing/verify` | JSON `receiptCode`, `planId`, `paymentReference`; returns the canonical subscription and server time after a committed verification. |

Errors are JSON with `error` and `message`, `Cache-Control: no-store`, and no merchant payloads or credentials. Invalid tokens and anonymous accounts are rejected. Uploads are bounded to 4 KiB while streaming. A crashed in-flight verification unlocks after 45 seconds. Retrying the same completed receipt/reference returns the existing result without another gateway call or another 30-day period. Concurrent accounts cannot redeem one receipt twice.

Prices are server-owned: Mobile 150, Basic 550, Standard 950, Premium 1,350 KES. Exact incoming successful M-Pesa evidence and an account/plan checkout reference are required. Amount lookup is bounded to ten ledger pages and a shared 30-second gateway deadline. An early same-plan renewal preserves unexpired paid time; switching plans starts a fresh 30 days. Receipt reuse, unknown plans, insufficient/fractional amounts, wrong currency, refunds, reversals and payouts are rejected.

## Build, deploy and reproduce checks

```sh
npm ci
npm test
npm run test:integration
npm run check
npm run deploy
```

Integration checks use demo projects and a local Firestore emulator; they cannot mutate the production project. The phone defaults to the deployed URL in `.env.example`. Override it in `.env`, the build environment, or the GitHub repository variable `PAYMENT_API_URL` when changing backends:

```text
PAYMENT_API_URL=https://netflixpro-membership.netflixpro-cca67.workers.dev
```

The release workflow accepts only this public backend URL and release-signing credentials; it no longer injects merchant authorization. The backend URL is public configuration. A blank/invalid URL or unreachable/unconfigured backend keeps checkout disabled before the user pays. Both repositories contain the same protected Firestore rules, now deployed and read back to confirm an exact match. For future rule changes, deploy only once from the phone repository with the authenticated Firebase CLI:

```sh
npx --prefix payment-server firebase deploy --project netflixpro-cca67 --only firestore:rules
```

Ship the updated phone app with this endpoint. Older APKs still use client-side activation and must be updated; current production rules already prohibit client-written memberships. Do not weaken those rules to restore legacy payment activation. TV uses the existing membership listener and requires no merchant configuration.

## Verified and still required

Passed: unit tests for payment policy, JWT cryptography, account access, bounded uploads, transaction races and idempotency; Firestore REST/emulator transactions and rules; live service-account reads and a read-only Firestore transaction; live PayHero merchant access and receipt lookup; deployed endpoint rejection of an unauthenticated request; both secret bindings present.

No charge, receipt redemption or paid-membership mutation was made during readiness checks. A controlled real checkout on the updated phone must still confirm the Lipwa reference round trip, signed-in billing availability, activation, same-code retry, renewal and TV refresh. These require a real account and payment; successful mock/emulator checks cannot certify the entire live purchase flow.

Cloudflare Workers Free has no inactivity sleep and currently permits 100,000 requests/day with a 10 ms CPU budget per request. Network wait does not count as CPU time. The initial deployed bundle reported 8 ms startup, which is not request CPU usage or checkout latency. Observe production CPU/error metrics before release; free quotas and external PayHero/Google availability do not provide a 100% uptime guarantee. Existing Firebase free quotas also apply.
