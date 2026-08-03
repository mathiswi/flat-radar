# System Specification: flat-radar

A lightweight, automated real-estate monitoring system. Scrapes property
listings (Kleinanzeigen, ImmoScout24), stores them deduplicated in Postgres via
a central backend, and distributes instant alerts via Discord.

---

## 1. System Architecture & Components

Gradle multi-project monorepo (Kotlin/JVM, toolchain 21):

```
flat-radar/
├── shared/             # Domain DTOs (`ApartmentAd`), kotlinx.serialization
├── scraper/            # One-shot polling job: fetch → parse → pre-filter → ingest
├── backend-api/        # Ktor server: dedup, persistence, outbox + Discord notifier
├── api-tests/          # Bruno/OpenCollection HTTP request collection
└── frontend/           # PLANNED — minimal dashboard
```

### A. `shared`
* Single source of truth for the wire format between scraper and backend.
* Key model: `ApartmentAd` — id, title, size, rooms, bedrooms, bathrooms,
  floor, apartmentType, availableFrom, deposit, baseRent, sideCosts,
  heatingCosts, totalRent, location, url, source, district, timestamp (ms).

### B. `scraper`
* **One-shot execution.** No internal loop; scheduling is external (host cron /
  systemd timer / k8s CronJob). Runs, ingests, exits — non-zero exit code when
  feeds can't be loaded or none are enabled, zero otherwise (individual ad/feed
  failures are logged and skipped, never fatal to the run).
* **Source abstraction:** each platform implements `SourceParser`
  (`parseSearch` → `List<AdRef>`, `parseDetail` → `ApartmentAd?`) and registers
  in `SourceParsers.all`. Currently registered: `kleinanzeigen` (jsoup over the
  HTML site) and `immoscout24` (ImmoScout24's unauthenticated mobile-app JSON
  API — avoids the website's bot-detection entirely). Feeds are configured in
  `feeds.json` (gitignored; see `feeds.json.example`) — adding a district is
  config-only.
* **Two-phase scrape** to minimize traffic and bot-detection surface:
  1. Parse the search page into lightweight `AdRef`s, filter swap ads
     (`SwapDetector`, title/slug heuristics), then POST all ids to
     `POST /api/v1/listings/ids`; the backend returns those already stored.
  2. Fetch detail pages only for new ids (rate-limited with a shared semaphore
     and randomized delay to avoid a machine-like access pattern), parse
     fully, ingest one by one.
* **Rent extraction ladder** (per detail page): structured attribute rows
  (Kaltmiete/Warmmiete/Nebenkosten/Heizkosten) → LLM fallback (Gemini, JSON
  mode; only when no structured rent info at all and a description exists) →
  headline price fills `totalRent` as last resort. LLM is optional: enabled
  iff `GEMINI_API_KEY` is set.
* **Diagnostics:** `scrape diagnose <url>` fetches through the production code
  path and classifies the response (detail page / search page / bot challenge).
* Tech: Ktor Client (Java engine, browser-like headers), jsoup, dotenv-java.

### C. `backend-api`
* Ktor (Netty) + Exposed + HikariCP + Postgres; Liquibase migrations run at
  startup (`db/changelog/db.changelog-master.yaml`).
* Endpoints (all under `/api/v1`):
  * `POST /listings/ids` — pre-filter: body `[id...]`, returns subset that exist.
  * `POST /listings` — ingest one ad; `201 {"status":"inserted"}` or
    `200 {"status":"already_exists"}` (refreshes `last_seen`).
  * `GET  /listings` — all stored listings.
  * `GET  /health` — liveness (no DB). `GET /ready` — readiness (DB ping),
    used by compose `depends_on`.
* Schema: `listings` table mirrors the DTO; `first_seen` / `last_seen` are
  `TIMESTAMPTZ` (DTO `timestamp` maps to `first_seen` on insert).

### D. Notifications (`backend-api`, `dev.flatradar.backend.notify`)
* **Transactional outbox, not a message broker.** `ListingRepository.upsert`
  writes a `notification_outbox` row in the *same* Exposed transaction as a
  genuinely-new listing insert — a notification can never be silently lost,
  even if Discord (or the whole process) is down at ingest time.
* `OutboxWorker` polls unsent rows every 5s (in-process background coroutine,
  launched from `Application.module` in the application's own coroutine scope,
  so it's cancelled automatically on shutdown), delivers each through a
  `Notifier`, and marks it sent — or records the failure (`attempts`,
  `last_error`) for retry on the next poll, up to a cap (dead-lettered, but
  left visible in the table, past that).
* `DiscordNotifier` is the only `Notifier` today: posts a rich embed (title
  linking to the listing, rent, size, rooms, location, distance) to a Discord
  webhook, honoring the `retry_after` Discord returns on HTTP 429.
* Disabled cleanly when `DISCORD_WEBHOOK_URL` is unset — listings are still
  ingested and queued in the outbox, so setting the webhook later delivers the
  backlog on the next poll.
* **Why not RabbitMQ:** one consumer, a handful of events per scrape run — a
  broker would add operational surface (container, client, reconnection
  handling) without solving anything the outbox doesn't already solve, and a
  reliable broker publisher needs a transactional outbox anyway (you can't
  atomically commit to Postgres and publish to AMQP in one step). Revisit if a
  second independent consumer shows up (e.g. a Telegram bot, a live frontend
  feed) — the worker loop is already the natural place to add a publisher
  alongside (or instead of) the direct Discord call.

---

## 2. Data Flow

1. Cron starts the scraper container (`docker run --rm`, every 10–15 min).
2. Scraper loads `feeds.json`, processes enabled feeds concurrently.
3. Per feed: fetch search page → parse `AdRef`s → drop swap ads → pre-filter
   ids against backend → fetch/parse detail pages of new ads only (rate
   limited) → POST each `ApartmentAd` to the backend.
4. Backend upserts; duplicates only refresh `last_seen`. A genuinely new
   listing also gets a `notification_outbox` row in the same transaction.
5. `OutboxWorker` (backend-api, polling every 5s) delivers each unsent outbox
   row as a Discord embed and marks it sent.

Failure policy: an individual ad or feed failure is logged and skipped, never
fatal to the run. A run with no loadable feeds exits non-zero so the operator
notices. If the backend is unreachable the feed aborts (no pre-filter means no
cheap dedup); the next cron tick retries naturally.

---

## 3. Deployment

* Each service ships a multi-stage Dockerfile (gradle build → JRE-only image).
* `docker-compose.yml` orchestrates `postgres` (healthcheck) → `backend-api`
  (waits for postgres, `/health` check) → `scraper` (waits for backend,
  `restart: "no"` — it is a job, not a service).
* Config via environment:
  * backend: `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`, `PORT` (default 8080),
    `DISCORD_WEBHOOK_URL` (optional — omit to disable notifications)
  * scraper: `BACKEND_URL`, `GEMINI_API_KEY` (optional), `feeds.json` mounted
    at `/app/feeds.json`; `.env` at repo root supported for local runs.

---

## 4. Roadmap

1. **Query filters** on `GET /listings` (price, rooms, district, since).
2. **More sources** — WG-Gesucht (new `SourceParser` + registry entry +
   feeds.json entries).
3. **Operational hardening** — run-summary metrics, alerting on repeated
   bot-blocks, retry/backoff for transient backend failures.
4. **Frontend** — minimal dashboard over `GET /listings` (optional).
5. **Notification thresholds** — price/size filters on outbox delivery, if
   the current "notify on every ingested listing" approach turns out to be
   too noisy (upstream feed filters + swap-ad exclusion already keep volume
   low today).
