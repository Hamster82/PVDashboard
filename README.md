# PV Dashboard

Android-App für die PV-Anlage in Osterhofen (9,6 kWp Ost/West, Kostal Plenticore Plus 8.5, Dyness 7,1 kWh).

**Heute**
- Solarprognose für heute und morgen aus der Wettervorhersage für den Standort (Open-Meteo, kostenlos, ohne Anmeldung)
- Beste Startzeiten für Waschmaschine, Spülmaschine und Trockner mit dem Anteil an Sonnenstrom
- Live-Werte vom Wechselrichter: PV-Leistung, Hausverbrauch, Akku, Netz und Autarkie

**Woche / Monate**
- Ertrag gegen Verbrauch als Diagramm und Tabelle, mit Überschuss bzw. Fehlmenge und Autarkie

**Benachrichtigung**
- Meldung, sobald das empfohlene Startfenster eines Geräts beginnt

## Installieren

1. Auf dem Handy die Seite **Releases** dieses Repositorys öffnen: <https://github.com/Hamster82/PVDashboard/releases/latest>
2. Unter *Assets* **PVDashboard.apk** herunterladen und öffnen.
3. Android fragt beim ersten Mal, ob der Browser „unbekannte Apps installieren“ darf → erlauben → **Installieren**.
4. Updates installierst du genauso; die alte Version wird ersetzt, Einstellungen und Verlauf bleiben erhalten.

## Einrichten

In der App unter **Einstellungen**:

- **Standort und Anlage** sind für Osterhofen und 2 × 4,8 kWp (Ost −90°, West 90°, 30° Neigung) vorbelegt. Die Dachneigung bitte prüfen.
- **Geräte:** Dauer, Energie pro Durchlauf und die Heizphase am Programmanfang. Geräte lassen sich hinzufügen und entfernen.
- **Kostal Plenticore** (optional, für echte Werte):
  - *Adresse im Heimnetz*: die IP des Wechselrichters, z. B. `192.168.178.40`. Sie steht in der Fritzbox unter *Heimnetz → Netzwerk*.
  - *Passwort*: das Passwort für „Anlagenbetreiber“ aus der Weboberfläche.
  - *Verbindung testen* drücken.
  - Im Heimnetz ist **keine Portfreigabe** nötig. Nur für Live-Werte von unterwegs zusätzlich die MyFRITZ!-Adresse mit Port eintragen (z. B. `xxxx.myfritz.net:48443`).

Ohne Wechselrichter rechnet die App mit dem Wettermodell und einem geschätzten Verbrauch (blasse Balken).

## Wie die Werte entstehen

- **Prognose:** Die stündliche Einstrahlung wird direkt auf die Ost- und die Westfläche berechnet. Berücksichtigt werden Modultemperatur, 86 % Systemwirkungsgrad und die 8,5-kW-Grenze des Wechselrichters. Liegen echte Tageswerte vom Wechselrichter vor, kalibriert sich die Prognose automatisch (Median der letzten 14 Tage).
- **Startzeiten:** in 15-Minuten-Schritten. Für jede mögliche Startzeit wird berechnet, wie viel vom Lastprofil des Geräts der Solarüberschuss deckt. Angezeigt wird das Startfenster mit mindestens 90 % des bestmöglichen Werts. Die Geräte werden nacheinander verplant, damit sie sich nicht denselben Überschuss teilen.
- **Verlauf:** Die App fragt etwa alle 30 Minuten im Hintergrund die Tages- und Monatszähler des Wechselrichters ab und speichert sie auf dem Handy. Ein Tag gilt als vollständig, wenn nach 22 Uhr ein Wert erfasst wurde. Das klappt, solange das Handy abends im WLAN ist oder eine Adresse für unterwegs eingetragen ist.
- **Sicherheit:** Die App liest nur und verändert nichts am Wechselrichter. Das Zertifikat des Wechselrichters wird bei der ersten Verbindung gemerkt und danach geprüft.

## Bauen

Jeder Push auf `main` baut über GitHub Actions eine signierte APK und veröffentlicht sie als Release.

`app/pvdashboard.keystore` ist ein eigener Schlüssel nur für diese App. Er liegt im Repository, damit jede neue Version sich über die alte installieren lässt.
