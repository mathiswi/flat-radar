# System Specification: flat-radar

A lightweight, automated real-estate monitoring system. Scrapes property
listings (currently Kleinanzeigen), stores them deduplicated in Postgres via a
central backend, and will distribute instant alerts via Discord.

---

## 1. System Architecture & Components

Gradle multi-project monorepo (Kotlin/JVM, toolchain 21):

```
flat-radar/
├── shared/             # Domain DTOs (`ApartmentAd`), kotlinx.serialization
├── scraper/            # One-shot polling job: fetch → parse → pre-filter → ingest
├── backend-api/        # Ktor server: dedup, persistence (Exposed + Postgres)
├── api-tests/          # Bruno/OpenCollection HTTP request collection
├── discord-service/    # PLANNED — notification worker
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
  in `SourceParsers.all`. Feeds are configured in `feeds.json` (gitignored;
  see `feeds.json.example`) — adding a district is config-only.
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

### D. `discord-service` — PLANNED
* Consumes new-listing events from the backend, posts rich embeds via Discord
  webhook. Requires a trigger mechanism (direct HTTP call or outbox/poll).

---

## 2. Data Flow

1. Cron starts the scraper container (`docker run --rm`, every 10–15 min).
2. Scraper loads `feeds.json`, processes enabled feeds concurrently.
3. Per feed: fetch search page → parse `AdRef`s → drop swap ads → pre-filter
   ids against backend → fetch/parse detail pages of new ads only (rate
   limited) → POST each `ApartmentAd` to the backend.
4. Backend upserts; duplicates only refresh `last_seen`.
5. (Planned) New inserts trigger a Discord notification.

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
  * backend: `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`, `PORT` (default 8080)
  * scraper: `BACKEND_URL`, `GEMINI_API_KEY` (optional), `feeds.json` mounted
    at `/app/feeds.json`; `.env` at repo root supported for local runs.

---

## 4. Roadmap

1. **Discord notifications** — webhook embeds for new inserts, with price/size
   filter thresholds to avoid spam.
2. **Query filters** on `GET /listings` (price, rooms, district, since).
3. **More sources** — ImmoScout24, WG-Gesucht (new `SourceParser` + registry
   entry + feeds.json entries).
4. **Operational hardening** — run-summary metrics, alerting on repeated
   bot-blocks, retry/backoff for transient backend failures.
5. **Frontend** — minimal dashboard over `GET /listings` (optional).
