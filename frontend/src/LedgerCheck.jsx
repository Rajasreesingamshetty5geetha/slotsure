import { useCallback, useEffect, useState } from 'react';
import { getLedgerCheck } from './api.js';

const money = (v) => `₹${Number(v).toFixed(2)}`;

export default function LedgerCheck({ refreshKey }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setData(await getLedgerCheck());
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  const balanced = data && Number(data.difference) === 0;

  return (
    <footer className="ledger">
      <div className="ledger-head">
        <span>Ledger check</span>
        <button className="link" onClick={load}>Check</button>
      </div>
      {error && <div className="muted small">{error}</div>}
      {data && (
        <div className="ledger-row">
          <span>Debit {money(data.totalDebit)}</span>
          <span>Credit {money(data.totalCredit)}</span>
          <span className={balanced ? 'ok' : 'bad'}>Diff {money(data.difference)}</span>
        </div>
      )}
    </footer>
  );
}
