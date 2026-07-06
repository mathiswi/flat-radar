# flat-radar

Lightweight real-estate listing monitor. Scrapes Kleinanzeigen, extracts structured data, paves the way for backend storage and Discord alerts.

## Modules

- `shared/` - domain DTOs (`ApartmentAd`), `kotlinx.serialization`
- `scraper/` - HTML parsing + LLM rent-extraction fallback

> `backend-api`, `discord-service`, `frontend` per the spec - not yet implemented.

## Run

```sh
./gradlew :scraper:test        # unit tests
./gradlew :scraper:build       # full build
./gradlew :scraper:run         # once the Fetcher TODO is filled in
```

LLM rent-extraction is optional. Set in `.env`:

```
GEMINI_API_KEY=...             # leave empty to disable
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