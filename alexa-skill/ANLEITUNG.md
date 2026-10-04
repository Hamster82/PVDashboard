# Solar-Planer – Alexa-Skill für Echo Show 8

Dashboard für die PV-Anlage in Osterhofen (9,6 kWp Ost/West, Kostal Plenticore Plus 8.5, Dyness 7,1 kWh).

**Was der Skill kann**

- **Seite „Heute“:** Solarprognose heute/morgen aus dem Wetter für deinen Standort, Stundenbalken mit grün markierten Laufzeiten, beste Startzeiten für Waschmaschine, Spülmaschine und Trockner, Live-Hinweis „jetzt einschalten / lieber warten“.
- **Seite „Woche“ und „Monat“:** Ertrag gegen Verbrauch als Balkendiagramm, Summen, Überschuss/Fehlmenge, Autarkie.
- **Sprache:** „Alexa, frag PV, wann soll ich die Waschmaschine starten?“
- Das Dashboard aktualisiert sich alle 15 Minuten selbst, solange es angezeigt wird.

**Was du brauchst:** nur ein kostenloses Amazon-Developer-Konto. Der Skill läuft kostenlos bei Amazon („Alexa-hosted“), du installierst nichts, die Wetterdaten (Open-Meteo) sind kostenlos und ohne Anmeldung.

---

## 1. Developer-Konto

1. Öffne <https://developer.amazon.com/alexa/console/ask>.
2. Melde dich mit **demselben Amazon-Konto** an, mit dem dein Echo Show 8 eingerichtet ist. Nur dann erscheint der Skill automatisch auf deinem Gerät.

## 2. Skill anlegen

1. **Skill erstellen** (Create Skill).
2. Name: `Solar Planer`, Sprache: **Deutsch (DE)**.
3. Typ: **Benutzerdefiniert / Other → Custom**.
4. Hosting: **Alexa-hosted (Node.js)**, Region **EU (Irland)**.
5. Vorlage: **Von Grund auf neu / Start from scratch** → erstellen. Das dauert etwa eine Minute.

## 3. Sprachmodell einfügen

Das Aufrufwort ist **„PV“**: Im Sprachmodell steht es als `p. v.` (so schreibt Amazon Abkürzungen, die buchstabiert werden).

1. Tab **Build** → links **Interaction Model → JSON Editor**.
2. Den gesamten Inhalt ersetzen durch die Datei `skill-package/interaktionsmodell-de-DE.json`.
3. **Save** und dann **Build Skill / Build Model**.

## 4. Bildschirm-Unterstützung einschalten

1. Tab **Build** → links **Interfaces**.
2. **Alexa Presentation Language** einschalten, dabei mindestens **Hub Landscape Medium** (Echo Show 8) anhaken.
3. **Save Interfaces**.

## 5. Programmcode einspielen

1. Tab **Code**.
2. **Code importieren** (Import Code) → die Datei `solar-planer-code.zip` wählen → alle Dateien übernehmen.
   - Falls die Konsole den Ordner nicht korrekt übernimmt, lege die Dateien im Ordner `lambda` von Hand an und kopiere den Inhalt hinein: `index.js`, `config.js`, `dashboard.js`, `forecast.js`, `planner.js`, `plenticore.js`, `util.js`, `package.json` und den Unterordner `apl/dashboard.json`.
3. **`config.js` prüfen**, vor allem die **Dachneigung** (`neigung`, Standard 30°). Die Aufteilung 4,8/4,8 kWp Ost/West ist bereits eingetragen.
4. **Save** und dann **Deploy**.

## 6. Testen

1. Tab **Test** → oben „Skill testing is enabled in“ auf **Development** stellen.
2. Eingabe: `öffne p. v.` – du siehst die Antwort und eine Vorschau des Displays.
3. Am Echo Show 8: **„Alexa, öffne PV.“**

## 7. Sprachbefehle

| Sag … | Ergebnis |
|---|---|
| „Alexa, öffne PV“ | Dashboard „Heute“ + Kurzansage |
| „Alexa, frag PV, wann soll ich die Spülmaschine starten?“ | Beste Startzeit heute (oder morgen) |
| „Alexa, frag PV, wann soll ich morgen den Trockner starten?“ | Plan für morgen |
| „Alexa, frag PV nach der Prognose für morgen“ | Ertrag morgen |
| „Alexa, frag PV nach den Sonnenstunden“ / „Zeig die Sonnenstunden“ | Seite „Sonne“: Sonnenstunden der nächsten 7 Tage |
| „Alexa, frag PV nach dem Status“ | Kurzansage: Überschuss, Akku, Prognose, Startzeiten |
| **„Alexa, PV Strom“** (über Routine, siehe unten) | dieselbe Kurzansage |
| „Zeig die Woche“ / „Zeig den Monat“ (während der Skill offen ist) | Diagrammseiten |

Auf dem Display kannst du zwischen **Heute · Woche · Monat · Sonne** wischen. Die Seite „Sonne“ zeigt die prognostizierten Sonnenstunden der nächsten 7 Tage (Balken vor der Tageslichtdauer) und darunter den erwarteten PV-Ertrag.

Versteht Alexa „PV“ nicht zuverlässig (bei Abkürzungen kommt das vor), stell das Aufrufwort unter *Build → Invocations → Skill Invocation Name* auf `pv anlage` um. Danach **Save** und **Build skill**; ab dann sagst du „Alexa, öffne PV Anlage“.

### Kurzansage mit „Alexa, PV Strom“

Ein Satz ohne „öffne“ oder „frag“ startet bei Alexa keinen eigenen Skill direkt. Deshalb übernimmt das eine Routine:

1. Alexa-App → **Mehr → Routinen → +**.
2. **Wenn Folgendes passiert** → **Sprache** → `PV Strom` eintippen.
3. **Aktion hinzufügen** → **Benutzerdefiniert** → `frag p. v. nach dem status` eintippen.
4. **Von** → *Echo Show 8* (bzw. „das Gerät, das du ansprichst“) → **Speichern**.

Beispiel der Ansage:
„Überschuss 1,8 Kilowatt, Akku 64 Prozent. Prognose heute 12, morgen 20 Kilowattstunden. Waschmaschine 13 Uhr 15 bis 15 Uhr 30, am besten 14 Uhr 15. Spülmaschine … Trockner morgen …“

Ohne Wechselrichter-Anbindung sagt Alexa „Überschuss laut Prognose …“ und lässt den Akku weg.

## 8. Automatisch anzeigen lassen (Routine)

In der Alexa-App unter **Mehr → Routinen → +**:

- **Morgens:** Auslöser *Zeitplan* 07:30 → Aktion *Skills → Solar Planer*, Gerät: *Echo Show 8*. Das Dashboard steht dann jeden Morgen auf dem Bildschirm.
- **Abends (nur mit Wechselrichter-Anbindung, siehe Abschnitt 9):** zweite Routine um **22:30**. So wird jeder Tag vollständig gespeichert und die Wochen- und Monatsauswertung zeigt echte Werte.

Hinweis: Wie lange der Echo Show das Dashboard ohne Berührung anzeigt, entscheidet das Gerät selbst. Der Skill bittet um 30 Minuten und lädt sich alle 15 Minuten neu.

---

## 9. Optional: echte Werte vom Kostal Plenticore

Ohne diesen Schritt rechnet der Skill **nur mit dem Wettermodell**. Ertrag ist dann geschätzt, Verbrauch aus `jahresverbrauchKwh` hochgerechnet; die Balken sind blass dargestellt. Die Gerätezeiten funktionieren trotzdem.

Mit diesem Schritt kommen echte Tages- und Monatswerte, Live-Leistung und Akkustand dazu. Außerdem kalibriert der Skill die Prognose nach ein paar Tagen automatisch auf deine Anlage.

**Warum eine Portfreigabe nötig ist:** Der Skill läuft in der Amazon-Cloud. Dein Wechselrichter steht im Heimnetz hinter der Fritzbox und ist von außen nicht erreichbar. Ohne zusätzliche Hardware oder Software geht es nur über eine Freigabe in der Fritzbox.

> **Sicherheit – bitte bewusst entscheiden:** Mit der Freigabe ist die Weboberfläche des Wechselrichters aus dem Internet erreichbar (passwortgeschützt, HTTPS).
> - Ein **langes, eigenes Passwort** für den Anlagenbetreiber verwenden.
> - Die **Wechselrichter-Firmware aktuell** halten.
> - Den Zertifikat-Fingerabdruck eintragen (siehe unten).
>
> Der Skill selbst liest nur und ändert nichts am Wechselrichter.

**Voraussetzung:** Dein Anschluss hat eine **öffentliche IPv4-Adresse** (kein reines „DS-Lite“). Das siehst du in der Fritzbox unter *Übersicht → Verbindungen*.

**Schritte in der Fritzbox 5690 Pro:**

1. *Internet → Freigaben → Portfreigaben → Gerät für Freigaben hinzufügen* → den Plenticore auswählen.
2. *Neue Freigabe → Portfreigabe*:
   - Protokoll **TCP**
   - Port an Gerät **443**
   - Port extern gewünscht **48443**
   - **IPv4** aktivieren
3. *Internet → MyFRITZ!-Konto*: MyFRITZ! einrichten und die Adresse notieren (`xxxx.myfritz.net`).

**Zertifikat-Fingerabdruck (empfohlen):**

1. Im Browser `https://<IP des Wechselrichters>` öffnen.
2. Auf das Schloss/Warnsymbol klicken → *Zertifikat anzeigen*.
3. Den Wert **SHA-256-Fingerabdruck** kopieren.

**In `config.js` eintragen:**

```js
plenticore: {
  aktiv: true,
  host: 'xxxx.myfritz.net',
  port: 48443,
  passwort: 'Passwort-Anlagenbetreiber',
  zertifikatFingerprint: 'AB:CD:…',
},
```

Danach **Save → Deploy**.

In der Fußzeile des Dashboards steht dann **„Live-Daten: Kostal Plenticore“**. Klappt die Verbindung nicht, steht dort der Grund und der Skill arbeitet mit dem Wettermodell weiter.

---

## 10. Anpassen

In `config.js`:

- **`geraete`:** Lastprofil je Gerät als `[Minuten, kW]`. Entscheidend sind die Heizphasen. Ein zusätzliches Gerät (z. B. Wallbox-Ladung) einfach als weitere Zeile ergänzen. Damit es per Sprache gefunden wird, braucht es auch einen Eintrag im Sprachmodell (Typ `GERAET`, gleiche `id`).
- **`grundlastKw`:** dein Dauerverbrauch.
- **`fruehesterStart` / `spaetesterStart`:** erlaubte Startzeiten.

## So rechnet der Skill

- **Wetter:** Open-Meteo liefert stündlich die Einstrahlung direkt auf die Ost- und auf die Westfläche (Neigung/Ausrichtung aus `config.js`). Daraus wird mit Temperaturverlust, Systemverlusten (86 %) und der Wechselrichtergrenze 8,5 kW die Leistung berechnet.
- **Geräteplanung:** in 15-Minuten-Schritten. Für jede mögliche Startzeit wird berechnet, wie viel vom Gerätebedarf der Solarüberschuss (PV minus Grundlast) deckt. Angezeigt wird das Startfenster mit mindestens 90 % des bestmöglichen Werts. Geräte werden nacheinander verplant, damit sie sich nicht denselben Überschuss teilen. Lohnt sich heute nichts mehr, empfiehlt der Skill morgen.
- **Akku:** Mit Live-Daten prüft der Skill, ob der Überschuss für Akku **und** Geräte reicht, und gibt einen Hinweis.
