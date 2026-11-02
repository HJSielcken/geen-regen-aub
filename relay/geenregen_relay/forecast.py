"""Builds the /v1/rain answer (see docs/api.md) from KNMI, with Open-Meteo as fallback.

KNMI: a background thread asks every `poll_seconds` for the newest radar_forecast file and downloads
and decodes it only when its name changed, so each file is fetched exactly once whatever the number
of phones. Open-Meteo: asked per rounded point, cached for a few minutes.
"""

from __future__ import annotations

import logging
import threading
import time
from collections import OrderedDict
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from .config import Settings
from .frames import FrameRenderer
from .knmi import KnmiClient, KnmiError
from .openmeteo import ATTRIBUTION as OPEN_METEO_ATTRIBUTION
from .openmeteo import OpenMeteoClient, OpenMeteoError, resample
from .radar import RadarForecast, RadarFormatError, decode

log = logging.getLogger("geenregen_relay")

KNMI_ATTRIBUTION = "Radardata: KNMI, CC BY 4.0"
STEPS = 25
INTERVAL = timedelta(minutes=5)
RAIN_THRESHOLD = 0.1  # mm/h
# A KNMI forecast older than this counts as stale; Open-Meteo is tried first.
STALE_AFTER = timedelta(minutes=20)
# Never serve anything older than this, not even as stale.
TOO_OLD = timedelta(hours=2)
OPEN_METEO_TTL = timedelta(minutes=5)
OPEN_METEO_CACHE_SIZE = 2048
# How far back the radar map shows observed rain.
MAP_HISTORY = timedelta(hours=1)


class NoForecast(RuntimeError):
    pass


@dataclass(frozen=True)
class MapFrame:
    id: str
    time: datetime
    kind: str  # "observed" or "forecast"
    png: bytes


def _frame_id(issued: datetime, time: datetime) -> str:
    """Unique per issue and time, so a frame's PNG never changes and can be cached for long."""
    return f"{issued.astimezone(UTC):%Y%m%d%H%M}-{time.astimezone(UTC):%Y%m%d%H%M}"


@dataclass(frozen=True)
class Answer:
    body: dict
    max_age: int


def _iso(t: datetime) -> str:
    return t.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


def _floor5(t: datetime) -> datetime:
    t = t.astimezone(UTC).replace(second=0, microsecond=0)
    return t - timedelta(minutes=t.minute % 5)


def build_body(
    *,
    lat: float,
    lon: float,
    source: str,
    issued_at: datetime,
    values: list[float],
    stale: bool,
    attribution: str,
    now: datetime,
) -> dict:
    values = [round(max(0.0, v), 1) for v in values]
    times = [issued_at + i * INTERVAL for i in range(STEPS)]
    wet = [v >= RAIN_THRESHOLD for v in values]
    start = next((i for i, w in enumerate(wet) if w), None)
    end = None if start is None else next((i for i in range(start + 1, STEPS) if not wet[i]), None)
    return {
        "point": {"lat": lat, "lon": lon},
        "source": source,
        "issued_at": _iso(issued_at),
        "generated_at": _iso(now),
        "interval_minutes": 5,
        "unit": "mm/h",
        "series": [{"time": _iso(t), "mm_per_hour": v} for t, v in zip(times, values, strict=True)],
        "summary": {
            "raining_now": wet[0],
            "rain_starts_at": None if start is None else _iso(times[start]),
            "rain_ends_at": None if end is None else _iso(times[end]),
            "max_mm_per_hour": max(values),
            "total_mm": round(sum(values[1:]) * INTERVAL.total_seconds() / 3600, 1),
        },
        "stale": stale,
        "attribution": attribution,
    }


class ForecastService:
    def __init__(
        self,
        settings: Settings,
        knmi: KnmiClient | None = None,
        open_meteo: OpenMeteoClient | None = None,
        clock: Callable[[], datetime] = lambda: datetime.now(UTC),
    ):
        self._settings = settings
        self._clock = clock
        self._knmi = knmi
        if self._knmi is None and settings.knmi_api_key:
            self._knmi = KnmiClient(settings)
        self._open_meteo = open_meteo or OpenMeteoClient(settings.open_meteo_url)
        self._radar: RadarForecast | None = None
        self._radar_fetched_at: datetime | None = None
        # Last failure of each source, shown on /healthz to make problems on the server visible.
        self.last_knmi_error: str | None = None if self._knmi else "KNMI_API_KEY is not set"
        self.last_open_meteo_error: str | None = None
        self._lock = threading.Lock()
        self._om_cache: OrderedDict[tuple[float, float], tuple[datetime, list[tuple[datetime, float]]]] = OrderedDict()
        self._om_lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        # Radar map: the current forecast's frames, plus the observed (+0) frame of each earlier file.
        self._renderer: FrameRenderer | None = None
        self._renderer_grid = None
        self._forecast_frames: list[MapFrame] = []
        self._observed_frames: OrderedDict[datetime, MapFrame] = OrderedDict()

    # --- KNMI -------------------------------------------------------------------------------

    @property
    def radar(self) -> RadarForecast | None:
        return self._radar

    def refresh(self) -> bool:
        """Fetch the newest KNMI file if it is new. True when a new forecast was loaded."""
        if self._knmi is None:
            return False
        with self._lock:
            try:
                files = self._knmi.latest_files(count=1)
                if not files:
                    return False
                name = files[0].filename
                if self._radar is not None and self._radar.filename == name:
                    return False
                forecast = decode(name, self._knmi.download(name))
            except (KnmiError, RadarFormatError, OSError) as error:
                log.warning("KNMI refresh failed: %s", error)
                self.last_knmi_error = f"{_iso(self._clock())} {error}"
                return False
            except Exception as error:  # an unexpected file layout must not kill the poller thread
                log.exception("KNMI refresh failed unexpectedly")
                self.last_knmi_error = f"{_iso(self._clock())} {type(error).__name__}: {error}"
                return False
            self.last_knmi_error = None
            self._radar = forecast
            self._radar_fetched_at = self._clock()
            try:
                self._render_frames(forecast)
            except Exception:  # the map is extra; never let it break the rain answer
                log.exception("Rendering radar map frames failed")
            log.info("Loaded %s (issued %s, %d steps)", name, _iso(forecast.issued_at), len(forecast.steps))
            return True

    def _render_frames(self, forecast: RadarForecast) -> None:
        if self._renderer is None or self._renderer_grid != forecast.grid:
            self._renderer = FrameRenderer(forecast.grid)
            self._renderer_grid = forecast.grid
            self._observed_frames.clear()
        issued = forecast.issued_at
        frames = [
            MapFrame(
                id=_frame_id(issued, step.time),
                time=step.time,
                kind="observed" if index == 0 else "forecast",
                png=self._renderer.png(step),
            )
            for index, step in enumerate(forecast.steps)
        ]
        self._forecast_frames = frames
        self._observed_frames[issued] = frames[0]
        for old in [t for t in self._observed_frames if issued - t > MAP_HISTORY]:
            del self._observed_frames[old]

    def map_frames(self) -> tuple[RadarForecast, FrameRenderer, list[MapFrame]]:
        """Observed frames of the past hour, then the current forecast. Raises NoForecast."""
        radar, renderer, current = self._radar, self._renderer, self._forecast_frames
        if radar is None or renderer is None or not current:
            raise NoForecast("No KNMI radar frames yet")
        if self._clock() - radar.issued_at > TOO_OLD:
            raise NoForecast("The KNMI radar frames are too old")
        past = [f for t, f in list(self._observed_frames.items()) if t < radar.issued_at]
        return radar, renderer, past + current

    def map_frame(self, frame_id: str) -> MapFrame | None:
        for frame in list(self._observed_frames.values()) + self._forecast_frames:
            if frame.id == frame_id:
                return frame
        return None

    def start(self) -> None:
        if self._thread is not None or self._knmi is None:
            return

        def loop() -> None:
            while not self._stop.is_set():
                started = time.monotonic()
                self.refresh()
                self._stop.wait(max(1.0, self._settings.poll_seconds - (time.monotonic() - started)))

        self._thread = threading.Thread(target=loop, name="knmi-poller", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=5)
            self._thread = None

    def _knmi_values(self, lat: float, lon: float) -> tuple[RadarForecast, list[float]] | None:
        radar = self._radar
        if radar is None:
            return None
        series = radar.series_at(lat, lon)
        if series is None or all(v is None for _, v in series):
            return None
        by_time = {t: v for t, v in series}
        times = [radar.issued_at + i * INTERVAL for i in range(STEPS)]
        return radar, [by_time.get(t) or 0.0 for t in times]

    # --- Open-Meteo -------------------------------------------------------------------------

    def _open_meteo_slots(self, lat: float, lon: float, now: datetime) -> list[tuple[datetime, float]]:
        key = (lat, lon)
        with self._om_lock:
            hit = self._om_cache.get(key)
            if hit and now - hit[0] < OPEN_METEO_TTL:
                self._om_cache.move_to_end(key)
                return hit[1]
        slots = self._open_meteo.slots(lat, lon)
        with self._om_lock:
            self._om_cache[key] = (now, slots)
            self._om_cache.move_to_end(key)
            while len(self._om_cache) > OPEN_METEO_CACHE_SIZE:
                self._om_cache.popitem(last=False)
        return slots

    # --- The answer -------------------------------------------------------------------------

    def _max_age(self, now: datetime) -> int:
        if self._radar_fetched_at is None:
            return 60
        next_file = self._radar_fetched_at + INTERVAL
        return int(min(300, max(30, (next_file - now).total_seconds())))

    def forecast(self, lat: float, lon: float) -> Answer:
        now = self._clock()
        knmi = self._knmi_values(lat, lon)
        if knmi is not None and now - knmi[0].issued_at <= STALE_AFTER:
            radar, values = knmi
            body = build_body(
                lat=lat,
                lon=lon,
                source="knmi",
                issued_at=radar.issued_at,
                values=values,
                stale=False,
                attribution=KNMI_ATTRIBUTION,
                now=now,
            )
            return Answer(body, self._max_age(now))

        try:
            slots = self._open_meteo_slots(lat, lon, now)
            issued = _floor5(now)
            values = resample(slots, [issued + i * INTERVAL for i in range(STEPS)])
            body = build_body(
                lat=lat,
                lon=lon,
                source="open-meteo",
                issued_at=issued,
                values=values,
                stale=False,
                attribution=OPEN_METEO_ATTRIBUTION,
                now=now,
            )
            return Answer(body, 60)
        except OpenMeteoError as error:
            log.warning("Open-Meteo fallback failed: %s", error)
            self.last_open_meteo_error = f"{_iso(now)} {error}"

        if knmi is not None and now - knmi[0].issued_at <= TOO_OLD:
            radar, values = knmi
            body = build_body(
                lat=lat,
                lon=lon,
                source="knmi",
                issued_at=radar.issued_at,
                values=values,
                stale=True,
                attribution=KNMI_ATTRIBUTION,
                now=now,
            )
            return Answer(body, 60)
        raise NoForecast("Neither KNMI nor Open-Meteo has a forecast right now")
