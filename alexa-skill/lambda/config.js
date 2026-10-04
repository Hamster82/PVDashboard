// =====================================================================
//  SOLAR-PLANER – EINSTELLUNGEN
//  Nur diese Datei musst du anpassen. Alles andere bleibt unverändert.
// =====================================================================
module.exports = {
  // Standort (Osterhofen, Mühlbachstr. 5 – auf ca. 100 m genau reicht völlig)
  standort: { name: 'Osterhofen', lat: 48.7010, lon: 13.0200 },

  // PV-Anlage: 9,6 kWp, Ost/West 50:50
  //   neigung: Dachneigung in Grad (bitte prüfen – Standard 30°)
  //   azimut:  -90 = Osten, 0 = Süden, 90 = Westen
  anlage: {
    flaechen: [
      { name: 'Ost', kwp: 4.8, neigung: 30, azimut: -90 },
      { name: 'West', kwp: 4.8, neigung: 30, azimut: 90 },
    ],
    wechselrichterMaxKw: 8.5,   // Kostal Plenticore Plus 8.5
    performanceRatio: 0.86,     // Verluste Kabel, Wechselrichter, Verschmutzung
    tempKoeffizient: -0.0037,   // Leistungsverlust je °C Modultemperatur über 25 °C
  },

  akku: { kapazitaetKwh: 7.1 },  // Dyness

  haushalt: {
    grundlastKw: 0.35,           // Dauerverbrauch (Kühlschrank, Router, Standby …)
    jahresverbrauchKwh: 4500,    // nur für Schätzung, solange kein Wechselrichter angebunden ist
    fruehesterStart: '07:00',    // Geräte frühestens …
    spaetesterStart: '19:00',    // … und spätestens zu dieser Uhrzeit starten
  },

  // Hauptverbraucher mit Lastprofil: Liste von [Minuten, kW] in zeitlicher Reihenfolge.
  // Wichtig sind die Heizphasen (hohe kW) – sie bestimmen, wann genug Sonne da sein muss.
  // Reihenfolge = Priorität (das erste Gerät bekommt die beste Sonnenzeit).
  geraete: [
    // 40-°C-Wäsche: 20 min Heizen mit 2 kW, danach Waschen/Schleudern  (≈ 1,0 kWh)
    { id: 'waschmaschine', name: 'Waschmaschine', profil: [[20, 2.0], [130, 0.15]], farbe: '#4cc38a' },
    // Eco-Programm: Aufheizen am Anfang und zum Klarspülen  (≈ 1,2 kWh)
    { id: 'spuelmaschine', name: 'Spülmaschine', profil: [[20, 1.8], [125, 0.08], [15, 1.8], [20, 0.05]], farbe: '#5aa9e6' },
    // Wärmepumpentrockner: gleichmäßig  (≈ 1,6 kWh)
    { id: 'trockner', name: 'Trockner', profil: [[150, 0.65]], farbe: '#c58af9' },
  ],

  // OPTIONAL: echte Ertrags-, Verbrauchs- und Akkudaten vom Kostal Plenticore.
  // Voraussetzung: Portfreigabe in der Fritzbox (siehe Anleitung, Abschnitt 5).
  // Ohne Freigabe (aktiv: false) arbeitet der Skill nur mit dem Wettermodell.
  plenticore: {
    aktiv: false,
    host: 'xxxxxxxxxxxxxxxx.myfritz.net', // deine MyFRITZ!-Adresse
    port: 48443,                          // externer Port aus der Fritzbox-Freigabe
    passwort: '',                         // Passwort "Anlagenbetreiber" (Web-Oberfläche)
    zertifikatFingerprint: '',            // optional, SHA-256 des Wechselrichter-Zertifikats
  },
};
