"use client";

import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

// Marker colors chosen to read on both the light and (inverted) dark basemap.
const SIGNAL = "#f5a623";
const REMOVED = "#94a3b8";
const NORMAL = "#3b82f6";

function colorFor(l: Listing): string {
  if (l.delistedAt != null) return REMOVED;
  if (isNew(l.timestamp)) return SIGNAL;
  return NORMAL;
}

function tooltip(l: Listing): string {
  const rent = l.totalRent != null ? `€${l.totalRent}` : "—";
  const where = l.district ?? l.location;
  // Text nodes only (escaped) to keep viewer-supplied strings inert.
  const div = document.createElement("div");
  div.innerHTML = `<strong></strong><br><span></span>`;
  div.querySelector("strong")!.textContent = `${rent} · ${where}`;
  div.querySelector("span")!.textContent = l.title;
  return div.innerHTML;
}

export function ListingsMap({
  listings,
  onSelect,
}: {
  listings: Listing[];
  onSelect: (listing: Listing) => void;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const layerRef = useRef<L.LayerGroup | null>(null);
  // Keep the latest onSelect without re-running the marker effect.
  const selectRef = useRef(onSelect);
  selectRef.current = onSelect;

  // Init the map once.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = L.map(containerRef.current, { scrollWheelZoom: true }).setView([51.34, 12.37], 12);
    // Keyless OSM tiles, darkened via CSS (.map-tiles in globals) to match the theme.
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
      className: "map-tiles",
    }).addTo(map);
    layerRef.current = L.layerGroup().addTo(map);
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
      layerRef.current = null;
    };
  }, []);

  // (Re)draw markers whenever the listing set changes.
  useEffect(() => {
    const map = mapRef.current;
    const layer = layerRef.current;
    if (!map || !layer) return;
    layer.clearLayers();

    const located = listings.filter((l) => l.lat != null && l.lon != null);
    const points: L.LatLngExpression[] = [];
    for (const l of located) {
      const color = colorFor(l);
      const marker = L.circleMarker([l.lat!, l.lon!], {
        radius: 7,
        color,
        weight: 2,
        fillColor: color,
        fillOpacity: 0.75,
      });
      marker.bindTooltip(tooltip(l), { direction: "top", offset: [0, -6] });
      marker.on("click", () => selectRef.current(l));
      marker.addTo(layer);
      points.push([l.lat!, l.lon!]);
    }
    if (points.length > 0) {
      map.fitBounds(L.latLngBounds(points).pad(0.2), { maxZoom: 15 });
    }
  }, [listings]);

  const withoutCoords = listings.filter((l) => l.lat == null || l.lon == null).length;

  return (
    <div className="relative overflow-hidden rounded-xl border border-border">
      <div ref={containerRef} className="h-[70vh] min-h-[24rem] w-full bg-surface-2" />

      <div className="pointer-events-none absolute right-3 top-3 z-[1000] flex flex-wrap gap-3 rounded-md border border-border bg-bg/80 px-3 py-1.5 text-xs text-muted backdrop-blur-sm">
        <Dot color={SIGNAL} label="New" />
        <Dot color={NORMAL} label="Listed" />
        <Dot color={REMOVED} label="Removed" />
      </div>

      {withoutCoords > 0 && (
        <div className="absolute bottom-3 left-3 z-[1000] rounded-md border border-border bg-bg/80 px-3 py-1.5 text-xs text-muted backdrop-blur-sm">
          <span className="tnum text-text">{withoutCoords}</span> without a location, not shown
        </div>
      )}
    </div>
  );
}

function Dot({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="h-2 w-2 rounded-full" style={{ backgroundColor: color }} aria-hidden="true" />
      {label}
    </span>
  );
}
