"""Radar map frames: each KNMI grid step drawn as a transparent PNG for the app's map.

The KNMI grid is polar stereographic; map apps draw an image stretched linearly between four
corners in Web Mercator. So every frame is resampled once onto a Web Mercator raster that covers the
whole grid, and the app places it between `bounds`. The lookup from output pixel to grid pixel is
computed once per grid and reused for every frame.

PNGs are palette images (one byte per pixel, transparent where dry), written with zlib directly so
no imaging library is needed.
"""

from __future__ import annotations

import struct
import zlib
from dataclasses import dataclass

import numpy as np
from pyproj import Proj

from .radar import Grid, Step, km_per_unit

EARTH_RADIUS = 6378137.0

# Upper edges (mm/h) of each colour class; class 0 is dry and fully transparent.
THRESHOLDS = (0.1, 0.5, 1.0, 2.5, 5.0, 10.0, 20.0, 50.0)
# RGBA per class, from drizzle (soft blue) to cloudburst (magenta). Matches the app's rain blues.
PALETTE = (
    (0, 0, 0, 0),
    (141, 184, 245, 150),
    (110, 160, 240, 175),
    (61, 127, 224, 195),
    (35, 95, 190, 210),
    (22, 64, 143, 225),
    (110, 40, 170, 235),
    (200, 30, 140, 245),
    (255, 120, 40, 250),
)


@dataclass(frozen=True)
class Bounds:
    north: float
    south: float
    east: float
    west: float

    def as_dict(self) -> dict:
        return {"north": self.north, "south": self.south, "east": self.east, "west": self.west}


def _mercator(lon: np.ndarray, lat: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    x = np.radians(lon) * EARTH_RADIUS
    y = np.log(np.tan(np.pi / 4 + np.radians(lat) / 2)) * EARTH_RADIUS
    return x, y


def _inverse_mercator(x: np.ndarray, y: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    lon = np.degrees(x / EARTH_RADIUS)
    lat = np.degrees(2 * np.arctan(np.exp(y / EARTH_RADIUS)) - np.pi / 2)
    return lon, lat


class FrameRenderer:
    """Resamples grid steps of one [Grid] to Web Mercator PNGs."""

    def __init__(self, grid: Grid, width: int | None = None):
        proj = Proj(grid.proj4)
        axis = km_per_unit(grid.proj4)

        # The grid's outline in lon/lat, sampled along all four edges.
        cols = np.arange(grid.columns + 1, dtype=float)
        rows = np.arange(grid.rows + 1, dtype=float)
        edge_c = np.concatenate([cols, cols, np.zeros_like(rows), np.full_like(rows, grid.columns)])
        edge_r = np.concatenate([np.zeros_like(cols), np.full_like(cols, grid.rows), rows, rows])
        ex = (edge_c + grid.column_offset) * grid.pixel_size_x / axis
        ey = (edge_r + grid.row_offset) * grid.pixel_size_y / axis
        lon, lat = proj(ex, ey, inverse=True)
        self.bounds = Bounds(
            north=round(float(np.max(lat)), 5),
            south=round(float(np.min(lat)), 5),
            east=round(float(np.max(lon)), 5),
            west=round(float(np.min(lon)), 5),
        )

        # Output raster in Web Mercator, about as many pixels wide as the grid.
        mx0, my0 = _mercator(np.array([self.bounds.west]), np.array([self.bounds.south]))
        mx1, my1 = _mercator(np.array([self.bounds.east]), np.array([self.bounds.north]))
        self.width = width or grid.columns
        self.height = max(1, int(round(self.width * float(my1[0] - my0[0]) / float(mx1[0] - mx0[0]))))
        xs = mx0[0] + (np.arange(self.width) + 0.5) * (mx1[0] - mx0[0]) / self.width
        ys = my1[0] - (np.arange(self.height) + 0.5) * (my1[0] - my0[0]) / self.height
        gx, gy = np.meshgrid(xs, ys)
        plon, plat = _inverse_mercator(gx, gy)
        px, py = proj(plon, plat)
        col = np.floor(px * axis / grid.pixel_size_x - grid.column_offset).astype(np.int64)
        row = np.floor(py * axis / grid.pixel_size_y - grid.row_offset).astype(np.int64)
        self._inside = (row >= 0) & (row < grid.rows) & (col >= 0) & (col < grid.columns)
        self._row = np.where(self._inside, row, 0)
        self._col = np.where(self._inside, col, 0)

    def classes(self, step: Step) -> np.ndarray:
        """Colour class per output pixel (uint8, 0 = dry or no data)."""
        raw = step.raw[self._row, self._col]
        mm = np.maximum(0.0, (step.scale * raw.astype(np.float64) + step.offset) * step.per_hour)
        missing = np.isin(raw, list(step.missing)) if step.missing else np.zeros(raw.shape, dtype=bool)
        out = np.digitize(mm, THRESHOLDS).astype(np.uint8)
        out[~self._inside | missing] = 0
        return out

    def png(self, step: Step) -> bytes:
        return encode_png(self.classes(step))


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def encode_png(classes: np.ndarray) -> bytes:
    """Palette PNG of a 2-D array of colour classes."""
    height, width = classes.shape
    header = struct.pack(">IIBBBBB", width, height, 8, 3, 0, 0, 0)
    plte = bytes(c for rgba in PALETTE for c in rgba[:3])
    trns = bytes(rgba[3] for rgba in PALETTE)
    rows = np.zeros((height, width + 1), dtype=np.uint8)  # filter byte 0 at the start of each row
    rows[:, 1:] = classes
    return b"".join(
        [
            b"\x89PNG\r\n\x1a\n",
            _chunk(b"IHDR", header),
            _chunk(b"PLTE", plte),
            _chunk(b"tRNS", trns),
            _chunk(b"IDAT", zlib.compress(rows.tobytes(), 6)),
            _chunk(b"IEND", b""),
        ]
    )
