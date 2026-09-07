interface StatsData {
  totalListings: number;
  pendingOutbox: number;
  deadLettered: number;
  lastScrape: string | null;
}

/**
 * Slim radar-status strip: what's on the radar and when it last swept. Notification
 * plumbing (pending / failed) is demoted to quiet indicators — failures only speak up,
 * in the signal color, when there actually are any.
 */
export function StatsBar({ stats }: { stats: StatsData }) {
  return (
    <div className="mb-6 flex flex-wrap items-center gap-x-6 gap-y-2 border-y border-border py-3 text-sm">
      <span className="text-muted">
        <span className="tnum font-display text-base font-medium text-text">
          {stats.totalListings}
        </span>{" "}
        listings tracked
      </span>

      <span className="text-muted">
        Last scan{" "}
        <span className="text-text">{stats.lastScrape ?? "never"}</span>
      </span>

      {stats.pendingOutbox > 0 && (
        <span className="text-muted">
          <span className="tnum text-text">{stats.pendingOutbox}</span> notification
          {stats.pendingOutbox === 1 ? "" : "s"} queued
        </span>
      )}

      {stats.deadLettered > 0 && (
        <span className="ml-auto inline-flex items-center gap-1.5 rounded-full bg-signal-soft px-2.5 py-0.5 text-signal">
          <span className="tnum font-medium">{stats.deadLettered}</span> notification
          {stats.deadLettered === 1 ? "" : "s"} failed
        </span>
      )}
    </div>
  );
}
