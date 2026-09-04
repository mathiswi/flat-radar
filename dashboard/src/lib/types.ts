export interface Listing {
  id: string;
  title: string;
  totalRent: number | null;
  size: number | null;
  rooms: number | null;
  location: string;
  district: string | null;
  source: string;
  url: string;
  thumbnailUrl: string | null;
  imageUrls: string[];
  /** Epoch millis when detected as removed from its feed, else null. */
  delistedAt: number | null;
}
