"use client";

import Image from "next/image";
import { RelativeTime } from "@/components/RelativeTime";
import { isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

export function ListingsGrid({ listings }: { listings: Listing[] }) {
  if (listings.length === 0) {
    return <EmptyState />;
  }

  return (
    <div className="grid grid-cols-1 items-start gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {listings.map((listing) => (
        <ListingCard key={listing.id} listing={listing} />
      ))}
    </div>
  );
}

function ListingCard({ listing }: { listing: Listing }) {
  const delisted = listing.delistedAt != null;
  const fresh = !delisted && isNew(listing.timestamp);

  // Lead image + up to three thumbnails, de-duped, thumbnail first.
  const gallery: string[] = [];
  for (const u of [listing.thumbnailUrl, ...(listing.imageUrls ?? [])]) {
    if (u && !gallery.includes(u)) gallery.push(u);
  }
  const [lead, ...rest] = gallery;
  const thumbs = rest.slice(0, 3);

  const size = listing.size != null ? `${listing.size} m²` : "";
  const rooms = listing.rooms != null ? `${listing.rooms} Zi.` : "";

  return (
    <a
      href={listing.url}
      target="_blank"
      rel="noopener noreferrer"
      className={`group block overflow-hidden rounded-xl border bg-surface transition-colors focus:outline-none focus-visible:border-signal ${
        fresh ? "border-signal/40 hover:border-signal/70" : "border-border hover:border-muted"
      } ${delisted ? "opacity-55" : ""}`}
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
          <span className="absolute left-3 top-3 inline-flex items-center gap-1.5 rounded-full bg-bg/80 px-2 py-0.5 text-xs font-medium text-signal backdrop-blur-sm">
            <span className="h-1.5 w-1.5 rounded-full bg-signal" aria-hidden="true" />
            New
          </span>
        )}
        {delisted && (
          <span className="absolute left-3 top-3 rounded-full bg-bg/80 px-2 py-0.5 text-xs font-medium text-removed backdrop-blur-sm">
            Entfernt
          </span>
        )}
        <span className="absolute right-3 top-3 rounded-full bg-bg/70 px-2 py-0.5 text-xs text-muted backdrop-blur-sm">
          {listing.source}
        </span>
      </div>

      {thumbs.length > 0 && (
        <div className="grid grid-cols-3 gap-1 p-1">
          {thumbs.map((src, i) => (
            <div key={src} className="relative aspect-[4/3] overflow-hidden rounded bg-surface-2">
              <Image
                src={src}
                alt=""
                fill
                className={`object-cover ${delisted ? "grayscale" : ""}`}
                sizes="(max-width: 640px) 33vw, (max-width: 1024px) 17vw, 11vw"
              />
              {/* If more images exist than we can show, mark the last tile. */}
              {i === thumbs.length - 1 && rest.length > thumbs.length && (
                <span className="absolute inset-0 flex items-center justify-center bg-bg/60 text-xs font-medium text-text">
                  +{rest.length - thumbs.length}
                </span>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="p-4">
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
        <h3 className="mt-2 truncate text-sm text-text/90 group-hover:text-text">
          {listing.title}
        </h3>
        <div className="mt-3 flex items-center justify-between text-xs text-muted">
          <span className="truncate">{listing.district ?? listing.location}</span>
          <RelativeTime ms={listing.timestamp} className="shrink-0 tnum" />
        </div>
      </div>
    </a>
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
