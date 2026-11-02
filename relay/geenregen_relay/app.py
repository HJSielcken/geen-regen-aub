"""HTTP API for the app.

Every /v1 endpoint requires the shared secret in the X-Geenregen-Key header, because the relay
is reachable on the public internet at https://geenregenaub.theharmen.org.
"""

from __future__ import annotations

import hmac
import math

from fastapi import Depends, FastAPI, Header, HTTPException, Query

from .config import Settings

SECRET_HEADER = "X-Geenregen-Key"
ATTRIBUTION = "Radardata: KNMI, CC BY 4.0"

# Rough bounding box of the KNMI radar forecast grid (Netherlands plus margin).
NL_LAT = (49.3, 55.9)
NL_LON = (0.0, 10.9)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    app = FastAPI(title="Geen regen aub relay", version="0.1.0", docs_url=None, redoc_url=None)

    def require_secret(key: str | None = Header(default=None, alias=SECRET_HEADER)) -> None:
        if not settings.shared_secret:
            raise HTTPException(status_code=503, detail="RELAY_SHARED_SECRET is not configured")
        if key is None or not hmac.compare_digest(key.encode(), settings.shared_secret.encode()):
            raise HTTPException(status_code=401, detail="Missing or wrong key")

    @app.get("/healthz")
    def healthz() -> dict:
        """Open health check for Docker and Nginx Proxy Manager. Reveals no secrets."""
        return {
            "status": "ok",
            "knmi_key_configured": bool(settings.knmi_api_key),
            "shared_secret_configured": bool(settings.shared_secret),
        }

    @app.get("/v1/rain", dependencies=[Depends(require_secret)])
    def rain(
        lat: float = Query(ge=NL_LAT[0], le=NL_LAT[1]),
        lon: float = Query(ge=NL_LON[0], le=NL_LON[1]),
    ) -> dict:
        """2-hour nowcast for a point. Decoding the KNMI files arrives in milestone 2."""
        rounded = {"lat": round_coord(lat), "lon": round_coord(lon)}
        raise HTTPException(
            status_code=501,
            detail={"message": "Not implemented yet (milestone 2)", "point": rounded, "attribution": ATTRIBUTION},
        )

    return app


def round_coord(value: float) -> float:
    """Round to 0.01 degree (about 1 km) so the relay never handles exact locations."""
    return math.floor(value * 100 + 0.5) / 100
