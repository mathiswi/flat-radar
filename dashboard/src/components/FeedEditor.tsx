"use client";

import { useEffect, useState } from "react";
import type { Feed } from "@/lib/types";

const FIELD =
  "rounded-md border border-border bg-surface px-2 py-1 text-sm text-text focus:border-signal focus:outline-none";
const BTN_PRIMARY =
  "rounded-md bg-signal px-3 py-1.5 text-sm font-semibold text-on-signal transition-colors hover:brightness-110 disabled:opacity-50";
const BTN_SECONDARY =
  "rounded-md border border-border px-3 py-1.5 text-sm text-muted transition-colors hover:border-muted hover:text-text";

// Fallback for when /api/sources is unreachable; the live list is fetched from
// the backend (the one shared source of truth) so this copy can't cause drift.
const FALLBACK_SOURCES = ["kleinanzeigen", "immoscout24"];

// Mirrors the backend's KLEINANZEIGEN_CATEGORY guard: a 'c203' category segment
// anchored to a '/' or the URL bounds, not an incidental substring.
const KLEINANZEIGEN_CATEGORY = /(^|\/)c203(l\d+)?([/?#]|$)/;

const emptyFeed: Feed = {
  id: "",
  displayName: "",
  url: "",
  district: "",
  source: "kleinanzeigen",
  enabled: true,
};

export function FeedEditor() {
  const [feeds, setFeeds] = useState<Feed[]>([]);
  const [sources, setSources] = useState<string[]>(FALLBACK_SOURCES);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [draft, setDraft] = useState<Feed | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null); // null = adding new
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const res = await fetch("/api/feeds", { cache: "no-store" });
      if (!res.ok) throw new Error(`load failed: ${res.status}`);
      setFeeds(await res.json());
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : "load failed");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // Sources are optional chrome for the dropdown; if the fetch fails we keep
    // the fallback and don't surface an error.
    fetch("/api/sources", { cache: "no-store" })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) setSources(data);
      })
      .catch(() => {});
  }, []);

  function startAdd() {
    setDraft({ ...emptyFeed });
    setEditingId(null);
    setFormError(null);
  }

  function startEdit(feed: Feed) {
    setDraft({ ...feed });
    setEditingId(feed.id);
    setFormError(null);
  }

  function cancel() {
    setDraft(null);
    setEditingId(null);
    setFormError(null);
  }

  async function save() {
    if (!draft) return;
    setSaving(true);
    setFormError(null);
    try {
      const isNew = editingId === null;
      const res = await fetch(isNew ? "/api/feeds" : `/api/feeds/${encodeURIComponent(editingId)}`, {
        method: isNew ? "POST" : "PUT",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(draft),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? `save failed: ${res.status}`);
      }
      cancel();
      await load();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : "save failed");
    } finally {
      setSaving(false);
    }
  }

  async function toggleEnabled(feed: Feed) {
    setActionError(null);
    try {
      const res = await fetch(`/api/feeds/${encodeURIComponent(feed.id)}`, {
        method: "PUT",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ ...feed, enabled: !feed.enabled }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? `toggle failed: ${res.status}`);
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "toggle failed");
    }
    await load();
  }

  async function remove(feed: Feed) {
    if (!confirm(`Delete feed "${feed.displayName}" (${feed.id})?`)) return;
    setActionError(null);
    try {
      const res = await fetch(`/api/feeds/${encodeURIComponent(feed.id)}`, { method: "DELETE" });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.error ?? `delete failed: ${res.status}`);
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "delete failed");
    }
    await load();
  }

  // Mirrors the backend guard so the operator sees it before submitting.
  const c203Warning =
    draft && draft.source === "kleinanzeigen" && draft.url.length > 0 && !KLEINANZEIGEN_CATEGORY.test(draft.url)
      ? "Kleinanzeigen URL should be category-locked (a 'c203' category segment) or it pulls cross-category junk."
      : null;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <h2 className="font-display text-lg font-semibold">Feeds</h2>
        {!draft && (
          <button onClick={startAdd} className={BTN_PRIMARY}>
            Add feed
          </button>
        )}
      </div>

      {loadError && <p className="text-sm text-danger">Failed to load feeds: {loadError}</p>}
      {actionError && <p className="text-sm text-danger">{actionError}</p>}

      {draft && (
        <div className="space-y-3 rounded-xl border border-border bg-surface/40 p-4">
          <h3 className="text-sm font-medium text-text">
            {editingId === null ? "New feed" : `Edit ${editingId}`}
          </h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="id (unique)">
              <input
                className={`${FIELD} w-full disabled:opacity-50`}
                value={draft.id}
                disabled={editingId !== null}
                onChange={(e) => setDraft({ ...draft, id: e.target.value })}
              />
            </Field>
            <Field label="Display name">
              <input
                className={`${FIELD} w-full`}
                value={draft.displayName}
                onChange={(e) => setDraft({ ...draft, displayName: e.target.value })}
              />
            </Field>
            <Field label="Source">
              <select
                className={`${FIELD} w-full`}
                value={draft.source}
                onChange={(e) => setDraft({ ...draft, source: e.target.value })}
              >
                {sources.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="District">
              <input
                className={`${FIELD} w-full`}
                value={draft.district}
                onChange={(e) => setDraft({ ...draft, district: e.target.value })}
              />
            </Field>
            <div className="sm:col-span-2">
              <Field label="URL">
                <input
                  className={`${FIELD} w-full`}
                  value={draft.url}
                  onChange={(e) => setDraft({ ...draft, url: e.target.value })}
                />
              </Field>
            </div>
            <label className="flex items-center gap-2 text-sm text-text">
              <input
                type="checkbox"
                className="accent-signal"
                checked={draft.enabled}
                onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })}
              />
              Enabled
            </label>
          </div>

          {c203Warning && <p className="text-sm text-signal">{c203Warning}</p>}
          {formError && <p className="text-sm text-danger">{formError}</p>}

          <div className="flex gap-2">
            <button onClick={save} disabled={saving} className={BTN_PRIMARY}>
              {saving ? "Saving…" : "Save"}
            </button>
            <button onClick={cancel} className={BTN_SECONDARY}>
              Cancel
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="text-sm text-muted">Loading…</p>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-surface text-xs text-muted">
              <tr>
                <th className="px-3 py-3 font-medium">Feed</th>
                <th className="px-3 py-3 font-medium">Source</th>
                <th className="px-3 py-3 font-medium">District</th>
                <th className="px-3 py-3 font-medium">Enabled</th>
                <th className="px-3 py-3"></th>
              </tr>
            </thead>
            <tbody>
              {feeds.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-4 text-muted">
                    No feeds configured. Add one to start scraping.
                  </td>
                </tr>
              )}
              {feeds.map((feed) => (
                <tr key={feed.id} className="border-b border-border last:border-0">
                  <td className="px-3 py-3">
                    <div className="font-medium text-text">{feed.displayName}</div>
                    <a
                      href={feed.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="block max-w-md truncate text-xs text-muted hover:text-text"
                    >
                      {feed.id} · {feed.url}
                    </a>
                  </td>
                  <td className="px-3 py-3 text-muted">{feed.source}</td>
                  <td className="px-3 py-3 text-muted">{feed.district}</td>
                  <td className="px-3 py-3">
                    <button
                      onClick={() => toggleEnabled(feed)}
                      className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-semibold ${
                        feed.enabled
                          ? "bg-signal text-on-signal"
                          : "bg-surface-2 text-muted"
                      }`}
                    >
                      <span
                        className={`h-1.5 w-1.5 rounded-full ${feed.enabled ? "bg-on-signal/70" : "bg-muted"}`}
                        aria-hidden="true"
                      />
                      {feed.enabled ? "on" : "off"}
                    </button>
                  </td>
                  <td className="px-3 py-3">
                    <div className="flex justify-end gap-3">
                      <button
                        onClick={() => startEdit(feed)}
                        className="text-xs text-muted hover:text-text"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => remove(feed)}
                        className="text-xs text-danger hover:brightness-110"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
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
