# Relay

A small server between the KNMI Data Platform and the app. It downloads each new radar nowcast once
and answers the phone with a tiny JSON forecast for one point. It runs as a Docker container on
BrooklynServer and is reachable at `https://geenregenaub.theharmen.org` through Nginx Proxy Manager.

## Endpoints

| Endpoint | Auth | Purpose |
| --- | --- | --- |
| `GET /healthz` | none | Health check: whether the KNMI key and shared secret are configured (never their values) |
| `GET /v1/rain?lat=&lon=` | `X-Geenregen-Key` header | 2-hour nowcast for a point. Returns 501 until milestone 2 |

Coordinates are rounded to about 1 km and points outside the Netherlands are refused.

## Run on BrooklynServer

```sh
git clone https://github.com/HJSielcken/geen-regen-aub.git
cd geen-regen-aub/relay
cp .env.example .env
# fill in KNMI_API_KEY (see ../docs/knmi-api-key.md) and a shared secret:
openssl rand -hex 32    # paste the output after RELAY_SHARED_SECRET=
docker compose up -d --build
curl http://localhost:8093/healthz
```

Update later with `git pull && docker compose up -d --build`.

## Nginx Proxy Manager

Add a proxy host (needed from milestone 2, harmless now):

- Domain: `geenregenaub.theharmen.org` (with a DNS record pointing at your server)
- Scheme `http`, forward host: the server's LAN IP (or `geenregen-relay` if NPM shares a Docker
  network with the container), forward port `8093` (or `8080` when forwarding to `geenregen-relay` over a shared Docker network)
- SSL tab: request a Let's Encrypt certificate, enable Force SSL and HTTP/2

Then `curl https://geenregenaub.theharmen.org/healthz` should answer `{"status":"ok",...}`.

## The shared secret

The relay URL is public, so every `/v1` request must carry `X-Geenregen-Key: <RELAY_SHARED_SECRET>`.
Give the app the same value at build time (see [android/README.md](../android/README.md)).
Without a configured secret the relay refuses all `/v1` requests.

## Develop

```sh
pip install -r requirements-dev.txt
ruff check . && ruff format --check .
pytest
KNMI_API_KEY=... RELAY_SHARED_SECRET=dev uvicorn geenregen_relay.main:app --reload --port 8080
```
