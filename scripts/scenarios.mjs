// usage: node scenarios.mjs happy|idem|fail|ledger
const S = 'http://localhost:8081', O = 'http://localhost:8082', P = 'http://localhost:8083';
const customerId = '7f3c2a10-4b5e-4c8d-9a1f-2e6b8d0c4a11';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const d = new Date(); d.setDate(d.getDate() + 1);
const tomorrow = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

async function call(method, url, body, key) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
  if (key) headers['Idempotency-Key'] = key;
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json; try { json = JSON.parse(text); } catch { json = text; }
  console.log(`${method} ${url.replace(/http:\/\/localhost:/, ':')} -> ${res.status} ${typeof json === 'string' ? json : JSON.stringify(json)}`);
  return { status: res.status, body: json };
}

const slots = () => call('GET', `${S}/api/slots?lat=12.9716&lng=77.5946&date=${tomorrow}`);
const pick = list => list.find(s => s.remaining > 0);

async function waitOrder(orderId, finals) {
  for (let i = 0; i < 30; i++) {
    const r = await fetch(`${O}/api/orders/${orderId}`).then(r => r.json());
    if (finals.includes(r.status) && r.sagaStep === 'DONE') break;
    await sleep(1000);
  }
  return call('GET', `${O}/api/orders/${orderId}`);
}

async function placeOrder(label) {
  const s = await slots();
  const slot = pick(s.body);
  console.log(`slot ${slot.slotId} ${slot.startHour}-${slot.endHour} remaining=${slot.remaining} priceTag=${slot.priceTag}`);
  const run = Date.now();
  const hold = await call('POST', `${S}/api/holds`, { slotId: slot.slotId, customerId }, `${label}-hold-${run}`);
  const order = await call('POST', `${O}/api/orders`, { customerId, holdId: hold.body.holdId, amount: 499.00 }, `${label}-order-${run}`);
  const final = await waitOrder(order.body.orderId, ['CONFIRMED', 'FAILED', 'CANCELLED']);
  await call('GET', `${S}/api/holds/${hold.body.holdId}`);
  await call('GET', `${P}/api/payments/${order.body.orderId}`);
  await call('GET', `${P}/api/ledger/${order.body.orderId}`);
  const after = (await slots()).body.find(x => x.slotId === slot.slotId);
  console.log(`slot remaining before=${slot.remaining} after=${after.remaining}`);
  return final;
}

const mode = process.argv[2];
if (mode === 'happy') {
  const f = await placeOrder('happy');
  await call('GET', `${P}/api/ledger/check`);
  console.log(`RESULT happy: status=${f.body.status} sagaStep=${f.body.sagaStep}`);
} else if (mode === 'idem') {
  const slot = pick((await slots()).body);
  const key = `idem-${Date.now()}`;
  const a = await call('POST', `${S}/api/holds`, { slotId: slot.slotId, customerId }, key);
  const b = await call('POST', `${S}/api/holds`, { slotId: slot.slotId, customerId }, key);
  const after = (await slots()).body.find(x => x.slotId === slot.slotId);
  console.log(`RESULT idem: sameHoldId=${a.body.holdId === b.body.holdId} remaining ${slot.remaining} -> ${after.remaining}`);
} else if (mode === 'fail') {
  const f = await placeOrder('fail');
  await call('GET', `${P}/api/ledger/check`);
  console.log(`RESULT fail: status=${f.body.status} sagaStep=${f.body.sagaStep}`);
} else if (mode === 'ledger') {
  await call('GET', `${P}/api/ledger/check`);
}
