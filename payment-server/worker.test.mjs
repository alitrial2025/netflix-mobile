import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPair, SignJWT, exportJWK, createLocalJWKSet } from 'jose';
import { createHandler, verifyFirebaseToken } from './worker.mjs';
const fullClaims={sub:'alice',auth_time:1700000000,firebase:{sign_in_provider:'password'}};
const handler=(claims=fullClaims)=>createHandler({verifyToken:async()=>claims,makeStore:()=>({checkAccount:async()=>{}})});
const env={PAYHERO_API_AUTH:'fixture-auth'};
const request=(path='/v1/billing/status',options={})=>new Request('https://fixture.example'+path,{headers:{Authorization:'Bearer fixture'},...options});
test('missing identity, anonymous identity and revoked identity cannot verify or pay',async()=>{
 assert.equal((await handler()(request('/v1/billing/status',{headers:{}}),env)).status,401);
 assert.equal((await handler({...fullClaims,firebase:{sign_in_provider:'anonymous'}})(request(),env)).status,401);
 const revoked=createHandler({verifyToken:async()=>fullClaims,makeStore:()=>({checkAccount:async()=>{throw Error('revoked')}})});
 assert.notEqual((await revoked(request(),env)).status,200);
});
test('availability is authenticated and requires payment configuration',async()=>{
 assert.equal((await handler()(request(),env)).status,200);
 assert.equal((await handler()(request(),{})).status,503);
 const response=await handler()(request(),env);assert.equal(response.headers.get('Cache-Control'),'no-store');
 assert.deepEqual(await response.json(),{enabled:true,version:1});
});
test('wrong methods, malformed or oversized inputs cannot reach gateway',async()=>{
 assert.equal((await handler()(request('/v1/billing/status',{method:'POST'}),env)).status,405);
 for (const body of ['{',JSON.stringify({receiptCode:'TEST123456',planId:'invalid',paymentReference:'invalid'}),'x'.repeat(5000)]) {
  assert.equal((await handler()(request('/v1/billing/verify',{method:'POST',body}),env)).status,400);
 }
});
test('Firebase JWT signature, issuer, audience, algorithm and expiry are verified',async()=>{
 const {privateKey,publicKey}=await generateKeyPair('RS256');
 const jwk=await exportJWK(publicKey);const keys=createLocalJWKSet({keys:[{...jwk,kid:'fixture',alg:'RS256'}]});
 const issue=({project='fixture-project',audience=project,expiry='1h'}={})=>new SignJWT(fullClaims).setProtectedHeader({alg:'RS256',kid:'fixture'})
  .setIssuer('https://securetoken.google.com/'+project).setAudience(audience).setIssuedAt().setExpirationTime(expiry).sign(privateKey);
 assert.equal((await verifyFirebaseToken(await issue(),'fixture-project',keys)).sub,'alice');
 for(const options of [{project:'other-project'},{audience:'other-project'},{expiry:1}]) await assert.rejects(verifyFirebaseToken(await issue(options),'fixture-project',keys));
 const altered=(await issue()).split('.');altered[1]=Buffer.from(JSON.stringify({...fullClaims,sub:'bob'})).toString('base64url');
 await assert.rejects(verifyFirebaseToken(altered.join('.'),'fixture-project',keys));
});
test('chunked oversized bodies are cancelled before the entire upload is buffered',async()=>{
 let cancelled=false;
 const stream=new ReadableStream({pull(controller){controller.enqueue(new Uint8Array(4097));},cancel(){cancelled=true;}});
 const response=await handler()(request('/v1/billing/verify',{method:'POST',body:stream,duplex:'half'}),env);
 assert.equal(response.status,400);
 assert.equal(cancelled,true);
});
