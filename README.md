# Geen regen aub

A personal rain-radar app for the Netherlands: will it rain here in the next two hours, and when?
Native Android app with a home-screen widget, fed by a small relay server that turns KNMI's radar
nowcast into a few hundred bytes of JSON per request.

| Folder | What | Stack |
| --- | --- | --- |
| [`android/`](android/README.md) | The app and widget | Kotlin, Jetpack Compose, Material 3, Glance, WorkManager |
| [`relay/`](relay/README.md) | The relay server, run on BrooklynServer | Python, FastAPI, Docker Compose |
| [`docs/`](docs/) | Setup guides | |

```
KNMI Open Data API ──(API key)──> relay (BrooklynServer, Docker)
                                    │  https://geenregenaub.theharmen.org via Nginx Proxy Manager
                                    ▼
                    phone app + widget (sends the shared secret header)
```

## Setup

1. [Get a KNMI API key](docs/knmi-api-key.md) and put it in `relay/.env`.
2. [Run the relay](relay/README.md) on BrooklynServer with `docker compose up -d --build`.
3. [Build or download the app](android/README.md). Every push builds a debug APK in GitHub Actions.

## Data and attribution

- Radar nowcast: [KNMI radar_forecast 2.0](https://dataplatform.knmi.nl/dataset/radar-forecast-2-0),
  CC BY 4.0. The app shows "Radardata: KNMI, CC BY 4.0" wherever the data appears.
- Fallback: [Open-Meteo](https://open-meteo.com/), CC BY 4.0, free non-commercial tier.
- Weather forecast (temperature, sky, wind; hour by hour and day by day): [Open-Meteo](https://open-meteo.com/),
  CC BY 4.0, fetched straight from the phone (coordinates rounded to ~1 km). The relay is not involved.
- Air quality (European AQI, for the air quality section of the weather screen): Copernicus Atmosphere Monitoring Service (CAMS)
  via the [Open-Meteo air quality API](https://open-meteo.com/en/docs/air-quality-api), same terms.
- Moon phases are worked out on the phone (Meeus, "Astronomical Algorithms"); no data source needed.
- Weather icons: [Meteocons](https://github.com/basmilius/weather-icons) by Bas Milius, MIT licence,
  converted to Android vector drawables. The licence text ships in the app
  (`android/feature/forecast/src/main/res/raw/meteocons_license.txt`) and opens from the weather screen.

## Status

Milestone 1 (foundation): project skeleton, CI, KNMI key setup. The app shows a placeholder home
screen and widget; the relay checks the shared secret and the KNMI key but does not decode radar
files yet (milestone 2).
