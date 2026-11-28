export const CUSTOMER_ID = 'demo-customer-1';
export const AMOUNT = 499.0;

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message || code || `HTTP ${status}`);
    this.status = status;
    this.code = code;
  }
}

async function request(method, url, body) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') headers['Idempotency-Key'] = crypto.randomUUID();

  let res;
  try {
    res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (e) {
    throw new ApiError(0, 'NETWORK', `Network error: ${e.message}`);
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!res.ok) {
    throw new ApiError(res.status, data?.code, data?.message || `HTTP ${res.status} on ${url}`);
  }
  return data;
}

export const getSlots = (lat, lng, date) =>
  request('GET', `/api/slots?lat=${lat}&lng=${lng}&date=${date}`);

export const createHold = (slotId) =>
  request('POST', '/api/holds', { slotId, customerId: CUSTOMER_ID });

export const createOrder = (holdId) =>
  request('POST', '/api/orders', { customerId: CUSTOMER_ID, holdId, amount: AMOUNT });

export const getOrder = (orderId) => request('GET', `/api/orders/${orderId}`);

export const getPayments = (orderId) => request('GET', `/api/payments/${orderId}`);

export const getLedgerCheck = () => request('GET', '/api/ledger/check');
