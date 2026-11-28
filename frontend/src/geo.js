const NOMINATIM = 'https://nominatim.openstreetmap.org';

async function getJson(url) {
  const res = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`Address lookup failed (HTTP ${res.status})`);
  return res.json();
}

export async function searchAddress(query) {
  const url = `${NOMINATIM}/search?format=json&limit=5&countrycodes=in&q=${encodeURIComponent(query)}`;
  const results = await getJson(url);
  return results.map((r) => ({
    id: r.place_id,
    label: r.display_name,
    lat: Number(r.lat),
    lng: Number(r.lon),
  }));
}

export async function reverseGeocode(lat, lng) {
  const data = await getJson(`${NOMINATIM}/reverse?format=json&lat=${lat}&lon=${lng}`);
  return shortAddress(data);
}

function shortAddress(data) {
  const a = data?.address;
  if (a) {
    const street = [a.house_number, a.road].filter(Boolean).join(' ');
    const area = a.neighbourhood || a.suburb || a.quarter || a.village;
    const city = a.city || a.town || a.county || a.state_district;
    const parts = [street, area, city].filter(Boolean);
    if (parts.length) return [...new Set(parts)].join(', ');
  }
  return data?.display_name?.split(',').slice(0, 3).join(',').trim() || 'Unknown place';
}

export function currentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('unsupported'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      reject,
      { enableHighAccuracy: true, timeout: 10000 },
    );
  });
}
