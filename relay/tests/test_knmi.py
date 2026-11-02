import httpx
import pytest

from geenregen_relay.config import Settings
from geenregen_relay.knmi import KnmiClient, KnmiError

SETTINGS = Settings(knmi_api_key="test-key", shared_secret="x")


def make_client(handler) -> KnmiClient:
    return KnmiClient(SETTINGS, client=httpx.Client(transport=httpx.MockTransport(handler)))


def test_latest_files_sends_key_and_parses_response():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["auth"] = request.headers["Authorization"]
        seen["path"] = request.url.path
        seen["params"] = dict(request.url.params)
        return httpx.Response(
            200, json={"files": [{"filename": "RAD_NL25_PCP_FM_202611022100.h5", "size": 1, "created": "t"}]}
        )

    files = make_client(handler).latest_files()
    assert seen["auth"] == "test-key"
    assert seen["path"] == "/open-data/v1/datasets/radar_forecast/versions/2.0/files"
    assert seen["params"] == {"maxKeys": "1", "orderBy": "created", "sorting": "desc"}
    assert files[0].filename == "RAD_NL25_PCP_FM_202611022100.h5"


def test_rejected_key_gives_clear_error():
    client = make_client(lambda request: httpx.Response(403, json={}))
    with pytest.raises(KnmiError, match="rejected the API key"):
        client.latest_files()


def test_missing_key_fails_fast():
    with pytest.raises(KnmiError, match="KNMI_API_KEY"):
        KnmiClient(Settings(knmi_api_key="", shared_secret="x"))


def test_settings_from_env():
    s = Settings.from_env({"KNMI_API_KEY": " k ", "RELAY_SHARED_SECRET": "s", "KNMI_BASE_URL": "http://x/"})
    assert (s.knmi_api_key, s.shared_secret, s.knmi_base_url) == ("k", "s", "http://x")
    assert (s.knmi_dataset, s.knmi_dataset_version) == ("radar_forecast", "2.0")
