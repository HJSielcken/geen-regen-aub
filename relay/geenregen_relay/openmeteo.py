"""Fallback source: Open-Meteo's 15-minute precipitation forecast (no key needed).

Docs: https://open-meteo.com/en/docs. A `minutely_15` precipitation value at time T is the amount
in mm that falls in the 15 minutes before T.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import httpx

ATTRIBUTION = "Weather data: Open-Meteo.com, CC BY 4.0"


class OpenMeteoError(RuntimeError):
    pass


class OpenMeteoClient:
    def __init__(self, url: str, client: httpx.Client | None = None):
        self._url = url
        self._client = client or httpx.Client(timeout=10.0)

    def slots(self, lat: float, lon: float) -> list[tuple[datetime, float]]:
        """(end of 15-minute slot, mm/h) pairs around now, oldest first."""
        params = {
            "latitude": lat,
            "longitude": lon,
            "minutely_15": "precipitation",
            "past_minutely_15": 2,
            "forecast_minutely_15": 10,
            "timezone": "GMT",
        }
        try:
            response = self._client.get(self._url, params=params)
        except httpx.HTTPError as error:
            raise OpenMeteoError(f"Open-Meteo request failed: {error}") from error
        if response.is_error:
            raise OpenMeteoError(f"Open-Meteo request failed: HTTP {response.status_code}")
        try:
            block = response.json()["minutely_15"]
            pairs = [
                (datetime.fromisoformat(t).replace(tzinfo=UTC), 4.0 * float(v))
                for t, v in zip(block["time"], block["precipitation"], strict=True)
                if v is not None
            ]
        except (KeyError, TypeError, ValueError) as error:
            raise OpenMeteoError(f"Unexpected Open-Meteo answer: {error}") from error
        if not pairs:
            raise OpenMeteoError("Open-Meteo returned no precipitation values")
        return sorted(pairs)


def resample(slots: list[tuple[datetime, float]], times: list[datetime]) -> list[float]:
    """Value of the 15-minute slot (end - 15 min, end] each time falls in; 0 beyond the data."""
    out = []
    for t in times:
        value = 0.0
        for end, rate in slots:
            if end - timedelta(minutes=15) < t <= end:
                value = rate
                break
        out.append(value)
    return out
