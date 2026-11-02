"""Check that the KNMI API key works: `python -m geenregen_relay.check_knmi`.

Reads KNMI_API_KEY from the environment and lists the newest radar forecast file.
"""

from __future__ import annotations

import sys

from .config import Settings
from .knmi import KnmiClient, KnmiError


def main() -> int:
    settings = Settings.from_env()
    try:
        files = KnmiClient(settings).latest_files(count=3)
    except KnmiError as error:
        print(f"FOUT: {error}", file=sys.stderr)
        return 1
    if not files:
        print(f"Sleutel werkt, maar {settings.knmi_dataset} {settings.knmi_dataset_version} gaf geen bestanden.")
        return 1
    print(f"Sleutel werkt. Nieuwste bestanden in {settings.knmi_dataset} {settings.knmi_dataset_version}:")
    for f in files:
        print(f"  {f.filename}  ({f.created})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
