"use client";

import { useEffect, useState } from "react";

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

/** A listing counts as "new" (radar signal) while it was first seen within a day. */
export function isNew(ms: number, now: number = Date.now()): boolean {
  return now - ms < DAY;
}

/**
 * Human-friendly age: "just now", "3h ago", "yesterday", "4d ago", then a compact
 * dd.mm. date. Kept locale-free so it renders identically on server and client.
 */
export function relativeTime(ms: number, now: number = Date.now()): string {
  const diff = now - ms;
  if (diff < HOUR) {
    const mins = Math.max(0, Math.floor(diff / (60 * 1000)));
    return mins <= 1 ? "just now" : `${mins}m ago`;
  }
  if (diff < DAY) return `${Math.floor(diff / HOUR)}h ago`;
  const days = Math.floor(diff / DAY);
  if (days === 1) return "yesterday";
  if (days < 7) return `${days}d ago`;
  return compactDate(ms);
}

/** Deterministic dd.mm. in UTC — the stable value shown before the client mounts. */
export function compactDate(ms: number): string {
  const d = new Date(ms);
  const dd = String(d.getUTCDate()).padStart(2, "0");
  const mm = String(d.getUTCMonth() + 1).padStart(2, "0");
  return `${dd}.${mm}.`;
}

/**
 * True only after the first client render. Lets time-relative UI render a stable,
 * SSR-safe value first (avoiding a hydration mismatch), then upgrade to "now"-based
 * text once mounted.
 */
export function useMounted(): boolean {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  return mounted;
}
