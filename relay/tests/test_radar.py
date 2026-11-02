import io
import tarfile
from datetime import timedelta

import pytest

from geenregen_relay.radar import Grid, RadarFormatError, decode, km_per_unit, parse_knmi_time

from .knmi_fixture import ISSUED, LEGACY_PROJ4, MISSING, PROJ4, grids, hdf5_bytes, hdf5_single

DE_BILT = (52.10, 5.18)


def test_corners_of_the_grid_map_to_its_corner_pixels():
    forecast = decode("f.h5", hdf5_bytes(grids(1)))
    # geo_product_corners of the real KNMI grid
    assert forecast.grid.pixel(55.97, 0.01) == (0, 0)
    assert forecast.grid.pixel(49.37, 0.01)[0] == 764
    assert forecast.grid.pixel(55.38, 10.84)[1] == 699


def test_points_off_the_grid_give_none():
    forecast = decode("f.h5", hdf5_bytes(grids(1)))
    assert forecast.grid.pixel(48.0, 2.0) is None
    assert forecast.series_at(48.0, 2.0) is None


def test_series_at_converts_5_minute_amounts_to_mm_per_hour():
    images = grids()
    row, col = decode("f.h5", hdf5_bytes(images[:1])).grid.pixel(*DE_BILT)
    images[0][1][row, col] = 10  # 0.10 mm in 5 minutes -> 1.2 mm/h
    images[3][1][row, col] = 250  # 2.5 mm -> 30 mm/h
    images[4][1][row, col] = MISSING
    images[5][1][row + 3, col] = 999  # a neighbouring pixel, must not leak in
    forecast = decode("f.h5", hdf5_bytes(images))

    series = forecast.series_at(*DE_BILT)
    assert len(series) == 25
    assert forecast.issued_at == ISSUED
    assert series[0] == (ISSUED, pytest.approx(1.2))
    assert series[3][1] == pytest.approx(30.0)
    assert series[4][1] is None
    assert series[5][1] == 0.0
    assert series[24][0] == ISSUED + timedelta(minutes=120)


def test_de_bilt_lies_in_the_middle_of_the_grid():
    row, col = decode("f.h5", hdf5_bytes(grids(1))).grid.pixel(*DE_BILT)
    assert 300 < row < 500 and 300 < col < 450


def test_km_per_unit_reads_units_and_ellipsoid_size():
    assert km_per_unit(PROJ4) == 1.0  # +units=km, even though +a is in metres
    assert km_per_unit(LEGACY_PROJ4) == 1.0  # ellipsoid given in km
    assert km_per_unit("+proj=stere +lat_0=90 +a=6378137 +b=6356752") == 0.001
    assert km_per_unit("+proj=stere +lat_0=90 +a=6378137 +units=m") == 0.001


@pytest.mark.parametrize("proj4", [PROJ4, LEGACY_PROJ4])
def test_both_projection_spellings_put_de_bilt_on_the_same_pixel(proj4):
    grid = Grid(proj4, 700, 765, 1.0, -1.0, 0.0, 3650.0)
    assert grid.pixel(*DE_BILT) == (427, 369)  # as read from a real RAD_NL25_RAC_FM file


def test_tar_of_single_step_files_is_accepted():
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w") as tar:
        for i, image in enumerate(reversed(grids(3))):
            blob = hdf5_single(image)
            info = tarfile.TarInfo(f"step{i}.h5")
            info.size = len(blob)
            tar.addfile(info, io.BytesIO(blob))
    forecast = decode("f.tar", buffer.getvalue())
    assert [s.time for s in forecast.steps] == [ISSUED + timedelta(minutes=5 * i) for i in range(3)]


def test_garbage_is_refused():
    with pytest.raises(RadarFormatError):
        decode("f.h5", b"not a radar file")


def test_parse_knmi_time():
    assert parse_knmi_time(b"02-NOV-2026;21:00:00.000") == ISSUED
