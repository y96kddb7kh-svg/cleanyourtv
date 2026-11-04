# Sky & RAM

One full-screen page with the **time and date, the local weather and the RAM in use**: a clock line, the time in
St Denis (La Réunion, 🇷🇪) and Fort-de-France (Martinique, 🇲🇶) with their flag emoji in the names (they fall
back to the letters "RE"/"MQ" on TVs without an emoji font), the city, the temperature now, the conditions and
feels-like, and a memory bar with its percentage. For the [Clean Your TV](../../README.md)
launcher it declares a [module card](../../docs/modules.md), so once you answer **Show card** its tile reads
"Sky & RAM" with a sky-blue glow; on any other Android TV home it is an ordinary app.

- **The city** is chosen once: type its name with the remote's keyboard (or voice), pick it from the matches,
  and it is kept. **Change city** at the bottom chooses another.
- **Weather data** comes from [Open-Meteo](https://open-meteo.com/), which needs no account or key, using DWD's
  ICON model. It is read only while the page is on screen: every 30 minutes, or 5 minutes after a failure.
- **The RAM percentage** is read every ten seconds from `/proc/meminfo` (used over total), which needs no
  permission and no network. The clock and the two other zones tick every second, in the TV's own time and date
formats (zones `Indian/Reunion` and `America/Martinique`).
- Degrees Celsius by default (set `temperature_unit` to `f` in `values/strings.xml` for Fahrenheit); English or
  French words, with the TV's language.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Open-Meteo: the place search and the weather. The RAM reading needs none. |

That is all. What is sent: the city name you type in the search, then that place's latitude and longitude. No
account, no telemetry, no ads, no background service, no location permission. The chosen city stays in the
app's own storage (backups are off).

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
cd addons/weather-ram && ./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card. Change the answer later by holding OK on
Settings, then Module cards.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Tested on

Not yet tested on a real TV.

## Maintainer

CleanYourTV contributors
