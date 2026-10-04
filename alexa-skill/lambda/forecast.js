const { request, shiftHour } = require('./util');

/**
 * Holt die Einstrahlung auf die geneigten Modulflächen (Open-Meteo, kostenlos, ohne Schlüssel)
 * für die letzten 92 Tage + heute + 2 Tage und rechnet sie in PV-Leistung um.
 * Ergebnis: { days: { 'JJJJ-MM-TT': { kw: [24 Stundenwerte], kwh, cloud } }, firstDate }
 * kw[h] = mittlere Leistung von h:00 bis h+1:00 (ohne Kalibrierung).
 */
async function load(cfg) {
  const planes = cfg.anlage.flaechen;
  const resps = await Promise.all(planes.map((f, i) => request({
    host: 'api.open-meteo.com',
    path: '/v1/forecast?' + new URLSearchParams({
      latitude: cfg.standort.lat,
      longitude: cfg.standort.lon,
      hourly: i === 0 ? 'global_tilted_irradiance,temperature_2m,cloud_cover' : 'global_tilted_irradiance',
      tilt: f.neigung,
      azimuth: f.azimut,
      timezone: 'Europe/Berlin',
      past_days: 92,
      forecast_days: 3,
    }),
    timeoutMs: 4500,
  })));
  return buildModel(cfg, resps);
}

function buildModel(cfg, resps) {
  const { flaechen, performanceRatio: pr, tempKoeffizient: tk, wechselrichterMaxKw: acMax } = cfg.anlage;
  const h0 = resps[0].hourly;
  const days = {};
  for (let i = 0; i < h0.time.length; i++) {
    let kw = 0;
    let anyValue = false;
    flaechen.forEach((f, j) => {
      const g = resps[j].hourly.global_tilted_irradiance[i];
      if (g === null || g === undefined) return;
      anyValue = true;
      const tCell = (h0.temperature_2m[i] ?? 15) + (g / 800) * 25; // vereinfachtes NOCT-Modell
      kw += f.kwp * (g / 1000) * pr * (1 + tk * (tCell - 25));
    });
    if (!anyValue) continue;
    kw = Math.max(0, Math.min(kw, acMax));
    // Open-Meteo liefert Mittelwerte der vorangegangenen Stunde -> Startstunde = Zeitstempel - 1 h
    const start = shiftHour(h0.time[i], -1);
    const d = start.slice(0, 10);
    const h = +start.slice(11, 13);
    if (!days[d]) days[d] = { kw: Array(24).fill(0), cloudSum: 0, cloudN: 0 };
    days[d].kw[h] = kw;
    const c = h0.cloud_cover ? h0.cloud_cover[i] : null;
    if (h >= 8 && h <= 17 && c !== null && c !== undefined) { days[d].cloudSum += c; days[d].cloudN += 1; }
  }
  const out = {};
  for (const [d, v] of Object.entries(days)) {
    out[d] = { kw: v.kw, kwh: v.kw.reduce((a, b) => a + b, 0), cloud: v.cloudN ? v.cloudSum / v.cloudN : null };
  }
  const firstDate = h0.time[0].slice(0, 10); // ab hier ist jeder Tag vollständig
  return { days: out, firstDate };
}

function wetterText(cloud) {
  if (cloud === null || cloud === undefined) return '';
  if (cloud < 25) return 'sonnig';
  if (cloud < 55) return 'heiter bis wolkig';
  if (cloud < 85) return 'wechselhaft';
  return 'bedeckt';
}

module.exports = { load, buildModel, wetterText };
