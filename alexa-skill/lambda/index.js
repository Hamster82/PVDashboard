const Alexa = require('ask-sdk-core');
const cfg = require('./config');
const dashboard = require('./dashboard');
const aplDoc = require('./apl/dashboard.json');

const SEITE = { heute: 0, woche: 1, monat: 2 };

function hasApl(hi) {
  const ifs = Alexa.getSupportedInterfaces(hi.requestEnvelope);
  return !!(ifs && ifs['Alexa.Presentation.APL']);
}

async function laden(hi) {
  const am = hi.attributesManager;
  let store = {};
  try { store = (await am.getPersistentAttributes()) || {}; } catch (e) { console.log('Speicher lesen:', e.message); }
  const r = await dashboard.collect(store);
  try { am.setPersistentAttributes(r.store); await am.savePersistentAttributes(); } catch (e) { console.log('Speicher schreiben:', e.message); }
  return r;
}

function antwort(hi, r, speech, seite = 0) {
  const rb = hi.responseBuilder;
  if (speech) rb.speak(speech);
  if (hasApl(hi)) {
    rb.addDirective({
      type: 'Alexa.Presentation.APL.RenderDocument',
      token: 'solarDashboard',
      document: aplDoc,
      datasources: { payload: { ...r.view, page: seite } },
    });
    // shouldEndSession bleibt offen (undefined): Dashboard bleibt sichtbar, Mikrofon geht nicht an
  } else {
    rb.withShouldEndSession(true);
  }
  return rb.getResponse();
}

const LaunchHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'LaunchRequest',
  async handle(hi) {
    const r = await laden(hi);
    return antwort(hi, r, r.speech, 0);
  },
};

const HeuteHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'IntentRequest'
    && ['HeuteIntent', 'AMAZON.NavigateHomeIntent'].includes(Alexa.getIntentName(hi.requestEnvelope)),
  async handle(hi) { const r = await laden(hi); return antwort(hi, r, r.speech, 0); },
};

const SeiteHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'IntentRequest'
    && ['WocheIntent', 'MonatIntent'].includes(Alexa.getIntentName(hi.requestEnvelope)),
  async handle(hi) {
    const r = await laden(hi);
    const woche = Alexa.getIntentName(hi.requestEnvelope) === 'WocheIntent';
    const b = woche ? r.view.woche : r.view.monat;
    const s = b.summen.length
      ? `${woche ? 'In den letzten sieben Tagen' : 'In den letzten sechs Monaten'}: Ertrag ${b.summen[0].value.replace(' kWh', ' Kilowattstunden')}, Verbrauch ${b.summen[1].value.replace(' kWh', ' Kilowattstunden')}.`
      : r.speech;
    return antwort(hi, r, s, woche ? SEITE.woche : SEITE.monat);
  },
};

function slotId(hi, name) {
  const slot = Alexa.getSlot(hi.requestEnvelope, name);
  const res = slot && slot.resolutions && slot.resolutions.resolutionsPerAuthority;
  if (res && res[0] && res[0].status.code === 'ER_SUCCESS_MATCH') return res[0].values[0].value.id;
  return slot && slot.value ? slot.value.toLowerCase() : null;
}

const GeraetHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'IntentRequest' && Alexa.getIntentName(hi.requestEnvelope) === 'GeraetIntent',
  async handle(hi) {
    const r = await laden(hi);
    if (!r.plan) return antwort(hi, r, r.speech, 0);
    const id = slotId(hi, 'geraet');
    const tagWunsch = slotId(hi, 'tag');
    const eintrag = r.plan.list.find((x) => x.geraet.id === id) || r.plan.list[0];
    let tag = eintrag.tag; let p = eintrag.plan;
    if (tagWunsch === 'morgen' && r.plan.morgen) { tag = 'morgen'; p = r.plan.morgen.geraete[eintrag.geraet.id]; }
    if (tagWunsch === 'heute' && r.plan.heute) { tag = 'heute'; p = r.plan.heute.geraete[eintrag.geraet.id]; }
    const s = p ? dashboard.speechFor(eintrag.geraet, tag, p) : `Für ${eintrag.geraet.name} finde ich ${tag} kein passendes Zeitfenster mehr.`;
    return antwort(hi, r, s, 0);
  },
};

/** Kurzansage: „Alexa, frag PV nach dem Status“ bzw. per Routine „Alexa, PV Strom“ */
const StatusHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'IntentRequest' && Alexa.getIntentName(hi.requestEnvelope) === 'StatusIntent',
  async handle(hi) {
    const r = await laden(hi);
    if (!r.plan) return antwort(hi, r, r.speech, 0);
    return antwort(hi, r, dashboard.kurzStatus(r), 0);
  },
};

const PrognoseHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'IntentRequest' && Alexa.getIntentName(hi.requestEnvelope) === 'PrognoseIntent',
  async handle(hi) {
    const r = await laden(hi);
    const morgen = slotId(hi, 'tag') === 'morgen';
    const kwh = morgen ? r.view.morgenKwh : r.view.heuteKwh;
    const wetter = morgen ? r.view.morgenWetter : r.view.heuteWetter.split(' · ')[0];
    return antwort(hi, r, `${morgen ? 'Morgen' : 'Heute'} erwarte ich etwa ${kwh} Kilowattstunden Solarstrom${wetter ? `, das Wetter wird ${wetter}` : ''}.`, 0);
  },
};

/** Automatische Aktualisierung aus dem Dashboard (alle 15 Minuten). */
const RefreshHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'Alexa.Presentation.APL.UserEvent',
  async handle(hi) { const r = await laden(hi); return antwort(hi, r, null, 0); },
};

const HelpHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'IntentRequest' && Alexa.getIntentName(hi.requestEnvelope) === 'AMAZON.HelpIntent',
  handle(hi) {
    const namen = cfg.geraete.map((g) => g.name).join(', ');
    return hi.responseBuilder
      .speak(`Frag mich zum Beispiel: Wann soll ich die Waschmaschine starten? Ich kenne: ${namen}. Oder sag: Zeig die Woche, oder: Zeig den Monat.`)
      .reprompt('Was möchtest du wissen?')
      .getResponse();
  },
};

const StopHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'IntentRequest'
    && ['AMAZON.StopIntent', 'AMAZON.CancelIntent'].includes(Alexa.getIntentName(hi.requestEnvelope)),
  handle: (hi) => hi.responseBuilder.speak('Bis später.').withShouldEndSession(true).getResponse(),
};

const FallbackHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'IntentRequest' && Alexa.getIntentName(hi.requestEnvelope) === 'AMAZON.FallbackIntent',
  handle: (hi) => hi.responseBuilder.speak('Das habe ich nicht verstanden. Frag zum Beispiel: Wann soll ich die Spülmaschine starten?').reprompt('Was möchtest du wissen?').getResponse(),
};

const SessionEndedHandler = {
  canHandle: (hi) => Alexa.getRequestType(hi.requestEnvelope) === 'SessionEndedRequest',
  handle: (hi) => hi.responseBuilder.getResponse(),
};

const ErrorHandler = {
  canHandle: () => true,
  handle(hi, error) {
    console.log('Fehler:', error && error.stack);
    return hi.responseBuilder.speak('Da ist etwas schiefgelaufen. Versuch es bitte gleich noch einmal.').withShouldEndSession(true).getResponse();
  },
};

/** Speicher: im Alexa-hosted Skill der mitgelieferte S3-Bucket, lokal ein einfacher Arbeitsspeicher. */
function persistence() {
  if (process.env.S3_PERSISTENCE_BUCKET) {
    const { S3PersistenceAdapter } = require('ask-sdk-s3-persistence-adapter');
    return new S3PersistenceAdapter({ bucketName: process.env.S3_PERSISTENCE_BUCKET });
  }
  const mem = {};
  return {
    getAttributes: async (env) => mem[env.context.System.user.userId] || {},
    saveAttributes: async (env, a) => { mem[env.context.System.user.userId] = a; },
    deleteAttributes: async (env) => { delete mem[env.context.System.user.userId]; },
  };
}

exports.handler = Alexa.SkillBuilders.custom()
  .addRequestHandlers(LaunchHandler, HeuteHandler, SeiteHandler, GeraetHandler, StatusHandler, PrognoseHandler, RefreshHandler, HelpHandler, StopHandler, FallbackHandler, SessionEndedHandler)
  .addErrorHandlers(ErrorHandler)
  .withPersistenceAdapter(persistence())
  .lambda();
