import { useEffect, useRef, useState } from 'react';
import { createOrder, getOrder, AMOUNT } from './api.js';
import { hourLabel } from './format.js';

const FINAL_STATUSES = ['CONFIRMED', 'FAILED', 'CANCELLED'];
const POLL_EVERY_MS = 1000;
const POLL_FOR_MS = 30000;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function formatCountdown(ms) {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function Timeline({ phase, status }) {
  const failed = status === 'FAILED' || status === 'CANCELLED';
  const confirmed = status === 'CONFIRMED';

  const steps = [
    { label: 'HELD', state: 'done' },
    {
      label: 'PAYMENT',
      state: confirmed || failed ? 'done' : phase === 'paying' ? 'active' : phase === 'timeout' ? 'failed' : 'pending',
    },
    failed
      ? { label: 'FAILED', note: 'slot released', state: 'failed' }
      : { label: 'CONFIRMED', state: confirmed ? 'success' : 'pending' },
  ];

  return (
    <ol className="timeline">
      {steps.map((s) => (
        <li key={s.label} className={`step ${s.state}`}>
          <span className="dot" />
          <span className="step-label">{s.label}</span>
          {s.note && <span className="step-note">{s.note}</span>}
        </li>
      ))}
    </ol>
  );
}

export default function BookingCard({ hold, date, onBusyChange, onSettled, onExpired, onClose }) {
  const [now, setNow] = useState(Date.now());
  const [phase, setPhase] = useState('held'); // held | paying | done | timeout
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      onBusyChange(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (phase !== 'held') return undefined;
    const t = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(t);
  }, [phase]);

  const remainingMs = new Date(hold.expiresAt).getTime() - now;
  const expired = phase === 'held' && remainingMs <= 0;

  useEffect(() => {
    if (expired) onExpired();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expired]);

  async function pay() {
    setPhase('paying');
    setError(null);
    onBusyChange(true);
    try {
      const created = await createOrder(hold.holdId);
      if (!alive.current) return;
      setOrder(created);

      const started = Date.now();
      while (alive.current && Date.now() - started < POLL_FOR_MS) {
        await sleep(POLL_EVERY_MS);
        if (!alive.current) return;
        try {
          const latest = await getOrder(created.orderId);
          if (!alive.current) return;
          setOrder(latest);
          if (FINAL_STATUSES.includes(latest.status)) {
            setPhase('done');
            return;
          }
        } catch {
          // keep polling until the time limit
        }
      }
      if (alive.current) setPhase('timeout');
    } catch (e) {
      if (alive.current) {
        setError(`Payment could not start: ${e.message}`);
        setPhase('held');
      }
    } finally {
      if (alive.current) {
        onBusyChange(false);
        onSettled();
      }
    }
  }

  const status = order?.status;
  const finished = phase === 'done' || phase === 'timeout';

  return (
    <div className="card booking">
      <div className="booking-head">
        <div>
          <div className="booking-title">
            {hourLabel(hold.slot.startHour)} – {hourLabel(hold.slot.endHour)}
          </div>
          <div className="muted small">{date}</div>
        </div>
        {phase === 'held' && (
          <div className={`countdown${remainingMs < 60000 ? ' urgent' : ''}`}>
            {expired ? 'Expired' : formatCountdown(remainingMs)}
          </div>
        )}
      </div>

      {phase === 'held' && !expired && (
        <button className="btn btn-block" onClick={pay}>
          Pay ₹{AMOUNT}
        </button>
      )}
      {expired && <div className="alert">Your hold expired and the slot was released. Pick a slot again.</div>}
      {error && <div className="alert">{error}</div>}

      {phase !== 'held' && <Timeline phase={phase} status={status} />}

      {phase === 'timeout' && <div className="alert">Still processing after 30 s. Refresh later to check.</div>}

      {(finished || expired) && (
        <button className="link" onClick={onClose}>
          Done
        </button>
      )}
    </div>
  );
}
