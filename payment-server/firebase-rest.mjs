import { SignJWT, importPKCS8 } from 'jose';
import { PaymentError } from './payment-policy.mjs';

let cachedCredential;
let tokenPromise;
let cachedToken;
export function serviceCredential(env) {
  if (cachedCredential?.source === env.FIREBASE_SERVICE_ACCOUNT_JSON) return cachedCredential.value;
  let value;
  try { value = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON); } catch { throw new PaymentError('unavailable', 'Membership service is not configured.'); }
  if (value.project_id !== env.FIREBASE_PROJECT_ID || typeof value.client_email !== 'string' || typeof value.private_key !== 'string') {
    throw new PaymentError('unavailable', 'Membership service configuration is invalid.');
  }
  cachedCredential = { source: env.FIREBASE_SERVICE_ACCOUNT_JSON, value };
  cachedToken = undefined;
  return value;
}

async function accessToken(env, fetchImpl) {
  const credential = serviceCredential(env);
  if (cachedToken?.credential === credential && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value;
  if (!tokenPromise) tokenPromise = (async () => {
    const key = await importPKCS8(credential.private_key, 'RS256');
    const assertion = await new SignJWT({ scope: 'https://www.googleapis.com/auth/cloud-platform' })
      .setProtectedHeader({ alg: 'RS256' }).setIssuer(credential.client_email)
      .setAudience('https://oauth2.googleapis.com/token').setIssuedAt().setExpirationTime('1h').sign(key);
    const response = await fetchImpl('https://oauth2.googleapis.com/token', { method: 'POST', redirect: 'manual',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
      signal: AbortSignal.timeout(10_000) });
    if (!response.ok) {
      const error = new PaymentError('unavailable', 'Membership service authentication failed. Retry shortly.');
      error.upstreamStatus = response.status;
      throw error;
    }
    const data = await response.json();
    if (typeof data.access_token !== 'string' || !Number.isFinite(data.expires_in) || data.expires_in <= 0) throw new PaymentError('unavailable', 'Invalid service authentication response.');
    cachedToken = { credential, value: data.access_token, expiresAt: Date.now() + data.expires_in * 1000 };
    return cachedToken.value;
  })().finally(() => { tokenPromise = undefined; });
  return tokenPromise;
}

export function encodeFields(data) {
  const encode = value => {
    if (typeof value === 'string') return { stringValue: value };
    if (typeof value === 'boolean') return { booleanValue: value };
    if (Number.isSafeInteger(value)) return { integerValue: String(value) };
    if (value === null) return { nullValue: null };
    throw new PaymentError('internal', 'Unsupported membership field.');
  };
  return Object.fromEntries(Object.entries(data).map(([key, value]) => [key, encode(value)]));
}
export function decodeFields(fields) {
  return Object.fromEntries(Object.entries(fields ?? {}).map(([key, value]) => {
    if ('stringValue' in value) return [key, value.stringValue];
    if ('integerValue' in value) return [key, Number(value.integerValue)];
    if ('booleanValue' in value) return [key, value.booleanValue];
    if ('timestampValue' in value) return [key, Date.parse(value.timestampValue)];
    return [key, null];
  }));
}

export function firestoreStore(env, { fetchImpl = fetch, baseUrl = 'https://firestore.googleapis.com/v1', tokenProvider } = {}) {
  const project = env.FIREBASE_PROJECT_ID;
  if (!/^[a-z][a-z0-9-]{4,29}$/.test(project ?? '')) throw new PaymentError('unavailable', 'Invalid Firebase project.');
  const database = `projects/${project}/databases/(default)`;
  const endpoint = `${baseUrl}/${database}/documents`;
  const request = async (url, method = 'GET', body) => {
    const token = tokenProvider ? await tokenProvider() : await accessToken(env, fetchImpl);
    const response = await fetchImpl(url, { method, redirect: 'manual',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(10_000) });
    if (response.status === 404 && method === 'GET') return null;
    if (!response.ok) {
      const error = new PaymentError(response.status === 409 ? 'aborted' : 'unavailable', 'Membership could not be saved. Retry the same code; do not pay again.');
      error.upstreamStatus = response.status;
      throw error;
    }
    return response.json();
  };
  const get = async (path, transaction) => {
    if (transaction) {
      // batchGet carries the bytes transaction in JSON and records missing-document reads.
      const name = `${database}/documents/${path}`;
      const rows = await request(`${endpoint}:batchGet`, 'POST', { documents: [name], transaction });
      const row = (Array.isArray(rows) ? rows : [rows]).find(item => item.found?.name === name || item.missing === name);
      if (!row) throw new PaymentError('unavailable', 'Invalid membership read response. Retry shortly.');
      return row.found ? decodeFields(row.found.fields) : null;
    }
    const url = new URL(`${endpoint}/${path.split('/').map(encodeURIComponent).join('/')}`);
    const document = await request(url);
    return document ? decodeFields(document.fields) : null;
  };
  return {
    get,
    async transaction(callback) {
      for (let attempt = 0; attempt < 5; attempt++) {
        const started = await request(`${endpoint}:beginTransaction`, 'POST', { options: { readWrite: {} } });
        const transaction = started.transaction;
        const writes = [];
        let committed = false;
        try {
          const result = await callback({
            get: path => {
              if (writes.length) throw new PaymentError('internal', 'Transaction reads must precede writes.');
              return get(path, transaction);
            },
            set(path, data, options = {}) {
              const fields = { ...data };
              const serverTimes = ['updatedAt', 'verifiedAt'].filter(key => key in fields);
              serverTimes.forEach(key => { delete fields[key]; });
              writes.push({ update: { name: `${database}/documents/${path}`, fields: encodeFields(fields) },
                ...(options.merge ? { updateMask: { fieldPaths: Object.keys(fields) } } : {}),
                ...(serverTimes.length ? { updateTransforms: serverTimes.map(fieldPath => ({ fieldPath, setToServerValue: 'REQUEST_TIME' })) } : {}) });
            },
          });
          await request(`${endpoint}:commit`, 'POST', { transaction, writes });
          committed = true;
          return result;
        } catch (error) {
          if (error.code !== 'aborted' || attempt === 4) throw error;
        } finally {
          if (!committed) await request(`${endpoint}:rollback`, 'POST', { transaction }).catch(() => {});
        }
      }
    },
    async checkAccount(uid, authTime) {
      const account = await request(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:lookup`, 'POST', { localId: [uid] });
      const user = account.users?.find(item => item.localId === uid);
      if (!user || user.disabled || Number(user.validSince ?? 0) > authTime) throw new PaymentError('unauthenticated', 'Sign in again before verifying payment.');
    },
  };
}
