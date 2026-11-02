from fastapi.testclient import TestClient

from geenregen_relay.app import SECRET_HEADER, create_app, round_coord
from geenregen_relay.config import Settings


def client(secret: str = "s3cret", key: str = "knmi") -> TestClient:
    return TestClient(create_app(Settings(knmi_api_key=key, shared_secret=secret)))


def test_healthz_is_open_and_reveals_no_secrets():
    response = client().get("/healthz")
    assert response.status_code == 200
    body = response.json()
    assert body == {"status": "ok", "knmi_key_configured": True, "shared_secret_configured": True}
    assert "s3cret" not in response.text


def test_rain_requires_secret():
    assert client().get("/v1/rain", params={"lat": 52.1, "lon": 5.1}).status_code == 401


def test_rain_rejects_wrong_secret():
    response = client().get("/v1/rain", params={"lat": 52.1, "lon": 5.1}, headers={SECRET_HEADER: "nope"})
    assert response.status_code == 401


def test_rain_refuses_when_no_secret_configured():
    response = client(secret="").get("/v1/rain", params={"lat": 52.1, "lon": 5.1}, headers={SECRET_HEADER: ""})
    assert response.status_code == 503


def test_rain_with_secret_reaches_endpoint():
    response = client().get("/v1/rain", params={"lat": 52.0907, "lon": 5.1214}, headers={SECRET_HEADER: "s3cret"})
    assert response.status_code == 501
    assert response.json()["detail"]["point"] == {"lat": 52.09, "lon": 5.12}


def test_rain_rejects_points_outside_the_netherlands():
    response = client().get("/v1/rain", params={"lat": 48.85, "lon": 2.35}, headers={SECRET_HEADER: "s3cret"})
    assert response.status_code == 422


def test_round_coord():
    assert round_coord(52.12345) == 52.12
    assert round_coord(4.995) == 5.0
