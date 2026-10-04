const https = require('https');

/** HTTPS-Anfrage mit JSON-Antwort und hartem Zeitlimit. */
function request({ host, port = 443, path, method = 'GET', headers = {}, body, timeoutMs = 4000, insecure = false, fingerprint = '' }) {
  return new Promise((resolve, reject) => {
    const data = body === undefined ? undefined : JSON.stringify(body);
    const opts = { host, port, path, method, headers: { Accept: 'application/json', ...headers } };
    if (data !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.headers['Content-Length'] = Buffer.byteLength(data);
    }
    if (insecure) {
      opts.rejectUnauthorized = false; // Wechselrichter nutzt ein selbstsigniertes Zertifikat
      opts.agent = false;              // eigene Verbindung je Anfrage -> Zertifikat wird jedes Mal geprüft
    }

    let done = false;
    const finish = (fn, v) => { if (!done) { done = true; clearTimeout(timer); fn(v); } };
    const req = https.request(opts, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const text = Buffer.concat(chunks).toString('utf8');
        if (res.statusCode < 200 || res.statusCode >= 300) {
          return finish(reject, new Error(`HTTP ${res.statusCode} bei ${path.split('?')[0]}`));
        }
        try { finish(resolve, text ? JSON.parse(text) : null); } catch (e) { finish(reject, new Error('Ungültige Antwort von ' + host)); }
      });
    });
    if (insecure && fingerprint) {
      req.once('socket', (s) => s.once('secureConnect', () => {
        const fp = (s.getPeerCertificate().fingerprint256 || '').replace(/:/g, '').toLowerCase();
        if (fp !== fingerprint.replace(/:/g, '').toLowerCase()) req.destroy(new Error('Zertifikat des Wechselrichters passt nicht'));
      }));
    }
    const timer = setTimeout(() => req.destroy(new Error('Zeitüberschreitung bei ' + host)), timeoutMs);
    req.on('error', (e) => finish(reject, e));
    if (data !== undefined) req.write(data);
    req.end();
  });
}

function withTimeout(promise, ms, msg) {
  let t;
  return Promise.race([promise, new Promise((_, rej) => { t = setTimeout(() => rej(new Error(msg)), ms); })])
    .finally(() => clearTimeout(t));
}

/** Aktuelle Uhrzeit in Deutschland. */
function berlinNow(d = new Date()) {
  const p = {};
  new Intl.DateTimeFormat('de-DE', { timeZone: 'Europe/Berlin', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .formatToParts(d).forEach((x) => { p[x.type] = x.value; });
  return { date: `${p.year}-${p.month}-${p.day}`, hour: +p.hour, minute: +p.minute };
}

const pad = (n) => String(n).padStart(2, '0');
function toUtc(key) { const [y, m, d] = key.split('-').map(Number); return Date.UTC(y, m - 1, d); }
function fromUtc(ms) { const x = new Date(ms); return `${x.getUTCFullYear()}-${pad(x.getUTCMonth() + 1)}-${pad(x.getUTCDate())}`; }
function addDays(key, n) { return fromUtc(toUtc(key) + n * 86400000); }
function weekday(key) { return new Date(toUtc(key)).getUTCDay(); }
function daysInMonth(ym) { const [y, m] = ym.split('-').map(Number); return new Date(Date.UTC(y, m, 0)).getUTCDate(); }
function addMonths(ym, n) { const [y, m] = ym.split('-').map(Number); const x = new Date(Date.UTC(y, m - 1 + n, 1)); return `${x.getUTCFullYear()}-${pad(x.getUTCMonth() + 1)}`; }

/** "2026-10-02T10:00" eine Stunde verschieben. */
function shiftHour(t, n) {
  const ms = Date.UTC(+t.slice(0, 4), +t.slice(5, 7) - 1, +t.slice(8, 10), +t.slice(11, 13), +t.slice(14, 16)) + n * 3600000;
  const x = new Date(ms);
  return `${fromUtc(ms)}T${pad(x.getUTCHours())}:${pad(x.getUTCMinutes())}`;
}

function fmt(x, digits = 1) {
  if (x === null || x === undefined || Number.isNaN(x)) return '–';
  return x.toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

/** Viertelstunde (0..95) -> "10:15" */
function qToTime(q) { return `${pad(Math.floor(q / 4))}:${pad((q % 4) * 15)}`; }
function timeToQ(s) { const [h, m] = s.split(':').map(Number); return h * 4 + Math.floor(m / 15); }
/** "10:15" -> "10 Uhr 15" für die Sprachausgabe */
function sayTime(s) { const [h, m] = s.split(':').map(Number); return m ? `${h} Uhr ${m}` : `${h} Uhr`; }

module.exports = { request, withTimeout, berlinNow, addDays, weekday, daysInMonth, addMonths, shiftHour, fmt, qToTime, timeToQ, sayTime, pad };
