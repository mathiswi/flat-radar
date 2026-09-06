# flat-radar handoff

## Current state (2026-09-05)

All work below is **committed and deployed** to `main`. Prod holds 22 clean
apartment listings (Barmbek 8, Barmbek-Nord 5, Eimsbüttel 9); no swaps, no
cents-scale rents. Deploys are now automated (registry-based CI/CD, pull model).

### Infrastructure

| Component | Detail |
|---|---|
| **VPS** | Hetzner, `167.233.243.210`, Ubuntu 24.04, `ssh root@167.233.243.210` |
| **Backend** | Ktor on `:8080`, Exposed + Postgres + Liquibase |
| **Dashboard** | Next.js 16 on `:3000` (SSR + API proxy to Ktor) |
| **Scraper** | One-shot, host cron `*/15` (`docker compose run --rm scraper`). Kleinanzeigen + ImmoScout24 |
| **DB** | Postgres 16 Alpine, not publicly exposed |
| **Public URL** | `https://flatradar.mwitte.dev` (Cloudflare proxy + Origin CA cert, nginx basic auth, VPS ports locked to Cloudflare ranges) |
| **Registry** | GHCR, private: `ghcr.io/mathiswi/flat-radar-{backend-api,dashboard,scraper}` |

---

## CI/CD (registry-based, pull deploy)

**CI** — `.github/workflows/deploy.yml`, on push to `main`: `test` job
(`./gradlew test` + dashboard `tsc --noEmit`) → `build-push` matrix builds the
three images on GitHub runners and pushes them to **private GHCR** (`latest` +
`sha-<short>`). Uses the auto `GITHUB_TOKEN`; **no VPS secrets in GitHub**.

**Deploy** — pull-based, no inbound SSH from CI. A **systemd timer** on the VPS
(`flatradar-deploy.timer`, ~every 3 min → `deploy/deploy.sh`) runs
`git pull --ff-only && docker compose pull && docker compose up -d postgres
backend-api dashboard`. The one-shot `scraper` is deliberately **not** started by
the deploy (it runs on its own `*/15` cron with the freshly-pulled image).

- **Auth:** the VPS is logged into `ghcr.io` (`/root/.docker/config.json`) with a
  `read:packages` token, so `docker compose pull` can fetch the private images.
- **Rollback:** set `TAG=sha-<short>` in the VPS `.env` (default `latest`); the
  timer then holds that tag. `.env` is gitignored so `git pull` won't touch it.
- **Force a deploy now:** `ssh root@… 'systemctl start flatradar-deploy.service'`.
- **Unit files** live in `deploy/` (symlinked into `/etc/systemd/system/`).

---

## Scraper

- **Kleinanzeigen** (curl subprocess — JVM TLS gets a stripped page; jsoup parse):
  - Search page is an Astro frontend but cards are server-rendered. Card selection
    is **scoped to `#srchrslt-adtable`** (`SearchPageParser`) so the cross-category
    "Anzeigen aus der Umgebung" recommendation blocks are never ingested.
  - Follows result pages 2..`PAGE_CAP` (3).
  - **Swap filter** (`SwapDetector`): title/slug match `TAUSCH`/`SWAP` (substring)
    or a guarded `… gegen …` heuristic (needs a home word, excludes money-"gegen"
    like "gegen Kaution"); the detail-page description adds positive "zum/im Tausch"
    + compound-noun signals. Swaps are dropped before ingest.
  - **Rent**: structured attribute rows (Kaltmiete/Warmmiete/Nebenkosten/Heizkosten)
    → `#viewad-price` headline as last resort. **No LLM** — the Gemini rent fallback
    was removed (the headline covers every no-structured-rows ad; the key was broken
    and it only added marginal accuracy).
- **ImmoScout24**: mobile API (`api.mobile.immobilienscout24.de`), structured, paginated.
- **Feeds are DB-owned** (`feeds` table, migration V8) and served to the scraper over
  `GET /api/v1/feeds` (`BackendFeeds` → `BackendClient.getFeeds()`). Edited from the
  dashboard at `/admin/feeds`; `FeedConfig` lives in `shared/`. Backend validates on
  write that Kleinanzeigen URLs are category-locked (`c203l<locid>…`); loose
  `/hamburg/<district>/anzeige:angebote` URLs return *every* category and were the
  source of the junk-listing incident. **No `feeds.json` anywhere** — a fresh DB is
  seeded with the initial feeds by migration `V9__seed_feeds.yaml` (runs once, so
  dashboard edits aren't overwritten). Current feeds: `barmbek` (l26487),
  `eimsbuettel` (l16480), `barmbek-is24`.
- Two-phase: pre-filter candidate IDs (`POST /listings/ids`), only detail-fetch
  unknown ones — a known listing is never re-fetched (the reprocess gap, see NEXT_STEPS).

## Backend

Ktor + Exposed + Liquibase. Dedup/upsert, delisting reconcile, outbox → Discord (if
`DISCORD_WEBHOOK_URL` set), IP whitelist. **Delisting** (migration `V7`): `feed_id`,
`missed_runs`, `delisted_at`. `reconcileSeen(feedId, seenIds, threshold)` bumps seen
IDs alive, increments the miss counter for that feed's absent listings, marks
`delisted_at` at `DELISTING_THRESHOLD` (env, default 2). Empty seen set is a no-op guard.

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/health`, `/api/v1/ready` | Liveness / readiness (public) |
| GET | `/api/v1/listings` | All stored listings (incl. `delistedAt`) — no query params yet |
| POST | `/api/v1/listings` | Ingest one ad (201 new / 200 exists) |
| POST | `/api/v1/listings/ids` | Pre-filter known IDs |
| POST | `/api/v1/feeds/{feedId}/seen` | Delisting reconcile; body = visible ad IDs |
| GET | `/api/v1/feeds` | All configured feeds (scraper + dashboard read this) |
| POST | `/api/v1/feeds` | Create/replace a feed (201 new / 200 exists); validates `c203` |
| PUT | `/api/v1/feeds/{feedId}` | Update a feed (path id authoritative); validates `c203` |
| DELETE | `/api/v1/feeds/{feedId}` | Delete a feed (200 / 404) |
| GET | `/api/v1/stats` | `{ totalListings, pendingOutbox, deadLettered, lastScrape }` |

## Dashboard

Next.js 16, SSR fetches all listings + stats + feed districts. `ViewToggle` (client)
owns grid/table, a **FilterBar** (district / source / rent / size / min-rooms — district
options come from the configured feeds, unioned with districts present in the listings),
**Pagination** (24/page, client-side), the delisting "Entfernt" badge, and a "Hide
removed (N)" toggle. A **`/admin/feeds`** page (`FeedEditor`) does feed CRUD via
`/api/feeds` proxy routes (behind the same nginx basic auth). Rents render as plain
euros (the cents band-aids were removed — no legacy cents rows after the DB clear).

---

## Architecture

```
push to main ─► GitHub Actions (test → build) ─► GHCR (private images: latest + sha)
                                                    ▲ docker compose pull (VPS read token)
scraper (cron */15) ─► backend-api (Ktor :8080) ─► Postgres
                │ GET  /feeds  (feed config)   Exposed
                │ POST /listings/ids            └── OutboxWorker ─► Discord (if configured)
                │ POST /listings
                │ POST /feeds/{id}/seen   (delisting reconcile)
                ▼
Browser ─► Next.js :3000 ─► Ktor :8080  (SSR + API proxy, internal Docker net)

VPS systemd timer (~3 min): git pull ─► docker compose pull ─► up -d {postgres,backend-api,dashboard}
```

| Module | Dir | What |
|---|---|---|
| `shared` | `shared/` | `ApartmentAd` DTO (+ `delistedAt`) |
| `scraper` | `scraper/` | Kleinanzeigen (curl + jsoup) + ImmoScout24 (mobile JSON API); swap filter; no LLM |
| `backend-api` | `backend-api/` | Ktor, dedup/upsert, delisting reconcile, outbox, Discord, IP whitelist |
| `dashboard` | `dashboard/` | Next.js 16, grid/table, filters + pagination, delisting badge |

---

## Useful commands

```sh
# Force a deploy (pull latest images now)
ssh root@167.233.243.210 'systemctl start flatradar-deploy.service'
journalctl -u flatradar-deploy -n 40 --no-pager          # deploy log
# Logs
ssh root@167.233.243.210 'docker compose -f /opt/flat-radar/docker-compose.yml logs --tail 50 backend-api'
tail -30 /var/log/flat-radar.log                          # scraper cron log
# Manual scrape / clear
ssh root@167.233.243.210 'cd /opt/flat-radar && docker compose run --rm scraper'
ssh root@167.233.243.210 "cd /opt/flat-radar && docker compose exec -T postgres psql -U app -d flatradar -c 'TRUNCATE listings, notification_outbox RESTART IDENTITY;'"
# DB peek
ssh root@167.233.243.210 "cd /opt/flat-radar && docker compose exec -T postgres psql -U app -d flatradar -c 'SELECT count(*) FILTER (WHERE delisted_at IS NULL) live, count(*) FILTER (WHERE delisted_at IS NOT NULL) delisted FROM listings;'"
```

### Local dev

```sh
docker compose up -d postgres backend-api
docker compose run --rm --build scraper                   # scrape into local DB
cd dashboard && BACKEND_URL=http://localhost:8080 npm run dev   # → http://localhost:3000
```

Optional env: `DELISTING_THRESHOLD` (default 2), `DISCORD_WEBHOOK_URL` (notifications
off until set). `GEMINI_API_KEY` is no longer used (LLM removed).

---

## Remaining work

See `NEXT_STEPS.md` — feed config is now fully DB-backed (done; `feeds.json` removed
entirely). Remaining: notifications/charts, CI/CD hardening, the reprocess/backfill gap.
