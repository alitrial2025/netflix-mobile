import test from 'node:test';
import assert from 'node:assert/strict';
import { lookupPayment } from './payhero-gateway.mjs';
import { validateRequest, referencePrefix } from './payment-policy.mjs';
const request=validateRequest('alice',{planId:'plan_basic',receiptCode:'TEST123456',paymentReference:referencePrefix('alice','plan_basic')+'a'.repeat(32)});
const payment=()=>({provider_reference:request.receipt,status:'SUCCESS',external_reference:request.reference,provider:'M-PESA',currency:'KES'});
const respond=value=>new Response(JSON.stringify(value),{status:200});
test('confirmed amount needs only the exact status lookup and keeps authorization on merchant host',async()=>{
  let calls=0;const result=await lookupPayment(request,'fixture-auth',{fetchImpl:async(url,options)=>{
    calls++;assert.equal(url.hostname,'backend.payhero.co.ke');assert.equal(url.searchParams.get('reference'),request.receipt);assert.equal(options.redirect,'manual');assert.equal(options.headers.Authorization,'fixture-auth');return respond({...payment(),amount:550});
  }});assert.equal(result.amount,550);assert.equal(calls,1);
});
test('amount fallback paginates exact receipt and rejects refunded ledger evidence',async()=>{
  const calls=[];const fetchImpl=async url=>{calls.push(url.pathname);if(url.pathname.endsWith('transaction-status'))return respond(payment());
    if(url.searchParams.get('page')==='1')return respond({data:[],pagination:{next_page:2}});
    return respond({data:[{...payment(),amount:550,transaction_type:'PAYMENT'}]});};
  assert.equal((await lookupPayment(request,'fixture-auth',{fetchImpl})).amount,550);assert.equal(calls.length,3);
  await assert.rejects(lookupPayment(request,'fixture-auth',{fetchImpl:async url=>respond(url.pathname.endsWith('transaction-status')?payment():{data:[{...payment(),amount:550,transaction_type:'REFUND'}]})}));
});
test('wrong checkout or pending receipt is rejected before ledger scans',async()=>{
  for(const changed of [{...payment(),external_reference:'other'},{...payment(),status:'PENDING'}]){
    let calls=0;await assert.rejects(lookupPayment(request,'fixture-auth',{fetchImpl:async()=>{calls++;return respond(changed)}}));assert.equal(calls,1);
  }
});
test('gateway failure, redirects, missing credentials and duplicate evidence never activate',async()=>{
  await assert.rejects(lookupPayment(request,''));
  for(const response of [new Response('',{status:429}),new Response('',{status:302}),respond({data:[{...payment(),amount:550},{...payment(),amount:550}]}),new Response('invalid',{status:200})]) {
    await assert.rejects(lookupPayment(request,'fixture-auth',{fetchImpl:async()=>response}));
  }
});
