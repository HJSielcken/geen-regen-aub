from datetime import timedelta

from fastapi.testclient import TestClient

from geenregen_relay.app import SECRET_HEADER, create_app, round_coord
from geenregen_relay.config import Settings
from geenregen_relay.forecast import ForecastService

from .knmi_fixture import ISSUED
from .test_forecast import FakeOpenMeteo


def client(secret: str = "s3cret", key: str = "knmi", open_meteo=None) -> TestClient:
    settings = Settings(knmi_api_key=key, shared_secret=secret)
    service = ForecastService(
        settings, open_meteo=open_meteo or FakeOpenMeteo(), clock=lambda: ISSUED + timedelta(minutes=2)
    )
    return TestClient(create_app(settings, service=service, background=False))


def test_healthz_is_open_and_reveals_no_secrets():
    response = client().get("/healthz")
    assert response.status_code == 200
    body = response.json()
    assert body == {
        "status": "ok",
        "knmi_key_configured": True,
        "shared_secret_configured": True,
        "knmi_issued_at": None,
        "last_knmi_error": None,
        "last_open_meteo_error": None,
    }
    assert "s3cret" not in response.text


def test_rain_requires_secret():
    assert client().get("/v1/rain", params={"lat": 52.1, "lon": 5.1}).status_code == 401


def test_rain_rejects_wrong_secret():
    response = client().get("/v1/rain", params={"lat": 52.1, "lon": 5.1}, headers={SECRET_HEADER: "nope"})
    assert response.status_code == 401


def test_rain_refuses_when_no_secret_configured():
    response = client(secret="").get("/v1/rain", params={"lat": 52.1, "lon": 5.1}, headers={SECRET_HEADER: ""})
    assert response.status_code == 503


def test_rain_answers_with_the_contract_shape():
    response = client().get("/v1/rain", params={"lat": 52.0907, "lon": 5.1214}, headers={SECRET_HEADER: "s3cret"})
    assert response.status_code == 200
    assert response.headers["Cache-Control"] == "max-age=60"
    body = response.json()
    assert body["point"] == {"lat": 52.09, "lon": 5.12}
    assert set(body) == {
        "point",
        "source",
        "issued_at",
        "generated_at",
        "interval_minutes",
        "unit",
        "series",
        "summary",
        "stale",
        "attribution",
    }
    assert body["interval_minutes"] == 5 and body["unit"] == "mm/h"
    assert len(body["series"]) == 25
    assert set(body["summary"]) == {"raining_now", "rain_starts_at", "rain_ends_at", "max_mm_per_hour", "total_mm"}


def test_rain_503_when_no_source_answers():
    response = client(open_meteo=FakeOpenMeteo(fail=True)).get(
        "/v1/rain", params={"lat": 52.1, "lon": 5.1}, headers={SECRET_HEADER: "s3cret"}
    )
    assert response.status_code == 503


def test_rain_rejects_points_outside_the_netherlands():
    response = client().get("/v1/rain", params={"lat": 48.85, "lon": 2.35}, headers={SECRET_HEADER: "s3cret"})
    assert response.status_code == 422


def test_round_coord():
    assert round_coord(52.12345) == 52.12
    assert round_coord(4.995) == 5.0


def test_healthz_shows_why_sources_fail():
    c = client(key="", open_meteo=FakeOpenMeteo(fail=True))
    assert c.get("/v1/rain", params={"lat": 52.1, "lon": 5.1}, headers={SECRET_HEADER: "s3cret"}).status_code == 503
    body = c.get("/healthz").json()
    assert body["last_knmi_error"] == "KNMI_API_KEY is not set"
    assert body["last_open_meteo_error"].endswith("down")


def test_radar_requires_secret():
    assert client().get("/v1/radar").status_code == 401


def test_radar_unavailable_before_the_first_file():
    response = client().get("/v1/radar", headers={SECRET_HEADER: "s3cret"})
    assert response.status_code == 503
