"""Settings, read from environment variables (see .env.example)."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    knmi_api_key: str
    shared_secret: str
    knmi_dataset: str = "radar_forecast"
    knmi_dataset_version: str = "2.0"
    knmi_base_url: str = "https://api.dataplatform.knmi.nl/open-data/v1"
    open_meteo_url: str = "https://api.open-meteo.com/v1/forecast"
    # How often the relay asks KNMI whether a new radar file exists.
    poll_seconds: int = 60

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> Settings:
        env = dict(os.environ) if env is None else env
        return cls(
            knmi_api_key=env.get("KNMI_API_KEY", "").strip(),
            shared_secret=env.get("RELAY_SHARED_SECRET", "").strip(),
            knmi_dataset=env.get("KNMI_DATASET", cls.knmi_dataset),
            knmi_dataset_version=env.get("KNMI_DATASET_VERSION", cls.knmi_dataset_version),
            knmi_base_url=env.get("KNMI_BASE_URL", cls.knmi_base_url).rstrip("/"),
            open_meteo_url=env.get("OPEN_METEO_URL", cls.open_meteo_url),
            poll_seconds=max(15, int(env.get("KNMI_POLL_SECONDS", cls.poll_seconds))),
        )
