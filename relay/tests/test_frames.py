import struct
import zlib
from datetime import timedelta

import numpy as np

from geenregen_relay.forecast import ForecastService
from geenregen_relay.frames import PALETTE, FrameRenderer, encode_png

from .knmi_fixture import ISSUED, grids, hdf5_bytes
from .test_forecast import DE_BILT, SETTINGS, FakeKnmi, make, radar_file


def _decode_png(data: bytes) -> tuple[int, int, np.ndarray]:
    """Minimal reader for the palette PNGs we write (filter 0 rows)."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, width, height = 8, b"", 0, 0
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos : pos + 4])
        kind = data[pos + 4 : pos + 8]
        body = data[pos + 8 : pos + 8 + length]
        crc = struct.unpack(">I", data[pos + 8 + length : pos + 12 + length])[0]
        assert crc == zlib.crc32(kind + body) & 0xFFFFFFFF
        if kind == b"IHDR":
            width, height = struct.unpack(">II", body[:8])
            assert body[8:10] == bytes([8, 3])
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    rows = np.frombuffer(zlib.decompress(idat), dtype=np.uint8).reshape(height, width + 1)
    assert (rows[:, 0] == 0).all()
    return width, height, rows[:, 1:]


def test_png_round_trips():
    classes = np.array([[0, 1, 2], [8, 0, 3]], dtype=np.uint8)
    width, height, pixels = _decode_png(encode_png(classes))
    assert (width, height) == (3, 2)
    assert (pixels == classes).all()
    assert len(PALETTE) == 9


def test_frames_cover_the_netherlands_and_put_rain_in_the_right_place():
    service, _, _ = make()
    service.refresh()
    row, col = service.radar.grid.pixel(*DE_BILT)
    images = grids(steps=3)
    images[2][1][row - 2 : row + 3, col - 2 : col + 3] = 10  # a 5 km shower around De Bilt
    service = ForecastService(SETTINGS, knmi=FakeKnmi({"x": hdf5_bytes(images)}).client())
    service.refresh()
    radar = service.radar
    renderer = FrameRenderer(radar.grid)
    b = renderer.bounds
    assert b.south < 50.7 and b.north > 53.6 and b.west < 3.3 and b.east > 7.3

    # Raw 10 = 0.1 mm per 5 min = 1.2 mm/h; it shows up around De Bilt only.
    classes = renderer.classes(radar.steps[2])
    wet = np.argwhere(classes > 0)
    assert len(wet) >= 1
    row, col = wet.mean(axis=0)
    lon = b.west + (col + 0.5) / renderer.width * (b.east - b.west)
    assert abs(lon - DE_BILT[1]) < 0.1
    # Latitude via Web Mercator.
    merc = np.log(np.tan(np.pi / 4 + np.radians([b.south, b.north]) / 2))
    y = merc[1] - (row + 0.5) / renderer.height * (merc[1] - merc[0])
    lat = np.degrees(2 * np.arctan(np.exp(y)) - np.pi / 2)
    assert abs(lat - DE_BILT[0]) < 0.1
    assert renderer.classes(radar.steps[0]).max() == 0


def test_service_keeps_an_hour_of_observed_frames():
    files = {}
    for i in range(15):
        issued = ISSUED + timedelta(minutes=5 * i)
        files[f"RAD_{i}.h5"] = hdf5_bytes(grids(steps=3, issued=issued))
    knmi = FakeKnmi(files)
    clock = {"now": ISSUED}
    service = ForecastService(SETTINGS, knmi=knmi.client(), clock=lambda: clock["now"])
    for i in range(15):
        knmi.current = f"RAD_{i}.h5"
        clock["now"] = ISSUED + timedelta(minutes=5 * i + 1)
        assert service.refresh() is True

    radar, _, frames = service.map_frames()
    observed = [f for f in frames if f.kind == "observed"]
    forecast = [f for f in frames if f.kind == "forecast"]
    # The past hour (12 earlier files) plus the current +0, then the current file's forecast steps.
    assert len(observed) == 13
    assert observed[-1].time == radar.issued_at
    assert len(forecast) == 2
    times = [f.time for f in frames]
    assert times == sorted(times)
    assert service.map_frame(frames[0].id).png.startswith(b"\x89PNG")
    assert service.map_frame("nope") is None


def test_frames_need_a_forecast():
    service = ForecastService(SETTINGS, knmi=FakeKnmi({"x": radar_file({})}).client())
    try:
        service.map_frames()
    except Exception as error:
        assert "No KNMI radar frames" in str(error)
    else:
        raise AssertionError("expected NoForecast")
