"use client";

import { useState } from "react";
import type { Filters } from "@/lib/filters";

const FIELD =
  "border border-border bg-surface px-2 py-1 text-sm text-text focus:border-signal focus:outline-none";
const NUM = `${FIELD} w-20`;

const MONTHS: [string, string][] = [
  ["01", "Jan"], ["02", "Feb"], ["03", "Mar"], ["04", "Apr"], ["05", "May"], ["06", "Jun"],
  ["07", "Jul"], ["08", "Aug"], ["09", "Sep"], ["10", "Oct"], ["11", "Nov"], ["12", "Dec"],
];

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

  // "Available by" is stored as "YYYY-MM". The two dropdowns hold their own state so a
  // half-made selection stays visible; the filter only applies once both are chosen.
  const [availMonth, setAvailMonth] = useState(() =>
    filters.availableBy ? filters.availableBy.slice(5, 7) : "",
  );
  const [availYear, setAvailYear] = useState(() =>
    filters.availableBy ? filters.availableBy.slice(0, 4) : "",
  );
  const thisYear = new Date().getFullYear();
  const years = [thisYear, thisYear + 1, thisYear + 2];

  const applyAvail = (month: string, year: string) => {
    setAvailMonth(month);
    setAvailYear(year);
    set({ availableBy: month && year ? `${year}-${month}` : "" });
  };
  const resetAll = () => {
    setAvailMonth("");
    setAvailYear("");
    onReset();
  };

  return (
    <div className="mb-4 flex flex-wrap items-end gap-x-4 gap-y-3 border-b border-line pb-4">
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

      <Field label="Available by">
        <div className="flex items-center gap-1">
          <select
            value={availMonth}
            onChange={(e) => applyAvail(e.target.value, availYear)}
            className={FIELD}
            aria-label="Available by month"
          >
            <option value="">Month</option>
            {MONTHS.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
          <select
            value={availYear}
            onChange={(e) => applyAvail(availMonth, e.target.value)}
            className={FIELD}
            aria-label="Available by year"
          >
            <option value="">Year</option>
            {years.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
          <label
            className="ml-1 flex cursor-pointer select-none items-center gap-1.5 normal-case text-muted"
            title="Also hide listings with no known move-in date"
          >
            <input
              type="checkbox"
              checked={filters.availableExact}
              onChange={(e) => set({ availableExact: e.target.checked })}
              className="h-4 w-4 accent-signal"
            />
            Exact
          </label>
        </div>
      </Field>

      {active && (
        <button
          onClick={resetAll}
          className="border border-border px-3 py-1.5 text-sm font-semibold uppercase tracking-wide transition-colors hover:bg-border hover:text-bg"
        >
          Reset
        </button>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-muted">
      {label}
      {children}
    </label>
  );
}
