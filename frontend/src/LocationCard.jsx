import { useState } from 'react';
import { currentPosition, searchAddress } from './geo.js';

function AddressSearch({ onPick }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState(null);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    const q = query.trim();
    if (!q) return;
    setSearching(true);
    setError(null);
    try {
      setResults(await searchAddress(q));
    } catch (err) {
      setError(err.message);
      setResults(null);
    } finally {
      setSearching(false);
    }
  }

  function pick(r) {
    setResults(null);
    setQuery('');
    onPick({ lat: r.lat, lng: r.lng });
  }

  return (
    <>
      <form className="search" onSubmit={submit}>
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search your address"
        />
        <button className="btn" type="submit" disabled={searching || !query.trim()}>
          {searching ? '…' : 'Search'}
        </button>
      </form>
      {error && <div className="alert">{error}</div>}
      {results && results.length === 0 && <div className="muted small">No matching addresses.</div>}
      {results && results.length > 0 && (
        <ul className="results">
          {results.map((r) => (
            <li key={r.id}>
              <button onClick={() => pick(r)}>{r.label}</button>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

export default function LocationCard({ location, address, onPick }) {
  const [locating, setLocating] = useState(false);
  const [geoError, setGeoError] = useState(null);

  async function useMyLocation() {
    setLocating(true);
    setGeoError(null);
    try {
      onPick(await currentPosition());
    } catch {
      setGeoError('Location permission denied, search your address instead');
    } finally {
      setLocating(false);
    }
  }

  return (
    <div className="card location-card">
      <div className="card-title">Deliver to</div>

      {location && (
        <div className="chosen">
          <span className="pin">●</span>
          <div>
            <div className="chosen-address">{address ?? 'Finding address…'}</div>
            <div className="muted small">Drag the pin or click the map to adjust</div>
          </div>
        </div>
      )}

      <button className="btn btn-outline btn-block" onClick={useMyLocation} disabled={locating}>
        {locating ? 'Locating…' : 'Use my current location'}
      </button>
      {geoError && <div className="alert">{geoError}</div>}

      <AddressSearch onPick={onPick} />
    </div>
  );
}
