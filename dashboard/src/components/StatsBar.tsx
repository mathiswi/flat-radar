"use client";

interface StatsData {
  totalListings: number;
  pendingOutbox: number;
  deadLettered: number;
  lastScrape: string | null;
}

export function StatsBar({ stats }: { stats: StatsData }) {
  return (
    <div className="mb-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
      <StatCard label="Total Listings" value={stats.totalListings} />
      <StatCard label="Pending" value={stats.pendingOutbox} />
      <StatCard label="Dead Letters" value={stats.deadLettered} />
      <StatCard
        label="Last Scrape"
        value={stats.lastScrape ?? "—"}
      />
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-zinc-800 bg-zinc-900 p-4">
      <p className="text-sm text-zinc-400">{label}</p>
      <p className="mt-1 text-2xl font-semibold">{value}</p>
    </div>
  );
}
