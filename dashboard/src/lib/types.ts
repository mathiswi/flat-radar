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
  size: number | null;
  rooms: number | null;
  bedrooms: number | null;
  bathrooms: number | null;
  floor: string | null;
  apartmentType: string | null;
  /** ISO date string (YYYY-MM-DD) the flat is available from, if given. */
  availableFrom: string | null;
  deposit: number | null;
  /** Kaltmiete (cold rent), if the source reported it. */
  baseRent: number | null;
  sideCosts: number | null;
  heatingCosts: number | null;
  /** Warmmiete (warm rent) — the headline figure when present. */
  totalRent: number | null;
  location: string;
  district: string | null;
  source: string;
  url: string;
  lat: number | null;
  lon: number | null;
  /** Metres from the feed's anchor point, if computed. */
  distanceMeters: number | null;
  thumbnailUrl: string | null;
  imageUrls: string[];
  /** Epoch millis when the listing was first seen (from the backend's first_seen). */
  timestamp: number;
  /** Epoch millis when detected as removed from its feed, else null. */
  delistedAt: number | null;
}
