"use client";

import type { Filters } from "@/lib/filters";

const FIELD =
  "rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-sm text-zinc-100 focus:border-zinc-500 focus:outline-none";
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
    <div className="mb-4 flex flex-wrap items-end gap-3 rounded-lg border border-zinc-800 bg-zinc-900/40 p-3">
      <Field label="District">
        {/* District options are derived from the loaded listings for now;
            TODO: source these from the feeds config once it exists. */}
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
          <span className="text-zinc-500">–</span>
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
          <span className="text-zinc-500">–</span>
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
          className="rounded bg-zinc-800 px-3 py-1.5 text-sm text-zinc-300 hover:text-zinc-100"
        >
          Reset
        </button>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-xs text-zinc-400">
      {label}
      {children}
    </label>
  );
}
