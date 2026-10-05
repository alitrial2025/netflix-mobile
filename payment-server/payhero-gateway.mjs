import { PaymentError, records, receiptOf, amountOf, validateEvidence, validateIncoming, text } from './payment-policy.mjs';

/** Merchant credentials stay in server secrets. All lookups share a bounded request deadline. */
export async function lookupPayment(request, authorization, { fetchImpl = fetch, signal = AbortSignal.timeout(30_000) } = {}) {
  if (!authorization) throw new PaymentError('unavailable', 'Payment verification is not configured. Contact support before paying.');
  const get = async (path, parameters) => {
    const url = new URL(`https://backend.payhero.co.ke/api/v2/${path}`);
    for (const [key, value] of Object.entries(parameters)) url.searchParams.set(key, String(value));
    let response;
    try {
      response = await fetchImpl(url, { headers: { Accept: 'application/json', Authorization: authorization }, redirect: 'manual', signal });
    } catch {
      throw new PaymentError('unavailable', 'Payment lookup timed out. Retry the same code; do not pay again.');
    }
    if (!response.ok) throw new PaymentError('unavailable', 'Payment gateway is unavailable. Retry the same code; do not pay again.');
    const body = await response.text();
    if (body.length > 1_048_576) throw new PaymentError('unavailable', 'Unexpected gateway response. Contact support with your receipt.');
    try { return JSON.parse(body); }
    catch { throw new PaymentError('unavailable', 'Unexpected gateway response. Retry shortly.'); }
  };
  const status = await get('transaction-status', { reference: request.receipt });
  const matches = records(status).filter(item => receiptOf(item) === request.receipt);
  if (matches.length !== 1) throw new PaymentError('failed-precondition', 'No unique exact payment found for this M-Pesa code.');
  const payment = matches[0];
  // Reject wrong accounts and pending payments before scanning the merchant ledger.
  validateIncoming(payment);
  if (text(payment, 'payment_reference', 'external_reference', 'user_reference') !== request.reference) {
    throw new PaymentError('failed-precondition', 'This payment does not match checkout for this account and plan.');
  }
  let amount = amountOf(payment);
  if (amount === null) {
    let page = 1;
    for (let count = 0; count < 10; count++) {
      const ledger = await get('transactions', { page, per: 100 });
      const rows = records(ledger).filter(item => receiptOf(item) === request.receipt && amountOf(item) !== null);
      if (rows.length > 1) throw new PaymentError('failed-precondition', 'Ambiguous receipt evidence. Contact support with your receipt.');
      if (rows.length === 1) {
        const row = rows[0];
        validateIncoming(row, { requireStatus: false });
        const reference = text(row, 'payment_reference', 'external_reference', 'user_reference');
        if (reference && reference !== request.reference) throw new PaymentError('failed-precondition', 'The merchant ledger does not match this checkout.');
        amount = amountOf(row);
        break;
      }
      const next = Number(ledger?.pagination?.next_page);
      if (!Number.isSafeInteger(next) || next <= page) break;
      page = next;
    }
  }
  return { amount: validateEvidence(payment, request, amount), gatewayReference: text(payment, 'reference') };
}
