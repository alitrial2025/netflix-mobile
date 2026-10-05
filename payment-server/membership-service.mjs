import { randomUUID } from 'node:crypto';
import { PaymentError, alreadyApplied, activation } from './payment-policy.mjs';

/** Store exposes get(path), transaction(callback); transaction reads precede every write. */
export async function verifyMembership(request, { store, lookup, now = Date.now }) {
  const receiptPath = `used_receipts/${request.receipt}`;
  const currentPath = `users/${request.uid}/subscription/current`;
  const attemptPath = `billing_attempts/${request.uid}`;
  const result = (subscription, amountPaid, already = false) => ({ subscription, amountPaid, serverTimeMs: now(),
    message: already ? 'This payment is already applied to your account.' : `Payment verified. Your ${request.plan.name} membership is active for 30 days.` });
  const existing = await store.get(receiptPath);
  if (existing) return result(alreadyApplied(existing, await store.get(currentPath), request), existing.amount, true);

  // One in-flight gateway verification per account. A crashed request expires promptly.
  const token = randomUUID();
  await store.transaction(async tx => {
    const attempt = await tx.get(attemptPath);
    if (attempt?.until > now()) throw new PaymentError('resource-exhausted', 'Verification is already running. Wait briefly and retry the same code.');
    tx.set(attemptPath, { token, until: now() + 45_000 });
  });
  try {
    // Recheck after joining the account lock so a completed retry uses no gateway calls.
    const redeemed = await store.get(receiptPath);
    if (redeemed) return result(alreadyApplied(redeemed, await store.get(currentPath), request), redeemed.amount, true);
    const payment = await lookup(request);
    const verifiedAt = now();
    const committed = await store.transaction(async tx => {
      const receipt = await tx.get(receiptPath);
      const current = await tx.get(currentPath);
      const attempt = await tx.get(attemptPath);
      if (receipt) return { sub: alreadyApplied(receipt, current, request), already: true, amount: receipt.amount };
      if (attempt?.token !== token || attempt.until <= now()) throw new PaymentError('aborted', 'Verification expired. Retry the same code; do not pay again.');
      const sub = activation(request, current, verifiedAt);
      tx.set(receiptPath, { receipt: request.receipt, usedByUserId: request.uid, planId: request.planId,
        amount: payment.amount, currency: 'KES', paymentReference: request.reference,
        gatewayReference: payment.gatewayReference, expiresAt: sub.expiresAt, verifiedAt });
      tx.set(currentPath, { ...sub, updatedAt: verifiedAt });
      tx.set(`subscriptions/${request.uid}`, { ...sub, userId: request.uid, updatedAt: verifiedAt });
      tx.set(`users/${request.uid}`, { subscriptionPlanId: request.planId, subscriptionStatus: 'ACTIVE', updatedAt: verifiedAt }, { merge: true });
      return { sub, amount: payment.amount, already: false };
    });
    return result(committed.sub, committed.amount, committed.already);
  } finally {
    // Only the original operation may release its lock. Do not mask an activation result.
    await store.transaction(async tx => {
      const attempt = await tx.get(attemptPath);
      if (attempt?.token === token) tx.set(attemptPath, { token, until: 0 });
    }).catch(() => {});
  }
}
