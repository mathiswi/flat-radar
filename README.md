# flat-radar

Lightweight real-estate listing monitor. Scrapes Kleinanzeigen, extracts structured data, paves the way for backend storage and Discord alerts.

## Modules

- `shared/` - domain DTOs (`ApartmentAd`), `kotlinx.serialization`
- `scraper/` - HTML parsing + LLM rent-extraction fallback
- `backend-api/` - Ktor server: dedup + persistence (Exposed + Postgres)

> `discord-service` and `frontend` per the spec - still planned, not yet implemented.

## Run

```sh
./gradlew build                # build + test everything
./gradlew :scraper:test        # unit tests
./gradlew :scraper:build       # full build
./gradlew :scraper:run         # one-shot: load feeds, parse, exit
```

The scraper is one-shot - no internal loop, no `delay()`. Scheduling is external (host cron, systemd timer, k8s CronJob). One failed run doesn't block the next; a fresh cron tick re-fetches.

LLM rent-extraction is optional. Set in `.env`:

```
GEMINI_API_KEY=...             # leave empty to disable
BACKEND_URL=http://localhost:8080   # where the scraper POSTs ingested ads
```

`.env` is read via dotenv-java (see `scraper/src/main/kotlin/dev/flatradar/scraper/Env.kt`), so these values work for local `./gradlew :scraper:run` without exporting real shell/OS env vars.

## Deploy

### Docker Compose (recommended for local end-to-end runs)

Brings up Postgres, the backend, and one scraper run in the right order:

```sh
docker compose up --build
```

### Scraper only

Build the Docker image (multi-stage, JRE-only runtime):

```sh
docker build -t flat-radar-scraper .
```

Run one-shot, mounting config from the host:

```sh
docker run --rm \
  --env-file /opt/flat-radar/.env \
  -v /opt/flat-radar/feeds.json:/app/feeds.json:ro \
  flat-radar-scraper
```

Schedule with host cron (every 15 min):

```cron
*/15 * * * * root docker run --rm --env-file /opt/flat-radar/.env -v /opt/flat-radar/feeds.json:/app/feeds.json:ro flat-radar-scraper >> /var/log/flat-radar.log 2>&1
```

## Feeds

Feeds live in `feeds.json` at the repo root (gitignored, see `feeds.json.example`). Each entry is one polling target:

```json
[
  { "id": "barmbek", "displayName": "Barmbek", "source": "kleinanzeigen", "url": "https://...", "district": "Barmbek", "enabled": true }
]
```

- Add a district: add an entry, zero code change.
- Add a source: implement `SourceParser`, register one line in `SourceParsers.all`.

## Architecture

Each listing source ships a `SourceParser` registered in `SourceParsers.all: Map<String, SourceParser>`. `JsonFileFeeds` loads `feeds.json`; the runner looks up `SourceParsers.get(feed.source)` and logs + skips unknown sources.

`KleinanzeigenParser` delegates to `SearchPageParser` (pure parse) and `DetailPageParser` (with `RentFallback` LLM hook), applying the Tauschwohnung filter at the source layer so no detail fetch is wasted on swap ads.