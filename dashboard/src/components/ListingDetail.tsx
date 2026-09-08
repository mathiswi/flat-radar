"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import Image from "next/image";
import { RelativeTime } from "@/components/RelativeTime";
import { isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

const euro = new Intl.NumberFormat("de-DE");

function formatEuro(n: number | null): string | null {
  return n == null ? null : `€${euro.format(n)}`;
}

function formatDate(iso: string | null): string | null {
  if (!iso) return null;
  const [y, m, d] = iso.split("-");
  return y && m && d ? `${d}.${m}.${y}` : iso;
}

function formatDistance(m: number | null): string | null {
  if (m == null) return null;
  return m >= 1000 ? `${(m / 1000).toFixed(1).replace(".", ",")} km` : `${m} m`;
}

/** Full-detail modal for one listing. Rendered only while a listing is selected. */
export function ListingDetail({ listing, onClose }: { listing: Listing; onClose: () => void }) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  // Lock scroll and wire Escape while open.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);

  if (!mounted) return null;

  const delisted = listing.delistedAt != null;
  const fresh = !delisted && isNew(listing.timestamp);

  const rent: [string, string | null][] = [
    ["Warm", formatEuro(listing.totalRent)],
    ["Kalt", formatEuro(listing.baseRent)],
    ["Nebenkosten", formatEuro(listing.sideCosts)],
    ["Heizung", formatEuro(listing.heatingCosts)],
    ["Kaution", formatEuro(listing.deposit)],
  ];
  const specs: [string, string | null][] = [
    ["Size", listing.size != null ? `${listing.size} m²` : null],
    ["Rooms", listing.rooms != null ? `${listing.rooms}` : null],
    ["Bedrooms", listing.bedrooms != null ? `${listing.bedrooms}` : null],
    ["Bathrooms", listing.bathrooms != null ? `${listing.bathrooms}` : null],
    ["Floor", listing.floor],
    ["Type", listing.apartmentType],
    ["Available from", formatDate(listing.availableFrom)],
    ["District", listing.district],
    ["Location", listing.location],
    ["Distance", formatDistance(listing.distanceMeters)],
  ];

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-bg/80 p-4 backdrop-blur-sm sm:p-8"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={listing.title}
        className="relative w-full max-w-3xl border-2 border-border bg-surface shadow-[8px_8px_0_0_var(--color-border)]"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={onClose}
          aria-label="Close"
          autoFocus
          className="absolute right-3 top-3 z-10 flex h-8 w-8 items-center justify-center border-2 border-border bg-bg text-text transition-colors hover:bg-border hover:text-bg"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M6 6l12 12M18 6L6 18" strokeLinecap="round" />
          </svg>
        </button>

        <Gallery listing={listing} delisted={delisted} />

        <div className="p-5 sm:p-6">
          <div className="flex flex-wrap items-center gap-2 text-xs">
            {fresh && (
              <span className="bg-signal px-2 py-0.5 font-extrabold uppercase tracking-widest text-on-signal">
                New
              </span>
            )}
            {delisted && (
              <span className="bg-border px-2 py-0.5 font-bold uppercase tracking-wide text-bg">Entfernt</span>
            )}
            <span className="bg-surface-2 px-2 py-0.5 font-semibold uppercase tracking-wide text-muted">
              {listing.source}
            </span>
            <RelativeTime ms={listing.timestamp} className="tnum text-muted" />
          </div>

          <h2 className="mt-3 font-display text-2xl font-extrabold tracking-tight text-text">{listing.title}</h2>

          <div className="mt-4 flex flex-wrap gap-x-8 gap-y-2">
            {rent
              .filter(([, v]) => v)
              .map(([label, v], i) => (
                <div key={label}>
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</p>
                  <p
                    className={`tnum font-display font-extrabold leading-none tracking-tight text-text ${
                      i === 0 ? "text-3xl" : "text-lg"
                    }`}
                  >
                    {v}
                  </p>
                </div>
              ))}
          </div>

          <dl className="mt-5 grid grid-cols-2 gap-x-6 gap-y-3 border-t-2 border-border pt-5 sm:grid-cols-3">
            {specs
              .filter(([, v]) => v)
              .map(([label, v]) => (
                <div key={label}>
                  <dt className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</dt>
                  <dd className="tnum mt-0.5 text-sm font-medium text-text">{v}</dd>
                </div>
              ))}
          </dl>

          <a
            href={listing.url}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-6 inline-flex items-center gap-2 bg-signal px-4 py-2.5 text-sm font-extrabold uppercase tracking-wide text-on-signal transition-transform hover:-translate-y-0.5"
          >
            Open on {listing.source}
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M7 17L17 7M17 7H8M17 7v9" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </a>
        </div>
      </div>
    </div>,
    document.body,
  );
}

/** Lead image with a clickable thumbnail strip that swaps the active image. */
function Gallery({ listing, delisted }: { listing: Listing; delisted: boolean }) {
  const images: string[] = [];
  for (const u of [listing.thumbnailUrl, ...(listing.imageUrls ?? [])]) {
    if (u && !images.includes(u)) images.push(u);
  }
  const [active, setActive] = useState(0);

  if (images.length === 0) {
    return (
      <div className="flex aspect-[16/9] items-center justify-center border-b-2 border-border bg-surface-2 text-muted/50">
        <svg className="h-12 w-12" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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

  const safeActive = Math.min(active, images.length - 1);

  return (
    <div>
      <div className="relative aspect-[16/9] overflow-hidden border-b-2 border-border bg-surface-2">
        <Image
          src={images[safeActive]}
          alt={listing.title}
          fill
          className={`object-cover ${delisted ? "grayscale" : ""}`}
          sizes="(max-width: 768px) 100vw, 768px"
          priority
        />
      </div>
      {images.length > 1 && (
        <div className="flex gap-1.5 overflow-x-auto p-1.5">
          {images.map((src, i) => (
            <button
              key={src}
              onClick={() => setActive(i)}
              aria-label={`Image ${i + 1}`}
              className={`relative aspect-[4/3] w-20 shrink-0 overflow-hidden ${
                i === safeActive ? "ring-2 ring-signal ring-offset-1 ring-offset-surface" : "opacity-70 hover:opacity-100"
              }`}
            >
              <Image src={src} alt="" fill className="object-cover" sizes="80px" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
