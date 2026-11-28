import { useEffect, useState } from 'react';
import BookingCard from './BookingCard.jsx';
import LedgerCheck from './LedgerCheck.jsx';
import LocationCard from './LocationCard.jsx';
import { createHold } from './api.js';
import { localDate, hourLabel } from './format.js';

const DATE_OPTIONS = [
  { label: 'Today', offset: 0 },
  { label: 'Tomorrow', offset: 1 },
  { label: 'Day after', offset: 2 },
];

function DatePills({ date, onChange, disabled }) {
  return (
    <div className="pills">
      {DATE_OPTIONS.map((o) => {
        const value = localDate(o.offset);
        return (
          <button
            key={o.label}
            className={`pill${value === date ? ' active' : ''}`}
            onClick={() => onChange(value)}
            disabled={disabled}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

function SlotCard({ slot, onHold, disabled, held }) {
  const full = slot.remaining <= 0;
  const pct = slot.capacity ? (slot.remaining / slot.capacity) * 100 : 0;
  return (
    <div className={`card slot-card${held ? ' held' : ''}`}>
      <div className="slot-main">
        <div className="slot-time">
          {hourLabel(slot.startHour)} – {hourLabel(slot.endHour)}
        </div>
        <div className="bar">
          <div className="bar-fill" style={{ width: `${pct}%` }} />
        </div>
        <div className="slot-left">{full ? 'No slots left' : `${slot.remaining} left`}</div>
      </div>
      <button className="btn" onClick={() => onHold(slot)} disabled={full || disabled}>
        {full ? 'Full' : 'Hold slot'}
      </button>
    </div>
  );
}

export default function SlotPanel({
  location,
  address,
  onLocationChange,
  date,
  onDateChange,
  slots,
  slotsError,
  onRefresh,
}) {
  const [hold, setHold] = useState(null); // { holdId, slotId, expiresAt, slot }
  const [holding, setHolding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState(null);
  const [ledgerKey, setLedgerKey] = useState(0);

  useEffect(() => {
    setHold(null);
    setMessage(null);
  }, [location, date]);

  async function holdSlot(slot) {
    setHolding(true);
    setMessage(null);
    try {
      const h = await createHold(slot.slotId);
      setHold({ ...h, slot });
    } catch (e) {
      setMessage(e.code === 'SLOT_FULL' ? 'That slot just filled up. Pick another one.' : `Could not hold slot: ${e.message}`);
    } finally {
      setHolding(false);
      onRefresh();
    }
  }

  function bookingSettled() {
    onRefresh();
    setLedgerKey((k) => k + 1);
  }

  return (
    <aside className="sidebar">
      <header className="brand">
        <div className="wordmark">SlotSure</div>
        <div className="subtitle">Book a delivery slot that never overbooks</div>
      </header>

      <div className="location-wrap">
        <LocationCard location={location} address={address} onPick={onLocationChange} />
      </div>

      <DatePills date={date} onChange={onDateChange} disabled={busy} />

      <div className="sidebar-body">
        {!location && (
          <div className="empty">
            <div className="empty-icon">⌖</div>
            Choose your delivery location to see available slots
          </div>
        )}

        {location && (
          <>
            <div className="zone-head">
              <span>Available slots</span>
              <button className="link" onClick={onRefresh}>Refresh</button>
            </div>

            {message && <div className="alert">{message}</div>}
            {slotsError && <div className="alert">Could not load slots: {slotsError}</div>}

            {hold && (
              <BookingCard
                key={hold.holdId}
                hold={hold}
                date={date}
                onBusyChange={setBusy}
                onSettled={bookingSettled}
                onExpired={onRefresh}
                onClose={() => setHold(null)}
              />
            )}

            {!slots && <div className="muted">Loading slots…</div>}
            {slots && slots.length === 0 && !slotsError && (
              <div className="empty">We don't deliver here yet</div>
            )}
            <div className="slot-list">
              {slots?.map((s) => (
                <SlotCard
                  key={s.slotId}
                  slot={s}
                  onHold={holdSlot}
                  disabled={holding || busy}
                  held={hold?.slotId === s.slotId}
                />
              ))}
            </div>
          </>
        )}
      </div>

      <LedgerCheck refreshKey={ledgerKey} />
    </aside>
  );
}
