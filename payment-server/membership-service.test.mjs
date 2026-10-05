import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyMembership } from './membership-service.mjs';
import { validateRequest, referencePrefix, PERIOD_MS } from './payment-policy.mjs';
const request = (uid='alice',receipt='TEST123456') => validateRequest(uid,{planId:'plan_premium',receiptCode:receipt,paymentReference:referencePrefix(uid,'plan_premium')+'a'.repeat(32)});
function memoryStore(seed={}) {
  const documents=new Map(Object.entries(seed)); let queue=Promise.resolve();
  return {documents,get:async path=>structuredClone(documents.get(path)), transaction(callback) {
    const operation=queue.then(async()=>{
      const writes=[];
      const result=await callback({get:async path=>structuredClone(documents.get(path)),set:(path,data,options)=>writes.push([path,data,options])});
      for(const [path,data,options] of writes) documents.set(path,options?.merge?{...documents.get(path),...data}:data);
      return result;
    });queue=operation.catch(()=>{});return operation;
  }};
}
const now=()=>1700000000000;
test('activation atomically writes all mirrors; retry does not call gateway or add days',async()=>{
  const store=memoryStore({'users/alice':{email:'fixture@example.invalid'}});let calls=0;
  const lookup=async()=>{calls++;return {amount:1350,gatewayReference:'gateway-fixture'}};
  const first=await verifyMembership(request(),{store,lookup,now});
  const retry=await verifyMembership(request(),{store,lookup,now});
  assert.equal(calls,1);assert.equal(first.subscription.expiresAt,now()+PERIOD_MS);
  assert.equal(retry.subscription.expiresAt,first.subscription.expiresAt);
  assert.equal(store.documents.get('subscriptions/alice').expiresAt,first.subscription.expiresAt);
  assert.equal(store.documents.get('users/alice').email,'fixture@example.invalid');
  assert.equal(store.documents.get('used_receipts/TEST123456').usedByUserId,'alice');
});
test('racing same-account verification issues one gateway request',async()=>{
  const store=memoryStore();let release;let notify;let calls=0;
  const ready=new Promise(resolve=>notify=resolve);const held=new Promise(resolve=>release=resolve);
  const lookup=async()=>{calls++;notify();await held;return {amount:1350,gatewayReference:'fixture'}};
  const first=verifyMembership(request(),{store,lookup,now});await ready;
  await assert.rejects(verifyMembership(request(),{store,lookup,now}),{code:'resource-exhausted'});
  release();await first;assert.equal(calls,1);
});
test('gateway failure consumes no receipt or membership and retry can succeed',async()=>{
  const store=memoryStore();
  await assert.rejects(verifyMembership(request(),{store,lookup:async()=>{throw Error('offline')},now}));
  assert.equal(store.documents.has('used_receipts/TEST123456'),false);
  assert.equal(store.documents.has('users/alice/subscription/current'),false);
  await verifyMembership(request(),{store,lookup:async()=>({amount:1350,gatewayReference:'fixture'}),now});
});
test('cross-account concurrent receipt redemption commits exactly once',async()=>{
  const store=memoryStore();let release;const held=new Promise(resolve=>release=resolve);let entered=0;let notify;const ready=new Promise(resolve=>notify=resolve);
  const lookup=async()=>{if(++entered===2)notify();await held;return {amount:1350,gatewayReference:'fixture'}};
  const alice=verifyMembership(request(),{store,lookup,now});const bob=verifyMembership(request('bob'),{store,lookup,now});
  await ready;release();const results=await Promise.allSettled([alice,bob]);
  assert.equal(results.filter(item=>item.status==='fulfilled').length,1);
  assert.equal(results.filter(item=>item.status==='rejected').length,1);
});
test('same-plan renewal keeps prepaid days and an expired verification cannot write',async()=>{
  const store=memoryStore({'users/alice/subscription/current':{planId:'plan_premium',status:'ACTIVE',expiresAt:now()+1000}});
  const result=await verifyMembership(request(),{store,lookup:async()=>({amount:1350,gatewayReference:'fixture'}),now});
  assert.equal(result.subscription.expiresAt,now()+1000+PERIOD_MS);
  let clock=now();const expired=memoryStore();
  await assert.rejects(verifyMembership(request(),{store:expired,lookup:async()=>{clock+=46000;return {amount:1350,gatewayReference:'fixture'}},now:()=>clock}),{code:'aborted'});
  assert.equal(expired.documents.has('used_receipts/TEST123456'),false);
});
