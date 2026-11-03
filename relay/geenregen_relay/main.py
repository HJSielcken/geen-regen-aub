"""ASGI entry point: `uvicorn geenregen_relay.main:app`."""

import os

import sentry_sdk

from .app import create_app

# Errors go to the Sentry project theharmen/geen-regen-aub-relay when SENTRY_DSN is set in .env.
if dsn := os.environ.get("SENTRY_DSN", "").strip():
    sentry_sdk.init(dsn=dsn, environment=os.environ.get("SENTRY_ENVIRONMENT", "production"), send_default_pii=False)

app = create_app()
