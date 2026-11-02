# Relay API contract (v1)

This is the contract between the relay (`relay/`) and the Android app (`android/`). The app codes
against this document. Changes to it are announced and land in a separate pull request; fields are
only ever **added** within v1, never renamed or removed.

Base URL: `https://geenregenaub.theharmen.org`

## Authentication

Every `/v1` request carries the shared secret:

```
X-Geenregen-Key: <RELAY_SHARED_SECRET>
```

## `GET /v1/rain?lat=<lat>&lon=<lon>`

The 2-hour precipitation nowcast for one point.

| Parameter | Type | Rules |
| --- | --- | --- |
| `lat` | decimal degrees, WGS84 | 49.3 to 55.9 |
| `lon` | decimal degrees, WGS84 | 0.0 to 10.9 |

The relay rounds the point to 0.01° (about 1 km) before doing anything with it, and echoes the
rounded point back. Apps may round before sending too, which also makes caching better.

### 200 OK

```json
{
  "point": { "lat": 52.09, "lon": 5.12 },
  "source": "knmi",
  "issued_at": "2026-11-02T21:00:00Z",
  "generated_at": "2026-11-02T21:03:12Z",
  "interval_minutes": 5,
  "unit": "mm/h",
  "series": [
    { "time": "2026-11-02T21:00:00Z", "mm_per_hour": 0.0 },
    { "time": "2026-11-02T21:05:00Z", "mm_per_hour": 0.4 },
    { "time": "2026-11-02T21:10:00Z", "mm_per_hour": 1.8 }
  ],
  "summary": {
    "raining_now": false,
    "rain_starts_at": "2026-11-02T21:05:00Z",
    "rain_ends_at": "2026-11-02T21:45:00Z",
    "max_mm_per_hour": 3.6,
    "total_mm": 1.2
  },
  "stale": false,
  "attribution": "Radardata: KNMI, CC BY 4.0"
}
```

(The `series` above is shortened; a real response always has 25 entries.)

| Field | Type | Meaning |
| --- | --- | --- |
| `point` | object | The rounded point the forecast is for. |
| `source` | `"knmi"` or `"open-meteo"` | Where the numbers came from. `open-meteo` is the fallback when KNMI is unavailable; it is coarser (model data, 15-minute steps spread over three 5-minute entries). New values may be added later; treat unknown values like `open-meteo`. |
| `issued_at` | ISO 8601 UTC string | Time of the radar image the nowcast starts from. Equals `series[0].time`. |
| `generated_at` | ISO 8601 UTC string | When the relay built this answer. |
| `interval_minutes` | integer | Always `5` in v1. |
| `unit` | string | Always `"mm/h"` in v1. |
| `series` | array | Exactly 25 entries, 5 minutes apart, from `issued_at` to `issued_at` + 120 min, oldest first. |
| `series[].time` | ISO 8601 UTC string | The moment the value applies to. |
| `series[].mm_per_hour` | number ≥ 0 | Precipitation intensity, rounded to 0.1. KNMI's 5-minute amounts are converted to an hourly rate (× 12). |
| `summary.raining_now` | boolean | `series[0].mm_per_hour >= 0.1`. |
| `summary.rain_starts_at` | string or `null` | First `time` with `mm_per_hour >= 0.1` (equals `issued_at` when it rains now); `null` if no rain in the next 2 hours. |
| `summary.rain_ends_at` | string or `null` | First `time` after `rain_starts_at` with `mm_per_hour < 0.1`; `null` if there is no rain, or it does not stop within 2 hours. |
| `summary.max_mm_per_hour` | number | Highest value in `series`. |
| `summary.total_mm` | number | Expected total amount over the 2 hours, rounded to 0.1. |
| `stale` | boolean | `true` when the relay could not get a fresh forecast and serves the newest one it has, which may be older than 15 minutes. The app can show "last updated at …" from `issued_at`. |
| `attribution` | string | Show this text near the radar data (KNMI's CC BY 4.0 licence). For Open-Meteo it is `"Weather data: Open-Meteo.com, CC BY 4.0"`. |

All times are UTC with a `Z` suffix; the app converts to local time.

Suggested intensity bands for the app (not part of the response):

| Band | mm/h |
| --- | --- |
| dry | < 0.1 |
| light | 0.1 to 2.5 |
| moderate | 2.5 to 10 |
| heavy | ≥ 10 |

### Caching headers

A 200 carries `Cache-Control: max-age=<seconds>` until the next KNMI file is expected (at most 300).
Polling more often than that returns the same answer.

### Errors

Error bodies are FastAPI's shape: `{"detail": ...}`. The app should key on the status code only.

| Status | When | What the app does |
| --- | --- | --- |
| 401 | Missing or wrong `X-Geenregen-Key` | Configuration error; show a message, do not retry. |
| 422 | `lat`/`lon` missing, not a number, or outside the box above | Show "outside the Netherlands". |
| 503 | Relay not configured, or neither KNMI nor Open-Meteo answered and nothing is cached | Retry later (back off, at least 1 minute). |
| 5xx other | Relay bug or proxy trouble | Retry later. |

## `GET /v1/radar`

Frames for the animated radar map: the past hour as measured (the +0 frame of each earlier KNMI
file the relay saw) and then the current 2-hour forecast, oldest first.

### 200 OK

```json
{
  "issued_at": "2026-11-02T21:00:00Z",
  "bounds": { "north": 55.97357, "south": 48.89527, "east": 10.85645, "west": 0.0 },
  "width": 700,
  "height": 751,
  "interval_minutes": 5,
  "frames": [
    { "time": "2026-11-02T20:55:00Z", "kind": "observed", "url": "/v1/radar/202611022055-202611022055.png" },
    { "time": "2026-11-02T21:00:00Z", "kind": "observed", "url": "/v1/radar/202611022100-202611022100.png" },
    { "time": "2026-11-02T21:05:00Z", "kind": "forecast", "url": "/v1/radar/202611022100-202611022105.png" }
  ],
  "legend": [ { "min_mm_per_hour": 0.1, "color": "#8db8f596" } ],
  "attribution": "Radardata: KNMI, CC BY 4.0"
}
```

- Each frame is a transparent PNG in **Web Mercator**, to be stretched linearly between `bounds`
  (top-left = north/west, bottom-right = south/east), e.g. a MapLibre `ImageSource`.
- `kind` is `observed` for measured rain (the past hour and the current +0 step), `forecast` after.
- `legend` lists the colour classes from light to heavy; `color` is `#rrggbbaa`.
- `Cache-Control: max-age=60`. 503 when there is no KNMI file yet or the newest is over 2 hours old
  (the map has no Open-Meteo fallback).

## `GET /v1/radar/{id}.png`

One frame from the list above (use its `url`). A frame id names one KNMI file and one time, so the
picture never changes: `Cache-Control: max-age=86400, immutable`. 404 once it has expired (frames
older than an hour are dropped).

## `GET /healthz`

No authentication. Returns `{"status": "ok", ...}` with booleans about the configuration, never
secret values. For Docker and Nginx Proxy Manager; the app does not need it.
