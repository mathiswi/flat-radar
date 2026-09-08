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
    <div className="mb-6 flex flex-wrap items-center gap-x-6 gap-y-2 pb-1 text-sm">
      <span className="text-muted">
        <span className="tnum font-display text-lg font-extrabold text-text">
          {stats.totalListings}
        </span>{" "}
        <span className="uppercase tracking-wide">listings tracked</span>
      </span>

      <span className="uppercase tracking-wide text-muted">
        Last scan <span className="normal-case tracking-normal text-text">{stats.lastScrape ?? "never"}</span>
      </span>

      {stats.pendingOutbox > 0 && (
        <span className="uppercase tracking-wide text-muted">
          <span className="tnum text-text">{stats.pendingOutbox}</span> queued
        </span>
      )}

      {stats.deadLettered > 0 && (
        <span className="ml-auto inline-flex items-center gap-1.5 bg-danger px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wide text-on-signal">
          <span className="tnum">{stats.deadLettered}</span> failed
        </span>
      )}
    </div>
  );
}
