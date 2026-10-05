import test from 'node:test';
import assert from 'node:assert/strict';
import { validateRequest, referencePrefix, validateEvidence, activation, alreadyApplied, records, PERIOD_MS } from './payment-policy.mjs';
const reference = referencePrefix('alice', 'plan_premium') + 'a'.repeat(32);
const request = () => validateRequest('alice', { planId: 'plan_premium', receiptCode: 'TEST123456', paymentReference: reference });
const evidence = () => ({ provider_reference: 'TEST123456', status: 'SUCCESS', provider: 'M-PESA', currency: 'KES', external_reference: reference, transaction_type: 'PAYMENT', amount: 1350 });
test('exact incoming successful checkout evidence is required', () => {
  assert.equal(validateEvidence(evidence(), request()), 1350);
  for (const [field, value] of [['status','PENDING'], ['success',false], ['external_reference','someone-else'], ['provider_reference','OTHER12345'], ['provider','CARD'], ['currency','USD'], ['transaction_type','WITHDRAWAL'], ['transaction_type','REFUND'], ['transaction_type','REVERSAL'], ['amount',1349], ['amount',1350.5], ['amount',true]]) {
    assert.throws(() => validateEvidence({ ...evidence(), [field]: value }, request()), undefined, `${field}=${value}`);
  }
  assert.equal(validateEvidence({ ...evidence(), amount: 1400 }, request()), 1400);
});
test('untrusted plan, user reference, receipt and request types are rejected', () => {
  for (const data of [null, {}, { planId: '__proto__' }, { planId:'plan_premium',receiptCode: 'TEST123456',paymentReference: referencePrefix('bob','plan_premium')+'a'.repeat(32) }, { planId:'plan_premium',receiptCode: ['TEST123456'],paymentReference: reference }]) {
    assert.throws(() => validateRequest('alice', data));
  }
});
test('renewals retain paid same-plan days; grace never adds another allowance', () => {
  const now = 1700000000000;
  for (const status of ['ACTIVE', 'GRACE_PERIOD']) assert.equal(activation(request(), { planId: 'plan_premium', status, expiresAt: now+1000 }, now).expiresAt, now+1000+PERIOD_MS);
  assert.equal(activation(request(), { planId: 'plan_premium', status: 'GRACE_PERIOD', expiresAt: now-1000 }, now).expiresAt, now+PERIOD_MS);
  assert.equal(activation(request(), { planId: 'plan_basic', status: 'ACTIVE', expiresAt: now+1000 }, now).expiresAt, now+PERIOD_MS);
});
test('receipt replay cannot extend expiry or apply to another account/plan/reference', () => {
  const current = activation(request(), null, 1700000000000);
  const receipt = { usedByUserId:'alice',planId:'plan_premium',paymentReference:reference };
  assert.equal(alreadyApplied(receipt,current,request()),current);
  for(const mutated of [{...receipt,usedByUserId:'bob'},{...receipt,planId:'plan_basic'},{...receipt,paymentReference:'other'}]) assert.throws(()=>alreadyApplied(mutated,current,request()));
  assert.throws(()=>alreadyApplied(receipt,{...current,mpesaReceipt:'NEW1234567'},request()));
});
test('nested provider response never discards receipt evidence', () => {
  const item = {...evidence(),response:{description:'gateway message'}};
  assert.deepEqual(records({data:[item]}),[item]);
});
