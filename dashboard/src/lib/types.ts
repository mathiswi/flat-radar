export interface Feed {
  id: string;
  displayName: string;
  url: string;
  district: string;
  source: string;
  enabled: boolean;
}

export interface Listing {
  id: string;
  title: string;
  /** Kaltmiete (cold rent), if the source reported it. */
  baseRent: number | null;
  /** Warmmiete (warm rent) — the headline figure when present. */
  totalRent: number | null;
  size: number | null;
  rooms: number | null;
  location: string;
  district: string | null;
  source: string;
  url: string;
  thumbnailUrl: string | null;
  imageUrls: string[];
  /** Epoch millis when the listing was first seen (from the backend's first_seen). */
  timestamp: number;
  /** Epoch millis when detected as removed from its feed, else null. */
  delistedAt: number | null;
}
