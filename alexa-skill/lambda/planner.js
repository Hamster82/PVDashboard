const { qToTime, timeToQ } = require('./util');

/** PV-Leistung zur Viertelstunde q (linear zwischen den Stundenmitten). */
function pvAtQuarter(kw, q) {
  const x = q / 4 + 0.125;           // Mitte der Viertelstunde in Stunden
  const c = x - 0.5;                 // Index auf Stundenmitten
  const i = Math.floor(c);
  const f = c - i;
  const a = kw[Math.max(0, Math.min(23, i))];
  const b = kw[Math.max(0, Math.min(23, i + 1))];
  return Math.max(0, a + (b - a) * f);
}

/** Lastprofil [[Minuten, kW], …] -> mittlere Leistung je Viertelstunde */
function quarterProfile(profil) {
  const minutes = [];
  profil.forEach(([min, kw]) => { for (let i = 0; i < min; i++) minutes.push(kw); });
  const out = [];
  for (let i = 0; i < minutes.length; i += 15) {
    const part = minutes.slice(i, i + 15);
    out.push(part.reduce((a, b) => a + b, 0) / 15);
  }
  return out;
}

function energieKwh(g) { return g.profil.reduce((a, [min, kw]) => a + (min / 60) * kw, 0); }

/**
 * Plant alle Geräte für einen Tag.
 * kw: 24 Stundenwerte PV (kalibriert), fromQ: frühester Start (Viertelstunde)
 */
function planDay(kw, cfg, fromQ) {
  const grund = cfg.haushalt.grundlastKw;
  const surplus = [];
  for (let q = 0; q < 96; q++) surplus.push(Math.max(0, pvAtQuarter(kw, q) - grund));
  const startQ = Math.max(fromQ, timeToQ(cfg.haushalt.fruehesterStart));
  const lastQ = timeToQ(cfg.haushalt.spaetesterStart);
  let ueberschussKwh = 0;
  for (let q = startQ; q < 96; q++) ueberschussKwh += surplus[q] * 0.25;

  const result = {};
  for (const g of cfg.geraete) {
    const pw = quarterProfile(g.profil);
    const durQ = pw.length;
    const energie = energieKwh(g);
    const scores = [];
    for (let q = startQ; q <= Math.min(lastQ, 96 - durQ); q++) {
      let solar = 0; let margin = 0;
      for (let k = 0; k < durQ; k++) { solar += Math.min(surplus[q + k], pw[k]) * 0.25; margin += Math.min(surplus[q + k] - pw[k], 2); }
      scores.push({ q, solar, margin });
    }
    if (!scores.length) { result[g.id] = null; continue; }
    const max = Math.max(...scores.map((s) => s.solar));
    // unter den (fast) besten Startzeiten die mit dem meisten Puffer -> mittig im Sonnenfenster
    let best = null;
    scores.forEach((s, i) => {
      if (s.solar >= max * 0.98 - 1e-9 && (!best || s.margin > best.s.margin)) best = { s, i };
    });
    // Zeitfenster: zusammenhängende Startzeiten mit mindestens 90 % des Bestwerts
    let lo = best.i; let hi = best.i;
    while (lo > 0 && scores[lo - 1].solar >= max * 0.9) lo--;
    while (hi < scores.length - 1 && scores[hi + 1].solar >= max * 0.9) hi++;
    const coverage = energie > 0 ? best.s.solar / energie : 0;
    for (let k = 0; k < durQ; k++) {
      const q = best.s.q + k;
      surplus[q] = Math.max(0, surplus[q] - pw[k]);
    }
    result[g.id] = {
      start: qToTime(best.s.q),
      ende: qToTime(Math.min(95, best.s.q + durQ)),
      vonStart: qToTime(scores[lo].q),
      bisStart: qToTime(scores[hi].q),
      coverage,
      startQ: best.s.q,
      durQ,
    };
  }
  return { geraete: result, ueberschussKwh };
}

/** Entscheidet je Gerät: heute oder morgen. */
function recommend(todayKw, tomorrowKw, cfg, nowQ) {
  const heute = todayKw ? planDay(todayKw, cfg, Math.ceil(nowQ)) : null;
  const morgen = tomorrowKw ? planDay(tomorrowKw, cfg, 0) : null;
  const list = cfg.geraete.map((g) => {
    const h = heute && heute.geraete[g.id];
    const m = morgen && morgen.geraete[g.id];
    let tag = 'heute'; let plan = h;
    if (!h || (m && h.coverage < 0.6 && m.coverage > h.coverage + 0.15)) { tag = 'morgen'; plan = m; }
    return { geraet: g, tag, plan };
  });
  return { list, heute, morgen };
}

function bewertung(cov) {
  if (cov >= 0.9) return 'fast komplett mit Sonnenstrom';
  if (cov >= 0.6) return 'überwiegend mit Sonnenstrom';
  if (cov >= 0.3) return 'teilweise mit Sonnenstrom';
  return 'kaum mit Sonnenstrom';
}

module.exports = { planDay, recommend, bewertung, pvAtQuarter, energieKwh };
