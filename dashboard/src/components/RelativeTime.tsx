"use client";

import { compactDate, relativeTime, useMounted } from "@/lib/time";

/**
 * Shows a listing's age. Renders the stable dd.mm. date on the server and first
 * paint, then swaps to "now"-relative text ("3h ago") once mounted, so the two
 * renders never disagree.
 */
export function RelativeTime({ ms, className }: { ms: number; className?: string }) {
  const mounted = useMounted();
  return (
    <time
      dateTime={new Date(ms).toISOString()}
      className={className}
      suppressHydrationWarning
    >
      {mounted ? relativeTime(ms) : compactDate(ms)}
    </time>
  );
}
