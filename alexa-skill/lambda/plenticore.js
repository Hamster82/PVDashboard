const crypto = require('crypto');
const { request } = require('./util');

/**
 * Liest Live- und Statistikwerte über die REST-Schnittstelle des Kostal Plenticore
 * (Anmeldung als "Anlagenbetreiber", SCRAM-SHA256 + AES-GCM-Session, wie in der Web-Oberfläche).
 * Es wird ausschließlich gelesen – der Skill schreibt nichts in den Wechselrichter.
 */
async function read(cfg) {
  const base = { host: cfg.host, port: cfg.port, insecure: true, fingerprint: cfg.zertifikatFingerprint, timeoutMs: 3000 };
  const api = (path, method = 'GET', body, headers) => request({ ...base, path: '/api/v1' + path, method, body, headers });
  const hmac = (key, data) => crypto.createHmac('sha256', key).update(data).digest();

  // --- Anmeldung ---
  const clientNonce = crypto.randomBytes(12).toString('base64');
  const start = await api('/auth/start', 'POST', { username: 'user', nonce: clientNonce });
  const salted = crypto.pbkdf2Sync(cfg.passwort, Buffer.from(start.salt, 'base64'), start.rounds, 32, 'sha256');
  const clientKey = hmac(salted, 'Client Key');
  const storedKey = crypto.createHash('sha256').update(clientKey).digest();
  const authMsg = `n=user,r=${clientNonce},r=${start.nonce},s=${start.salt},i=${start.rounds},c=biws,r=${start.nonce}`;
  const clientSig = hmac(storedKey, authMsg);
  const proof = Buffer.alloc(clientKey.length);
  for (let i = 0; i < proof.length; i++) proof[i] = clientKey[i] ^ clientSig[i];

  const fin = await api('/auth/finish', 'POST', { transactionId: start.transactionId, proof: proof.toString('base64') });
  const serverSig = hmac(hmac(salted, 'Server Key'), authMsg);
  if (!Buffer.from(fin.signature, 'base64').equals(serverSig)) throw new Error('Wechselrichter-Signatur ungültig');

  const protocolKey = crypto.createHmac('sha256', storedKey).update('Session Key').update(authMsg).update(clientKey).digest();
  const iv = crypto.randomBytes(16);
  const cipher = crypto.createCipheriv('aes-256-gcm', protocolKey, iv);
  const payload = Buffer.concat([cipher.update(fin.token, 'utf8'), cipher.final()]);
  const sess = await api('/auth/create_session', 'POST', {
    transactionId: start.transactionId,
    iv: iv.toString('base64'),
    tag: cipher.getAuthTag().toString('base64'),
    payload: payload.toString('base64'),
  });
  const auth = { Authorization: 'Session ' + sess.sessionId };

  // --- Daten lesen ---
  const modules = ['devices:local', 'devices:local:battery', 'devices:local:pv1', 'devices:local:pv2', 'scb:statistic:EnergyFlow'];
  const res = await Promise.allSettled(modules.map((m) => api('/processdata/' + m, 'GET', undefined, auth)));
  await api('/auth/logout', 'POST', undefined, auth).catch(() => {});

  const val = {};
  res.forEach((r) => {
    if (r.status !== 'fulfilled' || !Array.isArray(r.value)) return;
    r.value.forEach((mod) => (mod.processdata || []).forEach((pd) => { val[`${mod.moduleid}/${pd.id}`] = pd.value; }));
  });
  const get = (k) => (typeof val[k] === 'number' ? val[k] : null);
  const kwh = (k) => (get(k) === null ? null : get(k) / 1000);
  const pv1 = get('devices:local:pv1/P');
  const pv2 = get('devices:local:pv2/P');
  if (Object.keys(val).length === 0) throw new Error('Keine Daten vom Wechselrichter');

  const S = 'scb:statistic:EnergyFlow/Statistic:';
  return {
    live: {
      pvKw: pv1 === null && pv2 === null ? null : ((pv1 || 0) + (pv2 || 0)) / 1000,
      hausKw: get('devices:local/Home_P') === null ? null : get('devices:local/Home_P') / 1000,
      akkuSoc: get('devices:local:battery/SoC'),
    },
    tag: {
      y: kwh(S + 'Yield:Day'),
      h: kwh(S + 'EnergyHome:Day'),
      pv: kwh(S + 'EnergyHomePv:Day'),
      bat: kwh(S + 'EnergyHomeBat:Day'),
    },
    monat: { y: kwh(S + 'Yield:Month'), h: kwh(S + 'EnergyHome:Month') },
  };
}

module.exports = { read };
