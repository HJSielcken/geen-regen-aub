"""Check that the KNMI API key works: `python -m geenregen_relay.check_knmi`.

Reads KNMI_API_KEY from the environment, lists the newest radar forecast files, then downloads and
decodes the newest one and prints the 2-hour series for De Bilt.
"""

from __future__ import annotations

import sys

from .config import Settings
from .knmi import KnmiClient, KnmiError
from .radar import RadarFormatError, decode


def main() -> int:
    settings = Settings.from_env()
    try:
        client = KnmiClient(settings)
        files = client.latest_files(count=3)
    except KnmiError as error:
        print(f"FOUT: {error}", file=sys.stderr)
        return 1
    if not files:
        print(f"Sleutel werkt, maar {settings.knmi_dataset} {settings.knmi_dataset_version} gaf geen bestanden.")
        return 1
    print(f"Sleutel werkt. Nieuwste bestanden in {settings.knmi_dataset} {settings.knmi_dataset_version}:")
    for f in files:
        print(f"  {f.filename}  ({f.created})")
    try:
        forecast = decode(files[0].filename, client.download(files[0].filename))
    except (KnmiError, RadarFormatError, OSError) as error:
        print(f"FOUT bij uitlezen van {files[0].filename}: {error}", file=sys.stderr)
        return 1
    series = forecast.series_at(52.10, 5.18)
    print(f"Uitgelezen: {len(forecast.steps)} stappen vanaf {forecast.issued_at:%Y-%m-%d %H:%M} UTC.")
    print("De Bilt (mm/u):", " ".join("-" if v is None else f"{v:.1f}" for _, v in series or []))
    return 0


if __name__ == "__main__":
    sys.exit(main())
