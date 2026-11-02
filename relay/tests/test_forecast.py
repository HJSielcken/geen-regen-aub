from datetime import timedelta

import httpx
import pytest

from geenregen_relay.config import Settings
from geenregen_relay.forecast import ForecastService, NoForecast, build_body
from geenregen_relay.knmi import KnmiClient
from geenregen_relay.openmeteo import OpenMeteoClient, OpenMeteoError, resample

from .knmi_fixture import ISSUED, grids, hdf5_bytes

SETTINGS = Settings(knmi_api_key="k", shared_secret="s")
DE_BILT = (52.10, 5.18)


class FakeKnmi:
    """Serves the KNMI listing, download URL and file through httpx.MockTransport."""

    def __init__(self, files: dict[str, bytes]):
        self.files = files
        self.current = next(iter(files))
        self.downloads = 0
        self.fail = False

    def handler(self, request: httpx.Request) -> httpx.Response:
        if self.fail:
            return httpx.Response(500, text="down")
        path = request.url.path
        if path.endswith("/files"):
            return httpx.Response(200, json={"files": [{"filename": self.current}]})
        if path.endswith("/url"):
            name = path.split("/")[-2]
            return httpx.Response(200, json={"temporaryDownloadUrl": f"https://download.test/{name}"})
        if request.url.host == "download.test":
            self.downloads += 1
            return httpx.Response(200, content=self.files[path.lstrip("/")])
        return httpx.Response(404)

    def client(self) -> KnmiClient:
        return KnmiClient(SETTINGS, client=httpx.Client(transport=httpx.MockTransport(self.handler)))


class FakeOpenMeteo(OpenMeteoClient):
    def __init__(self, rate: float = 0.0, fail: bool = False):
        self.rate, self.fail, self.calls = rate, fail, 0

    def slots(self, lat, lon):
        self.calls += 1
        if self.fail:
            raise OpenMeteoError("down")
        return [(ISSUED + timedelta(minutes=15 * i), self.rate) for i in range(-2, 12)]


def radar_file(wet_steps: dict[int, int]) -> bytes:
    images = grids()
    service = ForecastService(SETTINGS, knmi=FakeKnmi({"x": hdf5_bytes(images[:1])}).client())
    service.refresh()
    row, col = service.radar.grid.pixel(*DE_BILT)
    for step, raw in wet_steps.items():
        images[step][1][row, col] = raw
    return hdf5_bytes(images)


def make(now_offset=timedelta(minutes=3), files=None, open_meteo=None):
    knmi = FakeKnmi(files or {"RAD_1.h5": radar_file({2: 10, 3: 20})})
    clock = {"now": ISSUED + now_offset}
    service = ForecastService(
        SETTINGS, knmi=knmi.client(), open_meteo=open_meteo or FakeOpenMeteo(), clock=lambda: clock["now"]
    )
    return service, knmi, clock


def test_fresh_knmi_forecast():
    service, _, _ = make()
    assert service.refresh() is True
    answer = service.forecast(*DE_BILT)
    body = answer.body
    assert body["source"] == "knmi"
    assert body["stale"] is False
    assert body["issued_at"] == "2026-11-02T21:00:00Z"
    assert body["generated_at"] == "2026-11-02T21:03:00Z"
    assert len(body["series"]) == 25
    assert body["series"][0] == {"time": "2026-11-02T21:00:00Z", "mm_per_hour": 0.0}
    assert body["series"][2]["mm_per_hour"] == 1.2
    assert body["series"][3]["mm_per_hour"] == 2.4
    assert body["series"][24]["time"] == "2026-11-02T23:00:00Z"
    assert body["summary"] == {
        "raining_now": False,
        "rain_starts_at": "2026-11-02T21:10:00Z",
        "rain_ends_at": "2026-11-02T21:20:00Z",
        "max_mm_per_hour": 2.4,
        "total_mm": 0.3,
    }
    assert body["attribution"] == "Radardata: KNMI, CC BY 4.0"
    assert answer.max_age == 300


def test_each_file_is_downloaded_once():
    service, knmi, _ = make()
    assert service.refresh() is True
    assert service.refresh() is False
    assert knmi.downloads == 1
    knmi.files["RAD_2.h5"] = knmi.files["RAD_1.h5"]
    knmi.current = "RAD_2.h5"
    assert service.refresh() is True
    assert knmi.downloads == 2


def test_failed_refresh_keeps_the_previous_forecast():
    service, knmi, _ = make()
    service.refresh()
    knmi.fail = True
    assert service.refresh() is False
    assert service.forecast(*DE_BILT).body["source"] == "knmi"


def test_old_knmi_forecast_falls_back_to_open_meteo():
    open_meteo = FakeOpenMeteo(rate=2.0)
    service, _, clock = make(open_meteo=open_meteo)
    service.refresh()
    clock["now"] = ISSUED + timedelta(minutes=31)
    body = service.forecast(*DE_BILT).body
    assert body["source"] == "open-meteo"
    assert body["issued_at"] == "2026-11-02T21:30:00Z"
    assert body["attribution"] == "Weather data: Open-Meteo.com, CC BY 4.0"
    assert body["summary"]["raining_now"] is True
    assert len(body["series"]) == 25
    service.forecast(*DE_BILT)
    assert open_meteo.calls == 1  # cached per point


def test_without_any_knmi_file_open_meteo_answers():
    service = ForecastService(Settings(knmi_api_key="", shared_secret="s"), open_meteo=FakeOpenMeteo())
    assert service.refresh() is False
    assert service.forecast(*DE_BILT).body["source"] == "open-meteo"


def test_stale_knmi_when_open_meteo_is_down_too():
    service, _, clock = make(open_meteo=FakeOpenMeteo(fail=True))
    service.refresh()
    clock["now"] = ISSUED + timedelta(minutes=40)
    answer = service.forecast(*DE_BILT)
    assert answer.body["source"] == "knmi"
    assert answer.body["stale"] is True
    assert answer.max_age == 60


def test_nothing_at_all_raises():
    service, _, clock = make(open_meteo=FakeOpenMeteo(fail=True))
    with pytest.raises(NoForecast):
        service.forecast(*DE_BILT)
    service.refresh()
    clock["now"] = ISSUED + timedelta(hours=3)
    with pytest.raises(NoForecast):
        service.forecast(*DE_BILT)


def test_point_outside_radar_coverage_uses_open_meteo():
    service, _, _ = make()
    service.refresh()
    # Inside the API's box but north-east of the radar grid's corner.
    assert service.radar.grid.pixel(55.8, 10.8) is None
    assert service.forecast(55.8, 10.8).body["source"] == "open-meteo"


def test_summary_when_raining_through_the_whole_window():
    body = build_body(
        lat=1, lon=2, source="knmi", issued_at=ISSUED, values=[1.0] * 25, stale=False, attribution="a", now=ISSUED
    )
    assert body["summary"]["raining_now"] is True
    assert body["summary"]["rain_starts_at"] == "2026-11-02T21:00:00Z"
    assert body["summary"]["rain_ends_at"] is None
    assert body["summary"]["total_mm"] == 2.0


def test_summary_when_dry():
    body = build_body(
        lat=1, lon=2, source="knmi", issued_at=ISSUED, values=[0.04] * 25, stale=False, attribution="a", now=ISSUED
    )
    assert body["summary"] == {
        "raining_now": False,
        "rain_starts_at": None,
        "rain_ends_at": None,
        "max_mm_per_hour": 0.0,
        "total_mm": 0.0,
    }


def test_resample_uses_the_slot_each_time_falls_in():
    slots = [(ISSUED, 1.0), (ISSUED + timedelta(minutes=15), 4.0)]
    times = [ISSUED + timedelta(minutes=5 * i) for i in range(5)]
    assert resample(slots, times) == [1.0, 4.0, 4.0, 4.0, 0.0]


def test_open_meteo_client_parses_answer():
    def handler(request):
        assert request.url.params["minutely_15"] == "precipitation"
        return httpx.Response(
            200,
            json={"minutely_15": {"time": ["2026-11-02T21:00", "2026-11-02T21:15"], "precipitation": [0.5, None]}},
        )

    client = OpenMeteoClient("https://om.test/v1/forecast", client=httpx.Client(transport=httpx.MockTransport(handler)))
    assert client.slots(52.1, 5.2) == [(ISSUED, 2.0)]


def test_open_meteo_client_errors():
    client = OpenMeteoClient(
        "https://om.test", client=httpx.Client(transport=httpx.MockTransport(lambda r: httpx.Response(502)))
    )
    with pytest.raises(OpenMeteoError):
        client.slots(52.1, 5.2)
