import { useCallback, useEffect, useRef, useState } from 'react';
import MapPanel from './MapPanel.jsx';
import SlotPanel from './SlotPanel.jsx';
import { getSlots } from './api.js';
import { reverseGeocode } from './geo.js';
import { localDate } from './format.js';

export default function App() {
  const [location, setLocation] = useState(null); // { lat, lng }
  const [address, setAddress] = useState(null);
  const [date, setDate] = useState(localDate(0));
  const [slots, setSlots] = useState(null);
  const [slotsError, setSlotsError] = useState(null);
  const slotsRequest = useRef(0);

  const loadSlots = useCallback(async () => {
    if (!location) return;
    const request = ++slotsRequest.current;
    setSlotsError(null);
    try {
      const result = await getSlots(location.lat, location.lng, date);
      if (request === slotsRequest.current) setSlots(result ?? []);
    } catch (e) {
      if (request === slotsRequest.current) {
        setSlotsError(e.message);
        setSlots([]);
      }
    }
  }, [location, date]);

  useEffect(() => {
    setSlots(null);
    loadSlots();
  }, [loadSlots]);

  useEffect(() => {
    if (!location) return undefined;
    let current = true;
    setAddress(null);
    reverseGeocode(location.lat, location.lng)
      .then((a) => current && setAddress(a))
      .catch(() => current && setAddress(`${location.lat.toFixed(5)}, ${location.lng.toFixed(5)}`));
    return () => {
      current = false;
    };
  }, [location]);

  return (
    <div className="app">
      <SlotPanel
        location={location}
        address={address}
        onLocationChange={setLocation}
        date={date}
        onDateChange={setDate}
        slots={slots}
        slotsError={slotsError}
        onRefresh={loadSlots}
      />
      <MapPanel location={location} onPick={setLocation} />
    </div>
  );
}
