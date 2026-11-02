"""HTTP API for the app.

Every /v1 endpoint requires the shared secret in the X-Geenregen-Key header, because the relay
is reachable on the public internet at https://geenregenaub.theharmen.org.
"""

from __future__ import annotations

import hmac
import logging
import math
from contextlib import asynccontextmanager
from datetime import UTC

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Response
from fastapi.middleware.gzip import GZipMiddleware

from .config import Settings
from .forecast import KNMI_ATTRIBUTION, ForecastService, NoForecast
from .frames import PALETTE, THRESHOLDS

SECRET_HEADER = "X-Geenregen-Key"

# Rough bounding box of the KNMI radar forecast grid (Netherlands plus margin).
NL_LAT = (49.3, 55.9)
NL_LON = (0.0, 10.9)


def create_app(
    settings: Settings | None = None, service: ForecastService | None = None, background: bool = True
) -> FastAPI:
    """`background=False` skips the KNMI poller thread (tests)."""
    settings = settings or Settings.from_env()
    service = service or ForecastService(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
        if background:
            service.start()
        yield
        service.stop()

    app = FastAPI(title="Geen regen aub relay", version="0.3.0", docs_url=None, redoc_url=None, lifespan=lifespan)
    app.state.forecast = service
    # The JSON answers shrink to about a fifth; the app's HTTP client unpacks them transparently.
    app.add_middleware(GZipMiddleware, minimum_size=1000)

    def require_secret(key: str | None = Header(default=None, alias=SECRET_HEADER)) -> None:
        if not settings.shared_secret:
            raise HTTPException(status_code=503, detail="RELAY_SHARED_SECRET is not configured")
        if key is None or not hmac.compare_digest(key.encode(), settings.shared_secret.encode()):
            raise HTTPException(status_code=401, detail="Missing or wrong key")

    @app.get("/healthz")
    def healthz() -> dict:
        """Open health check for Docker and Nginx Proxy Manager. Reveals no secrets."""
        radar = service.radar
        return {
            "status": "ok",
            "knmi_key_configured": bool(settings.knmi_api_key),
            "shared_secret_configured": bool(settings.shared_secret),
            "knmi_issued_at": None if radar is None else radar.issued_at.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "last_knmi_error": service.last_knmi_error,
            "last_open_meteo_error": service.last_open_meteo_error,
        }

    @app.get("/v1/rain", dependencies=[Depends(require_secret)])
    def rain(
        response: Response,
        lat: float = Query(ge=NL_LAT[0], le=NL_LAT[1]),
        lon: float = Query(ge=NL_LON[0], le=NL_LON[1]),
    ) -> dict:
        """2-hour nowcast for a point. The response shape is documented in docs/api.md."""
        try:
            answer = service.forecast(round_coord(lat), round_coord(lon))
        except NoForecast as error:
            raise HTTPException(status_code=503, detail=str(error)) from error
        response.headers["Cache-Control"] = f"max-age={answer.max_age}"
        return answer.body

    @app.get("/v1/radar", dependencies=[Depends(require_secret)])
    def radar(response: Response) -> dict:
        """Frames for the animated radar map. The response shape is documented in docs/api.md."""
        try:
            radar, renderer, frames = service.map_frames()
        except NoForecast as error:
            raise HTTPException(status_code=503, detail=str(error)) from error
        response.headers["Cache-Control"] = "max-age=60"
        return {
            "issued_at": _iso(radar.issued_at),
            "bounds": renderer.bounds.as_dict(),
            "width": renderer.width,
            "height": renderer.height,
            "interval_minutes": 5,
            "frames": [{"time": _iso(f.time), "kind": f.kind, "url": f"/v1/radar/{f.id}.png"} for f in frames],
            "legend": [
                {"min_mm_per_hour": low, "color": "#{:02x}{:02x}{:02x}{:02x}".format(*rgba)}
                for low, rgba in zip(THRESHOLDS, PALETTE[1:], strict=True)
            ],
            "attribution": KNMI_ATTRIBUTION,
        }

    @app.get("/v1/radar/{frame_id}.png", dependencies=[Depends(require_secret)])
    def radar_frame(frame_id: str) -> Response:
        frame = service.map_frame(frame_id)
        if frame is None:
            raise HTTPException(status_code=404, detail="Unknown or expired frame")
        # A frame id names one issue and one time, so its picture never changes.
        return Response(frame.png, media_type="image/png", headers={"Cache-Control": "max-age=86400, immutable"})

    return app


def _iso(t) -> str:
    return t.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


def round_coord(value: float) -> float:
    """Round to 0.01 degree (about 1 km) so the relay never handles exact locations."""
    return math.floor(value * 100 + 0.5) / 100
