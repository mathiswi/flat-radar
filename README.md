# flat-radar

Lightweight real-estate listing monitor. Scrapes Kleinanzeigen and ImmoScout24, stores deduplicated listings in Postgres, and delivers instant Discord alerts via a transactional outbox.

## Modules

- `shared/` - domain DTOs (`ApartmentAd`), `kotlinx.serialization`
- `scraper/` - one-shot polling job: fetch -> parse -> pre-filter -> ingest
- `backend-api/` - Ktor server: dedup, persistence (Exposed + Postgres), transactional outbox, Discord notifier

> `frontend` per the spec - still planned.

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
GEMINI_API_KEY=...                  # leave empty to disable
BACKEND_URL=http://localhost:8080   # where the scraper POSTs ingested ads
DISCORD_WEBHOOK_URL=...             # leave empty to disable notifications
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
  { "id": "barmbek", "displayName": "Barmbek", "source": "kleinanzeigen", "url": "https://...", "district": "Barmbek", "enabled": true },
  { "id": "barmbek-is24", "displayName": "Barmbek-Nord (ImmoScout24)", "source": "immoscout24", "url": "https://...", "district": "Barmbek-Nord", "enabled": true }
]
```

- Add a district: add an entry, zero code change.
- Add a source: implement `SourceParser`, register one line in `SourceParsers.all`.

## Architecture

Each listing source ships a `SourceParser` registered in `SourceParsers.all: Map<String, SourceParser>`. `JsonFileFeeds` loads `feeds.json`; the runner looks up `SourceParsers.get(feed.source)` and logs + skips unknown sources.

**Two-phase scrape:** parse search page into lightweight `AdRef`s -> filter swap ads (`SwapDetector`) -> pre-filter ids against backend via `POST /api/v1/listings/ids` -> fetch and parse detail pages only for new ads.

**Sources:**
- `kleinanzeigen` - jsoup over the HTML site, with optional LLM rent-extraction fallback
- `immoscout24` - ImmoScout24's unauthenticated mobile-app JSON API (avoids the website's bot-detection entirely)

**Notifications:** transactional outbox in the backend - a new listing insert writes a `notification_outbox` row atomically. `OutboxWorker` polls every 5s and delivers via Discord webhook. Disabled when `DISCORD_WEBHOOK_URL` is unset.
