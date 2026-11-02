"""Decode a KNMI radar_forecast (2.0) file and read the 2-hour series for one point.

A file is KNMI HDF5: `/image1` .. `/image25`, each with a 765 x 700 `image_data` grid of 5-minute
precipitation amounts on a polar stereographic 1 km grid. The grid geometry, the projection and the
calibration formula are read from the file's own metadata, so nothing about the grid is hard-coded.
Older KNMI deliveries packed one HDF5 file per time step in a tar; those are accepted too.
"""

from __future__ import annotations

import io
import re
import tarfile
from dataclasses import dataclass
from datetime import UTC, datetime

import h5py
import numpy as np
from pyproj import Proj

_CALIBRATION = re.compile(r"GEO\s*=\s*([-+0-9.eE]+)\s*\*\s*PV\s*\+\s*([-+0-9.eE]+)")


class RadarFormatError(ValueError):
    pass


def _text(value) -> str:
    """HDF5 attributes come back as bytes, numpy bytes or one-element arrays."""
    if isinstance(value, np.ndarray):
        value = value.flat[0] if value.size else ""
    if isinstance(value, bytes | np.bytes_):
        value = value.decode("ascii", errors="replace")
    return str(value).strip()


def _number(value) -> float:
    return float(np.asarray(value).flat[0])


def parse_knmi_time(value) -> datetime:
    """'02-NOV-2026;21:00:00.000' -> aware UTC datetime."""
    text = _text(value)
    for fmt in ("%d-%b-%Y;%H:%M:%S.%f", "%d-%b-%Y;%H:%M:%S"):
        try:
            return datetime.strptime(text.title(), fmt).replace(tzinfo=UTC)
        except ValueError:
            continue
    raise RadarFormatError(f"Unknown KNMI time format: {text!r}")


@dataclass(frozen=True)
class Grid:
    """Maps lat/lon to a pixel of the KNMI grid."""

    proj4: str
    columns: int
    rows: int
    pixel_size_x: float
    pixel_size_y: float
    column_offset: float
    row_offset: float

    def __post_init__(self):
        # KNMI gives the ellipsoid in km (+a=6378.137), so projected coordinates are km, like the
        # pixel sizes. Proj is used directly: a datum transformation would refuse a "planet" that small.
        object.__setattr__(self, "_proj", Proj(self.proj4))
        axis = re.search(r"\+a=([0-9.eE+-]+)", self.proj4)
        object.__setattr__(self, "_to_km", 0.001 if axis and float(axis.group(1)) > 100_000 else 1.0)

    def pixel(self, lat: float, lon: float) -> tuple[int, int] | None:
        """(row, column) of the pixel holding this point, or None if it is outside the grid."""
        x, y = self._proj(lon, lat)
        x, y = x * self._to_km, y * self._to_km
        col = int(np.floor(x / self.pixel_size_x - self.column_offset))
        row = int(np.floor(y / self.pixel_size_y - self.row_offset))
        if 0 <= row < self.rows and 0 <= col < self.columns:
            return row, col
        return None


@dataclass(frozen=True)
class Step:
    time: datetime
    raw: np.ndarray  # uint16 (rows, columns), as stored in the file
    scale: float
    offset: float
    missing: frozenset[int]
    per_hour: float  # factor to turn a calibrated value into mm/h


@dataclass(frozen=True)
class RadarForecast:
    filename: str
    grid: Grid
    steps: tuple[Step, ...]

    @property
    def issued_at(self) -> datetime:
        return self.steps[0].time

    def series_at(self, lat: float, lon: float) -> list[tuple[datetime, float | None]] | None:
        """mm/h per time step at the point; None for a missing pixel; None overall if off the grid."""
        pixel = self.grid.pixel(lat, lon)
        if pixel is None:
            return None
        row, col = pixel
        out: list[tuple[datetime, float | None]] = []
        for step in self.steps:
            value = int(step.raw[row, col])
            if value in step.missing:
                out.append((step.time, None))
            else:
                out.append((step.time, max(0.0, (step.scale * value + step.offset) * step.per_hour)))
        return out


def _read_grid(h5: h5py.File) -> Grid:
    try:
        geo = h5["geographic"].attrs
        proj = h5["geographic/map_projection"].attrs
        return Grid(
            proj4=_text(proj["projection_proj4_params"]),
            columns=int(_number(geo["geo_number_columns"])),
            rows=int(_number(geo["geo_number_rows"])),
            pixel_size_x=_number(geo["geo_pixel_size_x"]),
            pixel_size_y=_number(geo["geo_pixel_size_y"]),
            column_offset=_number(geo["geo_column_offset"]),
            row_offset=_number(geo["geo_row_offset"]),
        )
    except KeyError as error:
        raise RadarFormatError(f"Missing geographic metadata: {error}") from error


def _read_steps(h5: h5py.File) -> list[Step]:
    names = sorted((n for n in h5 if re.fullmatch(r"image\d+", n)), key=lambda n: int(n[5:]))
    steps = []
    for name in names:
        group = h5[name]
        cal = group["calibration"].attrs if "calibration" in group else {}
        match = _CALIBRATION.search(_text(cal.get("calibration_formulas", "GEO = 1.0 * PV + 0.0")))
        if not match:
            raise RadarFormatError(f"Cannot read calibration formula of {name}")
        missing = set()
        for key in ("calibration_missing_data", "calibration_out_of_image"):
            if key in cal:
                missing.add(int(_number(cal[key])))
        parameter = _text(group.attrs.get("image_geo_parameter", "")).upper()
        steps.append(
            Step(
                time=parse_knmi_time(group.attrs["image_datetime_valid"]),
                raw=np.asarray(group["image_data"][()]),
                scale=float(match.group(1)),
                offset=float(match.group(2)),
                missing=frozenset(missing),
                # Amounts per 5 minutes unless the file says it already holds a rate.
                per_hour=1.0 if "/H" in parameter else 12.0,
            )
        )
    return steps


def decode(filename: str, content: bytes) -> RadarForecast:
    """Decode a downloaded radar_forecast file (HDF5, or a tar of HDF5 files)."""
    blobs: list[bytes]
    if content[:8] == b"\x89HDF\r\n\x1a\n":
        blobs = [content]
    else:
        try:
            with tarfile.open(fileobj=io.BytesIO(content)) as tar:
                members = sorted((m for m in tar.getmembers() if m.isfile()), key=lambda m: m.name)
                blobs = [tar.extractfile(m).read() for m in members]
        except tarfile.TarError as error:
            raise RadarFormatError(f"{filename} is neither HDF5 nor tar") from error

    grid: Grid | None = None
    steps: list[Step] = []
    for blob in blobs:
        with h5py.File(io.BytesIO(blob), "r") as h5:
            grid = grid or _read_grid(h5)
            steps.extend(_read_steps(h5))
    if grid is None or not steps:
        raise RadarFormatError(f"{filename} holds no images")
    steps.sort(key=lambda s: s.time)
    return RadarForecast(filename=filename, grid=grid, steps=tuple(steps))
