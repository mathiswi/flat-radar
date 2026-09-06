# Next steps

Deferred backlog for flat-radar. Current-state detail lives in `HANDOFF.md`.

## Done recently (for context)
- Kleinanzeigen scraper fixed (Astro page, scoped to `#srchrslt-adtable`).
- ImmoScout cents bug fixed at source; DB cleared, so no legacy cents rows remain
  and the dashboard cents band-aids are gone.
- Delisting detection (feed_id / missed_runs / delisted_at + reconcile).
- Registry-based CI/CD: CI builds images → GHCR; VPS systemd timer pulls (`deploy/`).
- Feed URLs corrected to category-locked `c203` searches (loose URLs pulled
  cross-category junk); `feeds.json.example` fixed to match.
- Swap filter broadened (TAUSCH/SWAP + guarded `gegen` + description "zum/im Tausch").
- Gemini LLM rent fallback removed (headline price covers it; key was broken).
- Dashboard: client-side filters (district/source/rent/size/rooms) + pagination.

---

## 1. Remote feed configuration — replace `feeds.json` with a DB table ✅ DONE
Feeds are now DB-owned; **`feeds.json` is gone entirely** (no file, no mounts). `feeds`
table (V8), seeded with the initial 3 feeds by migration V9, `FeedRepository`, CRUD at
`GET/POST/PUT/DELETE /api/v1/feeds` (`FeedCrudRoutes.kt`) with `c203` validation on
write. Scraper reads them via `BackendFeeds` → `BackendClient.getFeeds()` (replaced
`JsonFileFeeds`, now deleted). `FeedConfig` moved to `shared/`. Dashboard editor at
`/admin/feeds` (behind existing nginx basic auth). District filter now sourced from feed
config (union with listing districts), resolving the `ViewToggle`/`FilterBar` TODOs.

V9 is a one-time seed (runs once), so dashboard edits/deletes to the seeded feeds are
never overwritten by redeploys. To change the seed for future fresh DBs, edit V9 — but
prefer the dashboard for a running deployment.

## 2. Notifications & charts (stretch)
- **Discord "listing gone" alert** — rides on the delisting work. The outbox + Notifier
  are shaped for a single "new listing" message; a "gone" type needs a message-type
  discriminator. Dashboard visibility (Entfernt badge) already covers detection, so
  this is optional.
- **Charts** — listings over time, price trends (Recharts). From the original spec's
  stretch goals; not started.

## 3. CI/CD hardening (small)
- Bump deprecated actions: `actions/setup-java@v4 → v5`; the Node 20 actions
  (`checkout`, `setup-node`, `setup-gradle`) are being force-run on Node 24 — update
  when newer majors land.
- Add a CI step running Liquibase against a throwaway Postgres service-container, so a
  bad migration fails CI instead of crash-looping the backend on the VPS (today only
  the H2 test-changelog validates schema, not the real `db.changelog-master`).
- GHCR image retention: prune old untagged `sha-*` versions so they don't accumulate.

## 4. Reprocess / backfill (general gap)
The two-phase scraper never re-fetches known IDs, so existing rows can't pick up fields
added by newer parsing logic. The cents/image cases are moot now (DB was cleared), but
the limitation remains for the next detail-parser change. Solve narrowly (a one-off
re-fetch script) or generally (a scraper `--reprocess` mode) if it bites again.

## 5. Server-side query params on `GET /listings` (if scale grows)
Pagination + filtering are client-side today (the whole list is fetched, then paged in
the browser) — fine at the current ~dozens of rows. If the dataset grows large, push
`limit`/`offset` + filters into `GET /listings` and switch the dashboard to URL-driven
SSR fetches of one page (`ListingRepository.findAll` → a filtered/paged query).
