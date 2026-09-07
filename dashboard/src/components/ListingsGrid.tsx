"use client";

import Image from "next/image";
import { RelativeTime } from "@/components/RelativeTime";
import { isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

export function ListingsGrid({
  listings,
  onSelect,
}: {
  listings: Listing[];
  onSelect: (listing: Listing) => void;
}) {
  if (listings.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {listings.map((listing) => (
        <ListingCard key={listing.id} listing={listing} onSelect={onSelect} />
      ))}
    </div>
  );
}

function ListingCard({
  listing,
  onSelect,
}: {
  listing: Listing;
  onSelect: (listing: Listing) => void;
}) {
  const delisted = listing.delistedAt != null;
  const fresh = !delisted && isNew(listing.timestamp);

  // One hero photo per card keeps every card the same shape; the rest of the
  // gallery lives in the detail modal. A count badge hints at how many there are.
  const images: string[] = [];
  for (const u of [listing.thumbnailUrl, ...(listing.imageUrls ?? [])]) {
    if (u && !images.includes(u)) images.push(u);
  }
  const lead = images[0];

  const size = listing.size != null ? `${listing.size} m²` : "";
  const rooms = listing.rooms != null ? `${listing.rooms} Zi.` : "";

  return (
    <button
      type="button"
      onClick={() => onSelect(listing)}
      className={`group flex h-full w-full flex-col overflow-hidden rounded-xl border bg-surface text-left transition-colors focus:outline-none focus-visible:border-signal ${
        fresh ? "border-signal/50 hover:border-signal" : "border-border hover:border-muted"
      } ${delisted ? "opacity-60" : ""}`}
    >
      <div className="relative aspect-[3/2] bg-surface-2">
        {lead ? (
          <Image
            src={lead}
            alt={listing.title}
            fill
            className={`object-cover ${delisted ? "grayscale" : ""}`}
            sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw"
          />
        ) : (
          <ImagePlaceholder />
        )}
        {fresh && (
          <span className="absolute left-3 top-3 inline-flex items-center gap-1.5 rounded-full bg-signal px-2 py-0.5 text-xs font-semibold text-on-signal">
            <span className="h-1.5 w-1.5 rounded-full bg-on-signal/70" aria-hidden="true" />
            New
          </span>
        )}
        {delisted && (
          <span className="absolute left-3 top-3 rounded-full bg-bg/85 px-2 py-0.5 text-xs font-medium text-removed backdrop-blur-sm">
            Entfernt
          </span>
        )}
        <span className="absolute right-3 top-3 rounded-full bg-bg/75 px-2 py-0.5 text-xs text-muted backdrop-blur-sm">
          {listing.source}
        </span>
        {images.length > 1 && (
          <span className="absolute bottom-3 right-3 inline-flex items-center gap-1 rounded-full bg-bg/75 px-2 py-0.5 text-xs text-text backdrop-blur-sm">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor" stroke="none" />
              <path d="M21 15l-5-5L5 21" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            {images.length}
          </span>
        )}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-baseline justify-between gap-3">
          <span className="tnum font-display text-xl font-semibold text-text">
            {listing.totalRent != null ? `€${listing.totalRent}` : "—"}
            {listing.totalRent != null && (
              <span className="ml-1 text-xs font-normal text-muted">warm</span>
            )}
          </span>
          {(size || rooms) && (
            <span className="tnum text-sm text-muted">
              {[size, rooms].filter(Boolean).join(" · ")}
            </span>
          )}
        </div>
        {listing.baseRent != null && (
          <p className="tnum mt-0.5 text-xs text-muted">€{listing.baseRent} kalt</p>
        )}
        <h3 className="mt-2 line-clamp-2 text-sm text-text/90 group-hover:text-text">
          {listing.title}
        </h3>
        <div className="mt-auto flex items-center justify-between pt-3 text-xs text-muted">
          <span className="truncate">{listing.district ?? listing.location}</span>
          <RelativeTime ms={listing.timestamp} className="shrink-0 tnum" />
        </div>
      </div>
    </button>
  );
}

function ImagePlaceholder() {
  return (
    <div className="flex h-full items-center justify-center text-muted/50">
      <svg className="h-10 w-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.5}
          d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
        />
      </svg>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="rounded-xl border border-dashed border-border py-16 text-center">
      <p className="text-text">Nothing on the radar yet.</p>
      <p className="mt-1 text-sm text-muted">
        Listings appear here as your feeds get scraped. Check your feeds if this stays empty.
      </p>
    </div>
  );
}
