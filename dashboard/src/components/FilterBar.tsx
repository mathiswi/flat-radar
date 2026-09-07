"use client";

import type { Filters } from "@/lib/filters";

const FIELD =
  "rounded-md border border-border bg-surface px-2 py-1 text-sm text-text focus:border-signal focus:outline-none";
const NUM = `${FIELD} w-20`;

export function FilterBar({
  filters,
  districts,
  onChange,
  onReset,
  active,
}: {
  filters: Filters;
  districts: string[];
  onChange: (f: Filters) => void;
  onReset: () => void;
  active: boolean;
}) {
  const set = (patch: Partial<Filters>) => onChange({ ...filters, ...patch });

  return (
    <div className="mb-4 flex flex-wrap items-end gap-3 rounded-xl border border-border bg-surface/40 p-3">
      <Field label="District">
        {/* Options come from the configured feeds (see ViewToggle), unioned with
            districts present in the listings. */}
        <select
          value={filters.district}
          onChange={(e) => set({ district: e.target.value })}
          className={FIELD}
        >
          <option value="">All</option>
          {districts.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
      </Field>

      <Field label="Source">
        <select
          value={filters.source}
          onChange={(e) => set({ source: e.target.value })}
          className={FIELD}
        >
          <option value="">All</option>
          <option value="kleinanzeigen">kleinanzeigen</option>
          <option value="immoscout24">immoscout24</option>
        </select>
      </Field>

      <Field label="Rent €">
        <div className="flex items-center gap-1">
          <input
            type="number"
            inputMode="numeric"
            placeholder="min"
            value={filters.minRent}
            onChange={(e) => set({ minRent: e.target.value })}
            className={NUM}
          />
          <span className="text-muted">–</span>
          <input
            type="number"
            inputMode="numeric"
            placeholder="max"
            value={filters.maxRent}
            onChange={(e) => set({ maxRent: e.target.value })}
            className={NUM}
          />
        </div>
      </Field>

      <Field label="Size m²">
        <div className="flex items-center gap-1">
          <input
            type="number"
            inputMode="numeric"
            placeholder="min"
            value={filters.minSize}
            onChange={(e) => set({ minSize: e.target.value })}
            className={NUM}
          />
          <span className="text-muted">–</span>
          <input
            type="number"
            inputMode="numeric"
            placeholder="max"
            value={filters.maxSize}
            onChange={(e) => set({ maxSize: e.target.value })}
            className={NUM}
          />
        </div>
      </Field>

      <Field label="Min rooms">
        <input
          type="number"
          inputMode="decimal"
          step="0.5"
          placeholder="any"
          value={filters.minRooms}
          onChange={(e) => set({ minRooms: e.target.value })}
          className={NUM}
        />
      </Field>

      {active && (
        <button
          onClick={onReset}
          className="rounded-md border border-border px-3 py-1.5 text-sm text-muted transition-colors hover:border-muted hover:text-text"
        >
          Reset
        </button>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-xs text-muted">
      {label}
      {children}
    </label>
  );
}
