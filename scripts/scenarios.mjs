// usage: node scenarios.mjs prepaid|cash|online|fail|idem|invalid|ledger|all
// Needs all four services running. Books as demo customer user1 in Koramangala, tomorrow.
// "fail" needs payment-service started with
//   --slotsure.gateway.a.failure-rate=1.0 --slotsure.gateway.b.failure-rate=1.0
const S = 'http://localhost:8081', O = 'http://localhost:8082', P = 'http://localhost:8083', D = 'http://localhost:8084';
const customerId = '6f1c2a10-3b7d-4e2a-9c11-0a1b2c3d4e01';
const LAT = 12.9352, LNG = 77.6245;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const d = new Date(); d.setDate(d.getDate() + 1);
const tomorrow = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const run = Date.now();
const SETTLED = ['CONFIRMED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED'];

async function call(method, url, body, key) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
  if (key) headers['Idempotency-Key'] = key;
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json; try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  const shown = typeof json === 'string' ? json : JSON.stringify(json);
  console.log(`${method} ${url.replace(/http:\/\/localhost:/, ':')} -> ${res.status} ${shown && shown.length > 300 ? shown.slice(0, 300) + '…' : shown ?? ''}`);
  return { status: res.status, body: json };
}

const slots = () => call('GET', `${S}/api/slots?lat=${LAT}&lng=${LNG}&date=${tomorrow}`);
const order = id => fetch(`${O}/api/orders/${id}`).then(r => r.json());

/** The first purchase of the mode still waiting for a slot; order-service tops the demo user up on restart. */
async function purchase(mode) {
  const list = (await call('GET', `${O}/api/purchases?customerId=${customerId}`)).body;
  const found = list.find(p => p.paymentMode === mode && p.status === 'AWAITING_SLOT');
  if (!found) throw new Error(`user1 has no ${mode} purchase awaiting a slot; restart order-service to top it up`);
  console.log(`purchase ${found.purchaseId} ${found.itemName} ${found.paymentMode} ${found.amount}`);
  return found;
}

/** Holds the first free evening slot and books it with the given payment choice, then waits for the saga. */
async function book(label, mode, choice) {
  const p = await purchase(mode);
  const slot = (await slots()).body.find(s => s.startHour >= 18 && s.remaining > 0);
  console.log(`slot ${slot.slotId} ${slot.areaName} ${slot.startHour}-${slot.endHour} remaining=${slot.remaining} priceTag=${slot.priceTag}`);
  const hold = await call('POST', `${S}/api/holds`, { slotId: slot.slotId, customerId }, `${label}-hold-${run}`);
  const created = await call('POST', `${O}/api/orders`, {
    customerId, purchaseId: p.purchaseId, holdId: hold.body.holdId, paymentChoice: choice,
    lat: LAT, lng: LNG, address: '42, 5th Cross, Koramangala, Bengaluru', areaName: slot.areaName,
    slotDate: tomorrow, startHour: slot.startHour, endHour: slot.endHour,
  }, `${label}-order-${run}`);
  const orderId = created.body.orderId;
  let o = await order(orderId);
  // Wait for the saga to settle and, for a booked order, for dispatch to assign an agent.
  for (let i = 0; i < 30 && !(o.status === 'OUT_FOR_DELIVERY' || ['FAILED', 'CANCELLED'].includes(o.status)); i++) {
    await sleep(1000);
    o = await order(orderId);
  }
  await call('GET', `${O}/api/orders/${orderId}`);
  await call('GET', `${S}/api/holds/${hold.body.holdId}`);
  await call('GET', `${P}/api/payments/${orderId}`);
  const after = (await slots()).body.find(x => x.slotId === slot.slotId);
  console.log(`slot remaining before=${slot.remaining} after=${after.remaining}`);
  return { orderId, order: o, purchaseId: p.purchaseId };
}

/** Marks the order delivered through dispatch-service and waits for order-service and the ledger. */
async function deliver(orderId) {
  const first = await call('POST', `${D}/api/deliveries/${orderId}/complete`, null, `complete-${orderId}`);
  const again = await call('POST', `${D}/api/deliveries/${orderId}/complete`, null, `complete-${orderId}-again`);
  let o = await order(orderId);
  for (let i = 0; i < 15 && o.status !== 'DELIVERED'; i++) {
    await sleep(1000);
    o = await order(orderId);
  }
  await sleep(1000);
  const ledger = (await call('GET', `${P}/api/ledger/${orderId}`)).body;
  const legs = ledger.map(e => `${e.account.startsWith('CUSTOMER:') ? 'CUSTOMER' : e.account} ${Number(e.debit) > 0 ? 'Dr ' + e.debit : 'Cr ' + e.credit}`);
  return { o, sameResult: first.body?.deliveredAt === again.body?.deliveredAt, legs };
}

async function notifications(orderId) {
  const list = (await call('GET', `${O}/api/notifications?customerId=${customerId}`)).body;
  return list.filter(n => n.orderId === orderId).map(n => n.type).reverse().join(' → ');
}

async function scenario(mode) {
  if (mode === 'prepaid' || mode === 'cash' || mode === 'online') {
    const [paymentMode, choice] = { prepaid: ['PREPAID', 'PREPAID'], cash: ['COD', 'CASH'], online: ['COD', 'ONLINE'] }[mode];
    const booked = await book(mode, paymentMode, choice);
    if (!SETTLED.includes(booked.order.status) || ['FAILED', 'CANCELLED'].includes(booked.order.status)) {
      console.log(`RESULT ${mode}: status=${booked.order.status} sagaStep=${booked.order.sagaStep} (not booked)`);
      return;
    }
    const done = await deliver(booked.orderId);
    await call('GET', `${P}/api/ledger/check`);
    console.log(`RESULT ${mode}: area=${booked.order.areaName} agent=${booked.order.agentName} -> ${done.o.status}, `
      + `repeat complete same=${done.sameResult}, ledger [${done.legs.join(' | ')}], notifications ${await notifications(booked.orderId)}`);
  } else if (mode === 'fail') {
    const booked = await book('fail', 'COD', 'ONLINE');
    const p = (await call('GET', `${O}/api/purchases?customerId=${customerId}`)).body
      .find(x => x.purchaseId === booked.purchaseId && x.status === 'AWAITING_SLOT' && x.orderId === null);
    await call('GET', `${P}/api/ledger/check`);
    console.log(`RESULT fail: status=${booked.order.status} sagaStep=${booked.order.sagaStep}, purchase free again=${!!p}, `
      + `notifications ${await notifications(booked.orderId)}`);
  } else if (mode === 'idem') {
    const slot = (await slots()).body.find(s => s.remaining > 0);
    const key = `idem-${run}`;
    const a = await call('POST', `${S}/api/holds`, { slotId: slot.slotId, customerId }, key);
    const b = await call('POST', `${S}/api/holds`, { slotId: slot.slotId, customerId }, key);
    const after = (await slots()).body.find(x => x.slotId === slot.slotId);
    console.log(`RESULT idem: sameHoldId=${a.body.holdId === b.body.holdId} remaining ${slot.remaining} -> ${after.remaining}`);
  } else if (mode === 'invalid') {
    const p = await purchase('PREPAID');
    const r = await call('POST', `${O}/api/orders`, {
      customerId, purchaseId: p.purchaseId, holdId: crypto.randomUUID(), paymentChoice: 'ONLINE',
      slotDate: tomorrow, startHour: 18, endHour: 19,
    }, `invalid-${run}`);
    console.log(`RESULT invalid: ${r.status} ${r.body?.code}`);
  } else if (mode === 'ledger') {
    const r = await call('GET', `${P}/api/ledger/check`);
    console.log(`RESULT ledger: difference=${r.body.difference}`);
  } else {
    console.log('usage: node scenarios.mjs prepaid|cash|online|fail|idem|invalid|ledger|all');
    process.exitCode = 1;
  }
}

const mode = process.argv[2];
if (mode === 'all') {
  for (const m of ['invalid', 'prepaid', 'cash', 'online', 'idem', 'ledger']) {
    console.log(`\n=== ${m}`);
    await scenario(m);
  }
} else {
  await scenario(mode);
}
