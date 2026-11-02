"""ASGI entry point: `uvicorn geenregen_relay.main:app`."""

from .app import create_app

app = create_app()
