import { createRemoteJWKSet, jwtVerify } from 'jose';
import { PaymentError, validateRequest } from './payment-policy.mjs';
import { firestoreStore, serviceCredential } from './firebase-rest.mjs';
import { lookupPayment } from './payhero-gateway.mjs';
import { verifyMembership } from './membership-service.mjs';

const firebaseKeys = createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'));
const statusCodes = { unauthenticated: 401, 'invalid-argument': 400, 'failed-precondition': 409, 'resource-exhausted': 429, aborted: 409, unavailable: 503, internal: 500 };
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: {
  'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
} });

async function requestBody(request) {
  const reader = request.body?.getReader();
  if (!reader) return '';
  const decoder = new TextDecoder();
  let bytes = 0;
  let body = '';
  let completed = false;
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) { completed = true; return body + decoder.decode(); }
      bytes += chunk.value.byteLength;
      if (bytes > 4096) throw new PaymentError('invalid-argument', 'Payment request is too large.');
      body += decoder.decode(chunk.value, { stream: true });
    }
  } finally {
    if (!completed) await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

export function createHandler({ verifyToken, makeStore = firestoreStore, lookup = lookupPayment } = {}) {
  return async (request, env) => {
    const path = new URL(request.url).pathname;
    if (!['/v1/billing/status', '/v1/billing/verify'].includes(path)) return json({ error: 'not-found' }, 404);
    if (request.method !== (path.endsWith('/status') ? 'GET' : 'POST')) return json({ error: 'method-not-allowed' }, 405);
    try {
      const authorization = request.headers.get('Authorization') ?? '';
      if (!authorization.startsWith('Bearer ') || authorization.length > 8192) throw new PaymentError('unauthenticated', 'Sign in before verifying payment.');
      let claims;
      try {
        claims = verifyToken ? await verifyToken(authorization.slice(7), env) :
          await verifyFirebaseToken(authorization.slice(7), env.FIREBASE_PROJECT_ID);
      } catch { throw new PaymentError('unauthenticated', 'Sign in again before verifying payment.'); }
      if (typeof claims.sub !== 'string' || !/^[A-Za-z0-9:_-]{1,128}$/.test(claims.sub) ||
          !Number.isInteger(claims.auth_time) || claims.firebase?.sign_in_provider === 'anonymous') {
        throw new PaymentError('unauthenticated', 'Sign in to a full account before paying.');
      }
      if (!env.PAYHERO_API_AUTH) throw new PaymentError('unavailable', 'Payment verification is not configured. Contact support before paying.');
      if (!makeStore || makeStore === firestoreStore) serviceCredential(env);
      const store = makeStore(env);
      await store.checkAccount(claims.sub, claims.auth_time);
      if (path.endsWith('/status')) return json({ enabled: true, version: 1 });
      if (Number(request.headers.get('Content-Length') ?? 0) > 4096) throw new PaymentError('invalid-argument', 'Payment request is too large.');
      const body = await requestBody(request);
      let data;
      try { data = JSON.parse(body); } catch { throw new PaymentError('invalid-argument', 'Invalid payment request.'); }
      const payment = validateRequest(claims.sub, data);
      const result = await verifyMembership(payment, { store, lookup: input => lookup(input, env.PAYHERO_API_AUTH) });
      return json(result);
    } catch (error) {
      // Never log tokens, merchant responses, receipt codes or service-account material.
      const known = error instanceof PaymentError;
      return json({ error: known ? error.code : 'unavailable', message: known ? error.message :
        'Membership verification is unavailable. Retry the same code; do not pay again.' }, known ? statusCodes[error.code] ?? 503 : 503);
    }
  };
}

export async function verifyFirebaseToken(token, project, keys = firebaseKeys) {
  return (await jwtVerify(token, keys, { issuer: `https://securetoken.google.com/${project}`,
    audience: project, algorithms: ['RS256'] })).payload;
}

export default { fetch: createHandler() };
