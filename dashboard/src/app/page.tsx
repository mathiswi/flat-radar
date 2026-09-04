import { StatsBar } from "@/components/StatsBar";
import { ViewToggle } from "@/components/ViewToggle";
import { AutoRefresh } from "@/components/AutoRefresh";

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

export default async function DashboardPage() {
  const [stats, listings] = await Promise.all([fetchStats(), fetchListings()]);

  return (
    <main className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="mb-8 text-3xl font-bold">Flat Radar</h1>
      <StatsBar stats={stats} />
      <AutoRefresh />
      <ViewToggle listings={listings} />
    </main>
  );
}
