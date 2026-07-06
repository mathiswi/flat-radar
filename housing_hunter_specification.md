# System Specification: Housing Hunter Monorepo

A lightweight, automated real estate monitoring system designed to scrape property listings, process data through a central backend, and distribute instant alerts via communication channels.

---

## 1. System Architecture & Components

The system is organized as a Gradle multi-project monorepo containing four core modules and a shared data layer.

```
housing-hunter-monorepo/
├── shared/             # Common Data Transfer Objects (DTOs) & Serialization
├── scraper/            # Scheduled polling service (HTTP + HTML Parser)
├── backend-api/        # Central storage, business logic, and REST endpoints
├── discord-service/    # Notification worker for downstream alerts
└── frontend/           # (Optional) Minimalist dashboard for monitoring
```

### Component Breakdown

#### A. `shared` (Kotlin Multiplatform / Common JVM)
* **Purpose:** Single source of truth for domain models to eliminate duplication across services.
* **Tech:** `kotlinx.serialization`
* **Key Model:** `ApartmentAd` containing ID, title, price, size, room count, location, URL, and timestamp.

#### B. `scraper` (Kotlin JVM / Coroutines)
* **Purpose:** Periodically queries real estate platforms without browser overhead.
* **Tech:** `Ktor Client` (with realistic browser headers/User-Agent rotation), `jsoup` for CSS-selector extraction.
* **Execution:** Non-blocking coroutine loop using `delay()` instead of standard blocking cron-jobs.

#### C. `backend-api` (Kotlin Ktor or Spring Boot)
* **Purpose:** Central coordinator. Receives raw scraped data, filters duplicates, stores records, and triggers events.
* **Tech:** `Ktor Server`, `Exposed` (SQL library), `SQLite` or `PostgreSQL`.
* **Endpoints:**
  * `POST /api/v1/listings` – Ingests raw listings from the scraper.
  * `GET /api/v1/listings` – Fetches tracked listings (filtered by status/price).

#### D. `discord-service` (Kotlin JVM)
* **Purpose:** Consumes events from the backend and posts rich embeds to a specified channel.
* **Tech:** `Ktor Client` hitting Discord Webhooks (or a lightweight library like `Kord`).

---

## 2. Core Data Flow

1. **Poll:** The `scraper` triggers every 10–15 minutes, fetching raw HTML from the target search URL.
2. **Extract & Map:** `jsoup` parses the DOM. Extracted fields are mapped directly into `shared.ApartmentAd` objects.
3. **Ingest:** The `scraper` sends a `POST` payload to `backend-api`.
4. **Filter:** The backend checks the incoming IDs against the database. 
5. **Persist & Alert:** If an ID is new, it is saved. The backend then dispatches an internal event or direct HTTP call to the `discord-service`.
6. **Notify:** The `discord-service` formats a rich embed with clickable URLs and sends it via the Discord webhook.

---

## 3. Development & Deployment Plan

### Step 1: Initialize Monorepo & Shared Module
* Set up a root `settings.gradle.kts` declaring all sub-modules.
* Implement the core data classes in `:shared`.

### Step 2: Build the Core Pipeline (Scraper to Backend)
* Implement the HTML parsing logic using mock HTML text first, then wire up `Ktor Client`.
* Create a minimal `backend-api` that accepts payloads and prints them to console to verify the network connection.

### Step 3: Persistence & Filtering
* Hook up `Exposed` with an embedded SQLite database file.
* Implement the deduplication step (`insert if not exists`).

### Step 4: Notification Loop
* Connect the Discord webhook flow. Turn on filtering parameters (e.g., maximum price thresholds, keyword exclusions) to avoid spamming the channel.
