"""Minimal client for the KNMI Open Data API.

Docs: https://developer.dataplatform.knmi.nl/open-data-api
Every request carries the API key in the Authorization header.
"""

from __future__ import annotations

from dataclasses import dataclass

import httpx

from .config import Settings


class KnmiError(RuntimeError):
    pass


@dataclass(frozen=True)
class KnmiFile:
    filename: str
    size: int | None
    created: str | None


class KnmiClient:
    def __init__(self, settings: Settings, client: httpx.Client | None = None):
        if not settings.knmi_api_key:
            raise KnmiError("KNMI_API_KEY is not set")
        self._settings = settings
        self._client = client or httpx.Client(timeout=20.0)

    @property
    def _dataset_url(self) -> str:
        s = self._settings
        return f"{s.knmi_base_url}/datasets/{s.knmi_dataset}/versions/{s.knmi_dataset_version}"

    def _get(self, url: str, params: dict | None = None) -> dict:
        response = self._client.get(url, params=params, headers={"Authorization": self._settings.knmi_api_key})
        if response.status_code in (401, 403):
            raise KnmiError(f"KNMI rejected the API key (HTTP {response.status_code})")
        if response.status_code == 429:
            raise KnmiError("KNMI rate limit reached (HTTP 429)")
        if response.is_error:
            raise KnmiError(f"KNMI request failed: HTTP {response.status_code} {response.text[:200]}")
        return response.json()

    def latest_files(self, count: int = 1) -> list[KnmiFile]:
        """Newest files of the configured dataset, newest first."""
        data = self._get(
            f"{self._dataset_url}/files",
            params={"maxKeys": count, "orderBy": "created", "sorting": "desc"},
        )
        return [
            KnmiFile(filename=f["filename"], size=f.get("size"), created=f.get("created"))
            for f in data.get("files", [])
        ]

    def download_url(self, filename: str) -> str:
        """A short-lived URL to download one file."""
        data = self._get(f"{self._dataset_url}/files/{filename}/url")
        return data["temporaryDownloadUrl"]
