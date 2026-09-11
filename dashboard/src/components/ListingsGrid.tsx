"use client";

import Image from "next/image";
import { RelativeTime } from "@/components/RelativeTime";
import { formatIsoDate, isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

export function ListingsGrid({
  listings,
  onSelect,
  onToggleFake,
}: {
  listings: Listing[];
  onSelect: (listing: Listing) => void;
  onToggleFake: (listing: Listing) => void;
}) {
  if (listings.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {listings.map((listing) => (
        <ListingCard key={listing.id} listing={listing} onSelect={onSelect} onToggleFake={onToggleFake} />
      ))}
    </div>
  );
}

function ListingCard({
  listing,
  onSelect,
  onToggleFake,
}: {
  listing: Listing;
  onSelect: (listing: Listing) => void;
  onToggleFake: (listing: Listing) => void;
}) {
  const delisted = listing.delistedAt != null;
  const fake = listing.fake;
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
  const availableFrom = formatIsoDate(listing.availableFrom);

  return (
    <button
      type="button"
      onClick={() => onSelect(listing)}
      className={`group flex h-full w-full flex-col overflow-hidden border-2 border-border bg-surface text-left transition-[transform,box-shadow] focus:outline-none focus-visible:ring-2 focus-visible:ring-signal hover:-translate-x-0.5 hover:-translate-y-0.5 hover:shadow-[4px_4px_0_0_var(--color-border)] ${
        delisted || fake ? "opacity-60" : ""
      }`}
    >
      <div className="relative aspect-[3/2] border-b border-border bg-surface-2">
        {lead ? (
          <Image
            src={lead}
            alt={listing.title}
            fill
            className={`object-cover ${delisted || fake ? "grayscale" : ""}`}
            sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw"
          />
        ) : (
          <ImagePlaceholder />
        )}
        <div className="absolute left-0 top-3 flex flex-col items-start gap-1">
          {fresh && (
            <span className="bg-signal px-2.5 py-1 text-xs font-extrabold uppercase tracking-widest text-on-signal">
              New
            </span>
          )}
          {delisted && (
            <span className="bg-border px-2.5 py-1 text-xs font-bold uppercase tracking-wide text-bg">
              Entfernt
            </span>
          )}
          {fake && (
            <span className="bg-danger px-2.5 py-1 text-xs font-bold uppercase tracking-wide text-on-signal">
              Fake
            </span>
          )}
        </div>
        <span className="absolute right-2 top-2 bg-bg/85 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-text backdrop-blur-sm">
          {listing.source}
        </span>
        {/* The card itself is a <button>, so the mark control can't be a nested
            button; a role="button" span with stopPropagation keeps the card
            clickable while offering its own action. */}
        <span
          role="button"
          tabIndex={0}
          aria-pressed={fake}
          aria-label={fake ? "Unmark fake" : "Mark as fake"}
          title={fake ? "Unmark fake" : "Mark as fake"}
          onClick={(e) => {
            e.stopPropagation();
            onToggleFake(listing);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              e.stopPropagation();
              onToggleFake(listing);
            }
          }}
          className={`absolute bottom-2 left-2 inline-flex cursor-pointer items-center gap-1 px-2 py-0.5 text-xs font-bold uppercase tracking-wide backdrop-blur-sm transition-colors ${
            fake ? "bg-danger text-on-signal" : "bg-bg/85 text-muted hover:text-danger"
          }`}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill={fake ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2">
            <path d="M4 21V4h13l-2 4 2 4H4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          {fake ? "Fake" : "Flag"}
        </span>
        {images.length > 1 && (
          <span className="absolute bottom-2 right-2 inline-flex items-center gap-1 bg-bg/85 px-2 py-0.5 text-xs font-bold text-text backdrop-blur-sm">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="3" width="18" height="18" />
              <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor" stroke="none" />
              <path d="M21 15l-5-5L5 21" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            {images.length}
          </span>
        )}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-baseline justify-between gap-3">
          <span className="tnum font-display text-2xl font-extrabold leading-none tracking-tight text-text">
            {listing.totalRent != null ? `€${listing.totalRent}` : "—"}
            {listing.totalRent != null && (
              <span className="ml-1 align-top text-[10px] font-semibold uppercase tracking-wide text-muted">
                warm
              </span>
            )}
          </span>
          {(size || rooms) && (
            <span className="tnum text-sm font-semibold text-text">
              {[size, rooms].filter(Boolean).join(" · ")}
            </span>
          )}
        </div>
        {(listing.baseRent != null || availableFrom) && (
          <p className="tnum mt-1 text-xs font-semibold uppercase tracking-wide text-muted">
            {[
              listing.baseRent != null ? `€${listing.baseRent} kalt` : null,
              availableFrom ? `frei ab ${availableFrom}` : null,
            ]
              .filter(Boolean)
              .join(" · ")}
          </p>
        )}
        <h3 className="mt-2 line-clamp-2 text-sm font-medium text-text">{listing.title}</h3>
        <div className="mt-auto flex items-center justify-between pt-3 text-xs text-muted">
          <span className="truncate font-semibold uppercase tracking-wide">
            {listing.district ?? listing.location}
          </span>
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
    <div className="border-2 border-dashed border-border py-16 text-center">
      <p className="font-display text-lg font-bold text-text">Nothing on the radar yet.</p>
      <p className="mt-1 text-sm text-muted">
        Listings appear here as your feeds get scraped. Check your feeds if this stays empty.
      </p>
    </div>
  );
}
