import { createHash } from 'node:crypto';

export const PERIOD_MS = 30 * 86_400_000;
export const PLANS = Object.freeze({
  plan_mobile: { name: 'Mobile', price: 150 },
  plan_basic: { name: 'Basic', price: 550 },
  plan_standard: { name: 'Standard', price: 950 },
  plan_premium: { name: 'Premium', price: 1350 },
});

export class PaymentError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
const reject = message => { throw new PaymentError('failed-precondition', message); };
export const text = (item, ...names) => names.map(name => item?.[name])
  .find(value => typeof value === 'string' && value.trim() && value.trim() !== 'null')?.trim() ?? '';
export const receiptOf = item => text(item, 'provider_reference', 'MpesaReceiptNumber',
  'providerReference', 'mpesa_receipt', 'third_party_reference').toUpperCase();
export function amountOf(item) {
  const raw = item?.amount;
  if (typeof raw !== 'number' && typeof raw !== 'string') return null;
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}
export const referencePrefix = (uid, planId) =>
  `NF_${createHash('sha256').update(uid).digest('hex').slice(0, 16)}_${planId.replace('plan_', '')}_`;

export function validateRequest(uid, data) {
  if (typeof uid !== 'string' || !uid) throw new PaymentError('unauthenticated', 'Sign in before verifying payment.');
  const planId = typeof data?.planId === 'string' ? data.planId : '';
  const receipt = typeof data?.receiptCode === 'string' ? data.receiptCode.trim().toUpperCase() : '';
  const reference = typeof data?.paymentReference === 'string' ? data.paymentReference : '';
  if (!Object.hasOwn(PLANS, planId)) throw new PaymentError('invalid-argument', 'Select a valid membership plan.');
  if (!/^[A-Z0-9]{8,12}$/.test(receipt)) throw new PaymentError('invalid-argument', 'Enter the exact M-Pesa confirmation code.');
  const prefix = referencePrefix(uid, planId);
  if (!reference.startsWith(prefix) || !/^[a-f0-9]{32}$/.test(reference.slice(prefix.length))) {
    throw new PaymentError('invalid-argument', 'Open checkout from this account and plan.');
  }
  return { uid, planId, receipt, reference, plan: PLANS[planId] };
}

export function validateIncoming(item, { requireStatus = true } = {}) {
  const status = text(item, 'status', 'Status');
  if ((requireStatus || status) && status.toUpperCase() !== 'SUCCESS' ||
      item.success !== undefined && item.success !== true) reject('This payment is not completed. Retry the same code after confirmation.');
  const provider = text(item, 'provider', 'gateway').toLowerCase();
  if (provider && !['mpesa', 'm-pesa'].includes(provider)) reject('Only M-Pesa membership payments are accepted.');
  const currency = text(item, 'currency');
  if (currency && currency.toUpperCase() !== 'KES') reject('The payment must be in Kenyan shillings.');
  const direction = text(item, 'transaction_type', 'type').toLowerCase();
  if (['withdraw', 'charge', 'payout', 'refund', 'revers'].some(value => direction.includes(value))) reject('This is not an incoming membership payment.');
}

export function validateEvidence(item, request, confirmedAmount = amountOf(item)) {
  if (receiptOf(item) !== request.receipt) reject('No exact payment found for this M-Pesa code.');
  validateIncoming(item);
  if (text(item, 'payment_reference', 'external_reference', 'user_reference') !== request.reference) {
    reject('This payment does not match checkout for this account and plan.');
  }
  if (!Number.isSafeInteger(confirmedAmount) || confirmedAmount < request.plan.price || confirmedAmount > 2_147_483_647) {
    reject(`The confirmed amount is insufficient or invalid for this plan (KES ${request.plan.price}).`);
  }
  return confirmedAmount;
}

export function activation(request, previous, now) {
  if (!Number.isSafeInteger(now) || now <= 0) throw new PaymentError('internal', 'Invalid verification time.');
  const retainExpiry = previous?.planId === request.planId &&
    ['ACTIVE', 'GRACE_PERIOD'].includes(String(previous.status).toUpperCase()) &&
    Number.isSafeInteger(previous.expiresAt) && previous.expiresAt > now ? previous.expiresAt : now;
  const expiresAt = retainExpiry + PERIOD_MS;
  if (!Number.isSafeInteger(expiresAt)) throw new PaymentError('internal', 'Invalid membership expiry.');
  return { status: 'ACTIVE', planId: request.planId, planName: request.plan.name,
    amount: request.plan.price, currency: 'KES', paymentReference: request.reference,
    mpesaReceipt: request.receipt, subscribedAt: now, expiresAt };
}

export function alreadyApplied(receipt, current, request) {
  if (receipt.usedByUserId !== request.uid || receipt.planId !== request.planId ||
      receipt.paymentReference !== request.reference || current?.mpesaReceipt !== request.receipt ||
      current.planId !== request.planId || current.paymentReference !== request.reference) {
    reject('This code has already been redeemed. Each payment can activate only one account and plan.');
  }
  return current;
}

export function records(value) {
  if (Array.isArray(value)) return value.flatMap(records);
  if (!value || typeof value !== 'object') return [];
  // Receipt evidence can itself have a provider response object; keep the evidence together.
  if (receiptOf(value)) return [value];
  const nested = ['response', 'data', 'results', 'transactions'].map(key => value[key])
    .find(item => item && typeof item === 'object');
  return nested ? records(nested) : [value];
}
