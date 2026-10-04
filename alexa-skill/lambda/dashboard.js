const cfg = require('./config');
const forecast = require('./forecast');
const plenticore = require('./plenticore');
const planner = require('./planner');
const U = require('./util');

const WT = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
const WT_KURZ = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
const MON = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
const MON_KURZ = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];
const C = { sun: '#f2b134', cons: '#5aa9e6', good: '#4cc38a', warn: '#e8875b', muted: '#98a2ad', text: '#eef1f4', night: '#3a424d' };

/** Geschätzter Tagesverbrauch (nur ohne Wechselrichterdaten): im Winter etwas mehr, im Sommer etwas weniger. */
function estConsumption(date) {
  const [y, m, d] = date.split('-').map(Number);
  const doy = (Date.UTC(y, m - 1, d) - Date.UTC(y, 0, 1)) / 86400000;
  return (cfg.haushalt.jahresverbrauchKwh / 365) * (1 + 0.15 * Math.cos((2 * Math.PI * (doy - 15)) / 365));
}

/** Kalibrierung: echter Ertrag / Modell-Ertrag der letzten 14 vollständigen Tage (Median). */
function calibration(store, model, today) {
  const ratios = [];
  for (let i = 1; i <= 21 && ratios.length < 14; i++) {
    const d = U.addDays(today, -i);
    const real = store.days && store.days[d] && store.days[d].y;
    const mod = model.days[d] && model.days[d].kwh;
    if (real && mod && mod > 2 && store.days[d].komplett) ratios.push(real / mod);
  }
  if (ratios.length < 3) return 1;
  ratios.sort((a, b) => a - b);
  return Math.max(0.6, Math.min(1.4, ratios[Math.floor(ratios.length / 2)]));
}

/** Holt alle Daten, aktualisiert den Speicher und baut Anzeige + Sprachtext. */
async function collect(store) {
  store = store || {};
  store.days = store.days || {};
  store.months = store.months || {};
  const now = U.berlinNow();
  const today = now.date;
  const nowQ = now.hour * 4 + now.minute / 15;

  const [fc, inv] = await Promise.allSettled([
    U.withTimeout(forecast.load(cfg), 5000, 'Wetterdienst antwortet nicht'),
    cfg.plenticore.aktiv ? U.withTimeout(plenticore.read(cfg.plenticore), 5500, 'Wechselrichter antwortet nicht') : Promise.resolve(null),
  ]);
  const live = inv.status === 'fulfilled' ? inv.value : null;
  const invFehler = cfg.plenticore.aktiv && inv.status === 'rejected' ? inv.reason.message : null;

  // Echte Werte speichern (Tageszähler steigen über den Tag, daher Maximum behalten)
  if (live) {
    const t = live.tag;
    if (t.y !== null) {
      const alt = store.days[today] || {};
      store.days[today] = { y: Math.max(t.y, alt.y || 0), h: Math.max(t.h || 0, alt.h || 0), pv: t.pv, bat: t.bat, komplett: now.hour >= 22 || !!alt.komplett };
    }
    if (live.monat.y !== null) store.months[today.slice(0, 7)] = { y: live.monat.y, h: live.monat.h, bis: +today.slice(8, 10) + (now.hour >= 22 ? 0 : -1) };
  }
  // Alte Tage aufräumen
  const grenze = U.addDays(today, -120);
  Object.keys(store.days).forEach((d) => { if (d < grenze) delete store.days[d]; });

  if (fc.status !== 'fulfilled') {
    return { store, fehler: fc.reason.message, view: fehlerView(fc.reason.message, now), speech: 'Die Wetterprognose ist gerade nicht erreichbar. Versuch es bitte gleich noch einmal.' };
  }
  const model = fc.value;
  const k = calibration(store, model, today);
  const kwTag = (d) => (model.days[d] ? model.days[d].kw.map((x) => x * k) : null);
  const tomorrow = U.addDays(today, 1);
  const heuteKw = kwTag(today);
  const morgenKw = kwTag(tomorrow);
  const heuteKwh = model.days[today] ? model.days[today].kwh * k : null;
  const morgenKwh = model.days[tomorrow] ? model.days[tomorrow].kwh * k : null;

  const plan = planner.recommend(heuteKw, morgenKw, cfg, nowQ);

  // ---------- Seite "Heute" ----------
  const pvJetztProg = heuteKw ? planner.pvAtQuarter(heuteKw, Math.floor(nowQ)) : 0;
  const liveRows = [];
  let ueberschussJetzt;
  if (live && live.live.pvKw !== null) {
    liveRows.push({ label: 'Sonne jetzt', value: `${U.fmt(live.live.pvKw)} kW` });
    if (live.live.hausKw !== null) liveRows.push({ label: 'Haus jetzt', value: `${U.fmt(live.live.hausKw)} kW` });
    if (live.live.akkuSoc !== null) liveRows.push({ label: 'Akku', value: `${Math.round(live.live.akkuSoc)} %` });
    if (live.tag.y !== null) liveRows.push({ label: 'Ertrag bisher', value: `${U.fmt(live.tag.y)} kWh` });
    ueberschussJetzt = live.live.pvKw - (live.live.hausKw ?? cfg.haushalt.grundlastKw);
  } else {
    liveRows.push({ label: 'Sonne jetzt (Prognose)', value: `${U.fmt(pvJetztProg)} kW` });
    liveRows.push({ label: 'Überschuss heute noch', value: `${U.fmt(plan.heute ? plan.heute.ueberschussKwh : 0)} kWh` });
    ueberschussJetzt = pvJetztProg - cfg.haushalt.grundlastKw;
  }

  let jetzt;
  if (ueberschussJetzt >= 1.5) jetzt = { text: `Jetzt ${U.fmt(ueberschussJetzt)} kW Überschuss – guter Moment zum Einschalten`, farbe: C.good };
  else if (ueberschussJetzt >= 0.5) jetzt = { text: `Jetzt ${U.fmt(ueberschussJetzt)} kW Überschuss – reicht für ein Gerät`, farbe: C.sun };
  else jetzt = { text: 'Jetzt kaum Überschuss – lieber warten', farbe: C.warn };

  const geraete = plan.list.map(({ geraet, tag, plan: p }) => {
    if (!p) return { name: geraet.name, wann: 'kein Zeitfenster', info: '', farbe: geraet.farbe };
    const zeit = p.vonStart === p.bisStart ? `ab ${p.start}` : `${p.vonStart}–${p.bisStart}`;
    return {
      name: geraet.name,
      wann: `${tag === 'morgen' ? 'Morgen' : 'Heute'} ${zeit}`,
      info: `≈ ${Math.round(p.coverage * 100)} % Sonne`,
      farbe: p.coverage >= 0.6 ? geraet.farbe : C.muted,
    };
  });

  // Akku-Hinweis
  let hinweis = '';
  const geraeteKwh = cfg.geraete.reduce((a, g) => a + planner.energieKwh(g), 0);
  const restUeberschuss = plan.heute ? plan.heute.ueberschussKwh : 0;
  const soc = live && live.live.akkuSoc;
  if (soc !== null && soc !== undefined) {
    const bedarf = (1 - soc / 100) * cfg.akku.kapazitaetKwh;
    if (restUeberschuss >= bedarf + geraeteKwh) hinweis = `Akku wird heute voll und es bleibt genug Sonne für alle Geräte.`;
    else if (restUeberschuss >= bedarf) hinweis = `Akku wird voll, für Geräte bleiben ca. ${U.fmt(restUeberschuss - bedarf)} kWh – das wichtigste Gerät zuerst.`;
    else hinweis = `Akku wird heute nicht ganz voll – Geräte nur in die markierten Sonnenstunden legen.`;
  } else {
    hinweis = `Startzeit innerhalb des Zeitfensters wählen. Restlicher Überschuss heute ca. ${U.fmt(restUeberschuss)} kWh.`;
  }

  // Stundenbalken 06–20 Uhr
  const empfohlen = new Set();
  plan.list.forEach(({ tag, plan: p }) => {
    if (tag === 'heute' && p && p.coverage >= 0.3) for (let q = p.startQ; q < p.startQ + p.durQ; q++) empfohlen.add(Math.floor(q / 4));
  });
  const maxKw = Math.max(1, ...(heuteKw || [0]).slice(6, 21));
  const stunden = [];
  for (let h = 6; h <= 20; h++) {
    const v = heuteKw ? heuteKw[h] : 0;
    stunden.push({
      h: Math.max(2, Math.round((v / maxKw) * 205)),
      c: empfohlen.has(h) ? C.good : (v > cfg.haushalt.grundlastKw ? C.sun : C.night),
      o: h < now.hour ? 0.35 : 1,
      l: h % 2 === 0 ? String(h) : '',
    });
  }

  // ---------- Seite "Woche" ----------
  const wocheRows = [];
  for (let i = 6; i >= 0; i--) {
    const d = U.addDays(today, -i);
    const echt = store.days[d];
    let y; let h; let est = false;
    const modellY = model.days[d] ? model.days[d].kwh * k : null;
    if (echt && echt.y !== undefined && (echt.komplett || d === today)) { y = echt.y; h = echt.h; } else if (echt && echt.y !== undefined) {
      // Tag wurde nur tagsüber erfasst -> fehlende Stunden aus Modell/Schätzung ergänzen
      est = true; y = Math.max(echt.y, modellY || 0); h = Math.max(echt.h || 0, estConsumption(d));
    } else {
      est = true;
      const md = model.days[d];
      if (d === today) {
        y = md ? md.kw.slice(0, now.hour).reduce((a, b) => a + b, 0) * k + (md.kw[now.hour] * k * now.minute) / 60 : null;
        h = estConsumption(d) * (now.hour + now.minute / 60) / 24;
      } else {
        y = md ? md.kwh * k : null;
        h = estConsumption(d);
      }
    }
    wocheRows.push({ label: d === today ? 'Heute' : WT_KURZ[U.weekday(d)], y, h, est, pv: echt && echt.pv, bat: echt && echt.bat });
  }
  const woche = chartBlock('Letzte 7 Tage', wocheRows, 270);

  // ---------- Seite "Monat" ----------
  const monatRows = [];
  const thisYm = today.slice(0, 7);
  for (let i = 5; i >= 0; i--) {
    const ym = U.addMonths(thisYm, -i);
    const echt = store.months[ym];
    const label = MON_KURZ[+ym.slice(5, 7) - 1] + (ym === thisYm ? ' bisher' : '');
    const tageGesamt = ym === thisYm ? +today.slice(8, 10) : U.daysInMonth(ym);
    if (echt && (ym === thisYm || (echt.bis || 0) >= tageGesamt)) { monatRows.push({ label, y: echt.y, h: echt.h, est: false }); continue; }
    if (echt) {
      // Monat nicht bis zum letzten Tag erfasst -> Resttage schätzen
      let y = echt.y; let h = echt.h; let fehlt = false;
      for (let t = (echt.bis || 0) + 1; t <= tageGesamt; t++) {
        const d = `${ym}-${U.pad(t)}`;
        if (model.days[d] && d >= model.firstDate) y += model.days[d].kwh * k; else fehlt = true;
        h += estConsumption(d);
      }
      monatRows.push({ label, y: fehlt ? echt.y : y, h, est: true });
      continue;
    }
    let y = 0; let abgedeckt = 0;
    for (let t = 1; t <= tageGesamt; t++) {
      const d = `${ym}-${U.pad(t)}`;
      if (d >= model.firstDate && model.days[d]) { y += (d === today ? (wocheRows[6].y || 0) : model.days[d].kwh * k); abgedeckt++; }
    }
    const h = Array.from({ length: tageGesamt }, (_, t) => estConsumption(`${ym}-${U.pad(t + 1)}`)).reduce((a, b) => a + b, 0);
    monatRows.push({ label, y: abgedeckt / tageGesamt >= 0.8 ? (y * tageGesamt) / abgedeckt : null, h, est: true });
  }
  const monat = chartBlock('Letzte 6 Monate', monatRows, 270);

  const datum = `${WT[U.weekday(today)]}, ${+today.slice(8, 10)}. ${MON[+today.slice(5, 7) - 1]}`;
  const view = {
    datum,
    heuteKwh: U.fmt(heuteKwh),
    heuteWetter: `${forecast.wetterText(model.days[today] && model.days[today].cloud)} · ${cfg.standort.name}`,
    morgenKwh: U.fmt(morgenKwh),
    morgenWetter: forecast.wetterText(model.days[tomorrow] && model.days[tomorrow].cloud),
    live: liveRows,
    jetzt,
    geraete,
    hinweis,
    stunden,
    woche,
    monat,
    quelle: live ? 'Live-Daten: Kostal Plenticore' : (invFehler ? `Wechselrichter nicht erreichbar (${invFehler}) – Werte geschätzt` : 'Wettermodell Open-Meteo · Verbrauch geschätzt'),
    stand: `Stand ${U.pad(now.hour)}:${U.pad(now.minute)}`,
  };

  // ---------- Sprache ----------
  const teile = [`Heute erwarte ich etwa ${Math.round(heuteKwh || 0)} Kilowattstunden Solarstrom`];
  if (morgenKwh !== null) teile[0] += `, morgen etwa ${Math.round(morgenKwh)}`;
  teile[0] += '.';
  plan.list.slice(0, 2).forEach(({ geraet, tag, plan: p }) => { if (p) teile.push(speechFor(geraet, tag, p)); });
  const status = {
    ueberschussKw: ueberschussJetzt,
    live: !!(live && live.live.pvKw !== null),
    soc: live ? live.live.akkuSoc : null,
    heuteKwh,
    morgenKwh,
  };
  return { store, view, speech: teile.join(' '), plan, status };
}

/** Kurze Statusansage: Überschuss, Akku, Prognose und Startzeiten aller Geräte */
function kurzStatus(r) {
  const st = r.status;
  const satz = [];
  const u = st.ueberschussKw;
  if (st.live) satz.push(u >= 0.1 ? `Überschuss ${U.fmt(u)} Kilowatt` : 'Aktuell kein Überschuss');
  else satz.push(u >= 0.1 ? `Überschuss laut Prognose ${U.fmt(u)} Kilowatt` : 'Laut Prognose aktuell kein Überschuss');
  if (st.soc !== null && st.soc !== undefined) satz[0] += `, Akku ${Math.round(st.soc)} Prozent`;
  satz[0] += '.';
  let prog = `Prognose heute ${Math.round(st.heuteKwh || 0)}`;
  if (st.morgenKwh !== null && st.morgenKwh !== undefined) prog += `, morgen ${Math.round(st.morgenKwh)}`;
  satz.push(prog + ' Kilowattstunden.');
  r.plan.list.forEach(({ geraet, tag, plan: p }) => {
    if (!p || p.coverage < 0.3) { satz.push(`${geraet.name} lohnt sich heute und morgen kaum.`); return; }
    const tagWort = tag === 'morgen' ? ' morgen' : '';
    if (p.vonStart === p.bisStart) satz.push(`${geraet.name}${tagWort} ab ${U.sayTime(p.start)}.`);
    else satz.push(`${geraet.name}${tagWort} ${U.sayTime(p.vonStart)} bis ${U.sayTime(p.bisStart)}, am besten ${U.sayTime(p.start)}.`);
  });
  return satz.join(' ');
}

function speechFor(geraet, tag, p) {
  const maennlich = geraet.id === 'trockner';
  const art = maennlich ? 'Den' : 'Die';
  const pron = maennlich ? 'er' : 'sie';
  const zeit = p.vonStart === p.bisStart ? `ab ${U.sayTime(p.start)}` : `zwischen ${U.sayTime(p.vonStart)} und ${U.sayTime(p.bisStart)}`;
  if (p.coverage < 0.3) return `${maennlich ? 'Der' : 'Die'} ${geraet.name} läuft ${tag} ${planner.bewertung(p.coverage)}. Am ehesten startest du ${pron === 'er' ? 'ihn' : 'sie'} ${zeit}.`;
  return `${art} ${geraet.name} startest du ${tag} am besten ${zeit}, dann läuft ${pron} ${planner.bewertung(p.coverage)}.`;
}

/** Balkendiagramm Ertrag vs. Verbrauch + Summenkacheln */
function chartBlock(titel, rows, maxH) {
  const max = Math.max(1, ...rows.map((r) => Math.max(r.y || 0, r.h || 0)));
  const sumY = rows.reduce((a, r) => a + (r.y || 0), 0);
  const sumH = rows.reduce((a, r) => a + (r.h || 0), 0);
  const geschaetzt = rows.some((r) => r.est);
  const echteTage = rows.filter((r) => r.pv !== undefined && r.pv !== null && r.h);
  let autarkie = null;
  if (echteTage.length) {
    const h = echteTage.reduce((a, r) => a + r.h, 0);
    autarkie = h ? echteTage.reduce((a, r) => a + (r.pv || 0) + (r.bat || 0), 0) / h : null;
  }
  const bilanz = sumY - sumH;
  const summen = [
    { label: 'Ertrag', value: `${U.fmt(sumY, 0)} kWh`, farbe: C.sun },
    { label: 'Verbrauch', value: `${U.fmt(sumH, 0)} kWh`, farbe: C.cons },
    { label: bilanz >= 0 ? 'Überschuss' : 'Fehlmenge', value: `${U.fmt(Math.abs(bilanz), 0)} kWh`, farbe: bilanz >= 0 ? C.good : C.warn },
    autarkie !== null
      ? { label: 'Autarkie', value: `${Math.round(autarkie * 100)} %`, farbe: C.text }
      : { label: 'Deckung rechnerisch', value: sumH ? `${Math.round(Math.min(sumY / sumH, 9.99) * 100)} %` : '–', farbe: C.text },
  ];
  const balken = rows.map((r) => ({
    label: r.label,
    yh: r.y === null ? 0 : Math.max(2, Math.round(((r.y || 0) / max) * maxH)),
    ch: Math.max(2, Math.round(((r.h || 0) / max) * maxH)),
    yo: r.est ? 0.5 : 1,
    co: r.est ? 0.5 : 1,
    yv: r.y === null ? 'k. D.' : U.fmt(r.y, r.y >= 100 ? 0 : 1),
  }));
  return {
    titel,
    summen,
    balken,
    fussnote: geschaetzt ? 'Blasse Balken sind geschätzt (Wettermodell bzw. Jahresverbrauch). Mit Wechselrichter-Anbindung werden sie durch echte Werte ersetzt.' : 'Echte Werte vom Wechselrichter.',
  };
}

function fehlerView(msg, now) {
  const leer = { titel: '', summen: [], balken: [], fussnote: '' };
  return {
    datum: 'Solar-Planer', heuteKwh: '–', heuteWetter: msg, morgenKwh: '–', morgenWetter: '',
    live: [], jetzt: { text: 'Daten werden beim nächsten Aufruf neu geladen', farbe: C.warn }, geraete: [], hinweis: '', stunden: [],
    woche: leer, monat: leer, quelle: msg, stand: `Stand ${U.pad(now.hour)}:${U.pad(now.minute)}`,
  };
}

module.exports = { collect, speechFor, chartBlock, kurzStatus };
