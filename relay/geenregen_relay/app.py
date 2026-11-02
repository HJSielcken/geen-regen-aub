"""HTTP API for the app.

Every /v1 endpoint requires the shared secret in the X-Geenregen-Key header, because the relay
is reachable on the public internet at https://geenregenaub.theharmen.org.
"""

from __future__ import annotations

import hmac
import logging
import math
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Response

from .config import Settings
from .forecast import ForecastService, NoForecast

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

    app = FastAPI(title="Geen regen aub relay", version="0.2.0", docs_url=None, redoc_url=None, lifespan=lifespan)
    app.state.forecast = service

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

    return app


def round_coord(value: float) -> float:
    """Round to 0.01 degree (about 1 km) so the relay never handles exact locations."""
    return math.floor(value * 100 + 0.5) / 100
