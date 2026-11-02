# KNMI API key

The relay downloads the radar nowcast from the KNMI Data Platform. Every request needs an API key.
The key only ever lives on the relay server; the phone app never sees it.

## 1. Register (about 5 minutes)

1. Go to the KNMI Developer Portal: <https://developer.dataplatform.knmi.nl/>.
2. Choose **Register** / **Request an API key** (direct link:
   <https://developer.dataplatform.knmi.nl/register>).
3. Request a key for the **Open Data API**. Describe the use honestly, for example:
   "Personal rain radar app; a single server downloads each `radar_forecast` 2.0 file once
   (about every 5 minutes)."
4. While you are there, also request a key for the **Notification Service**, if the form offers it
   separately. Milestone 2 uses it to hear about new radar files instead of polling.
5. The key arrives by email. It is a long string starting with `eyJ`.

Why register instead of using KNMI's public anonymous key: the anonymous key is shared by everyone
(50 requests a minute, 3,000 an hour) and expires periodically. A registered key gets 1,000
requests an hour for you alone; the relay needs about 12.

## 2. Put it on BrooklynServer

In the checked-out repo on the server:

```sh
cd relay
cp .env.example .env
nano .env            # paste the key after KNMI_API_KEY=
```

Fill in `RELAY_SHARED_SECRET` at the same time (see [relay/README.md](../relay/README.md)).
`.env` is git-ignored, so the key never ends up in the repo.

## 3. Check that it works

```sh
docker compose build
docker compose run --rm relay python -m geenregen_relay.check_knmi
```

A working key prints the newest radar forecast files, for example
`RAD_NL25_PCP_FM_202611022100.h5`. A wrong key prints `FOUT: KNMI rejected the API key`.

Without Docker, from `relay/`:

```sh
pip install -r requirements.txt
KNMI_API_KEY=eyJ... python -m geenregen_relay.check_knmi
```

## Do not

- Commit the key, or put it in the Android app or in GitHub Actions secrets. Only the relay needs it.
- Poll KNMI from several places with the same key; KNMI treats heavy polling as abuse.
