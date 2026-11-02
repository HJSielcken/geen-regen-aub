"""Builds small files in the KNMI radar_forecast HDF5 layout, for tests."""

from __future__ import annotations

import io
from datetime import UTC, datetime, timedelta

import h5py
import numpy as np

# As in current KNMI files: ellipsoid in metres, projected coordinates in km.
PROJ4 = "+proj=stere +lat_0=90 +lon_0=0 +lat_ts=60 +a=6378137 +b=6356752 +x_0=0 +y_0=0 +units=km"
# Older KNMI files gave the ellipsoid itself in km.
LEGACY_PROJ4 = "+proj=stere +lat_0=90 +lon_0=0 +lat_ts=60 +a=6378.137 +b=6356.752 +x_0=0 +y_0=0"
ROWS, COLUMNS = 765, 700
MISSING = 65535
ISSUED = datetime(2026, 11, 2, 21, 0, tzinfo=UTC)


def knmi_time(t: datetime) -> bytes:
    return t.strftime("%d-%b-%Y;%H:%M:%S.000").upper().encode()


def _write(h5: h5py.File, images: dict[int, tuple[datetime, np.ndarray]]) -> None:
    geo = h5.create_group("geographic")
    geo.attrs["geo_number_columns"] = np.int32(COLUMNS)
    geo.attrs["geo_number_rows"] = np.int32(ROWS)
    geo.attrs["geo_pixel_size_x"] = np.float32(1.0000035)
    geo.attrs["geo_pixel_size_y"] = np.float32(-1.0000048)
    geo.attrs["geo_column_offset"] = np.float32(0.0)
    geo.attrs["geo_row_offset"] = np.float32(3649.982)
    proj = geo.create_group("map_projection")
    proj.attrs["projection_proj4_params"] = PROJ4.encode()
    for index, (valid, data) in images.items():
        group = h5.create_group(f"image{index}")
        group.attrs["image_datetime_valid"] = knmi_time(valid)
        group.attrs["image_geo_parameter"] = b"PRECIP_[MM]"
        group.create_dataset("image_data", data=data, compression="gzip")
        cal = group.create_group("calibration")
        cal.attrs["calibration_formulas"] = b"GEO = 0.010000 * PV + 0.000000"
        cal.attrs["calibration_missing_data"] = np.int32(MISSING)
        cal.attrs["calibration_out_of_image"] = np.int32(65534)


def grids(steps: int = 25, issued: datetime = ISSUED) -> list[tuple[datetime, np.ndarray]]:
    return [(issued + timedelta(minutes=5 * i), np.zeros((ROWS, COLUMNS), dtype=np.uint16)) for i in range(steps)]


def hdf5_bytes(images: list[tuple[datetime, np.ndarray]]) -> bytes:
    buffer = io.BytesIO()
    with h5py.File(buffer, "w") as h5:
        _write(h5, {i + 1: image for i, image in enumerate(images)})
    return buffer.getvalue()


def hdf5_single(image: tuple[datetime, np.ndarray]) -> bytes:
    buffer = io.BytesIO()
    with h5py.File(buffer, "w") as h5:
        _write(h5, {1: image})
    return buffer.getvalue()
