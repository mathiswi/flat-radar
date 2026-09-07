import Link from "next/link";
import { StatsBar } from "@/components/StatsBar";
import { ViewToggle } from "@/components/ViewToggle";
import { AutoRefresh } from "@/components/AutoRefresh";
import type { Feed } from "@/lib/types";

export const dynamic = "force-dynamic";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

async function fetchStats() {
  const res = await fetch(`${BACKEND_URL}/api/v1/stats`);
  if (!res.ok) throw new Error(`stats: ${res.status}`);
  const data = await res.json();
  // Format date server-side to avoid locale hydration mismatch
  return {
    ...data,
    lastScrape: data.lastScrape
      ? new Date(data.lastScrape).toISOString().replace("T", " ").slice(0, 19) + " UTC"
      : null,
  };
}

async function fetchListings() {
  const res = await fetch(`${BACKEND_URL}/api/v1/listings`);
  if (!res.ok) throw new Error(`listings: ${res.status}`);
  return res.json();
}

// District filter options come from the configured feeds (the source of truth),
// not from whatever districts happen to be present in the loaded listings.
// Failing soft (empty) lets ViewToggle fall back to listing-derived districts.
async function fetchFeedDistricts(): Promise<string[]> {
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/feeds`);
    if (!res.ok) return [];
    const feeds: Feed[] = await res.json();
    return feeds.filter((f) => f.enabled).map((f) => f.district);
  } catch {
    return [];
  }
}

export default async function DashboardPage() {
  const [stats, listings, feedDistricts] = await Promise.all([
    fetchStats(),
    fetchListings(),
    fetchFeedDistricts(),
  ]);

  return (
    <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <header className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <RadarMark />
          <h1 className="font-display text-2xl font-semibold tracking-tight">Flat Radar</h1>
        </div>
        <Link
          href="/admin/feeds"
          className="rounded-md border border-border px-3 py-1.5 text-sm text-muted transition-colors hover:border-muted hover:text-text"
        >
          Feeds
        </Link>
      </header>
      <StatsBar stats={stats} />
      <AutoRefresh />
      <ViewToggle listings={listings} feedDistricts={feedDistricts} />
    </main>
  );
}

/** A small radar sweep — the one bit of iconography, in the signal color. */
function RadarMark() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9.25" stroke="var(--color-border)" strokeWidth="1.5" />
      <circle cx="12" cy="12" r="4.75" stroke="var(--color-border)" strokeWidth="1.5" />
      <path d="M12 12 12 3.25" stroke="var(--color-signal)" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="12" cy="12" r="1.6" fill="var(--color-signal)" />
    </svg>
  );
}
