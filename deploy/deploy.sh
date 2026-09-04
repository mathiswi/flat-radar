#!/usr/bin/env bash
# Pull-based deploy, run on the VPS by flatradar-deploy.timer (~every 3 min).
#
# CI publishes new images to GHCR; this script is the *only* thing that rolls
# them onto the server. Nothing reaches in from outside (no SSH from CI).
#
#   git pull        - pick up docker-compose.yml / config changes
#   compose pull     - fetch the newest images from GHCR (needs a prior
#                      `docker login ghcr.io` on this host; images are private)
#   compose up -d    - recreate ONLY changed long-running services
#
# The one-shot `scraper` service is deliberately NOT started here: it runs on
# its own */15 host cron (`docker compose run --rm scraper`). Starting it every
# timer tick would hammer the sources. `compose pull` still refreshes its image
# so the next cron run uses the latest build.
#
# TAG (default "latest") comes from .env and is the rollback lever: pin
# TAG=sha-<short> there to hold/roll back an older build.
set -euo pipefail

cd /opt/flat-radar

# Serialize with any still-running previous tick.
exec 9>/tmp/flatradar-deploy.lock
flock -n 9 || { echo "another deploy is running; skipping"; exit 0; }

git pull --ff-only
docker compose pull
docker compose up -d postgres backend-api dashboard
docker image prune -f >/dev/null 2>&1 || true
