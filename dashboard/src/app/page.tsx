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
    <main className="mx-auto max-w-6xl px-4 py-8">
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-3xl font-bold">Flat Radar</h1>
        <Link href="/admin/feeds" className="text-sm text-zinc-400 hover:text-zinc-100">
          Feeds →
        </Link>
      </div>
      <StatsBar stats={stats} />
      <AutoRefresh />
      <ViewToggle listings={listings} feedDistricts={feedDistricts} />
    </main>
  );
}
