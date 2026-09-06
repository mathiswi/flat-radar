"use client";

import { useEffect, useState } from "react";
import type { Feed } from "@/lib/types";

const FIELD =
  "rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-sm text-zinc-100 focus:border-zinc-500 focus:outline-none";

const SOURCES = ["kleinanzeigen", "immoscout24"];

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
  const [loadError, setLoadError] = useState<string | null>(null);
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
    await fetch(`/api/feeds/${encodeURIComponent(feed.id)}`, {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ ...feed, enabled: !feed.enabled }),
    });
    await load();
  }

  async function remove(feed: Feed) {
    if (!confirm(`Delete feed "${feed.displayName}" (${feed.id})?`)) return;
    await fetch(`/api/feeds/${encodeURIComponent(feed.id)}`, { method: "DELETE" });
    await load();
  }

  // Mirrors the backend guard so the operator sees it before submitting.
  const c203Warning =
    draft && draft.source === "kleinanzeigen" && draft.url.length > 0 && !draft.url.includes("c203")
      ? "Kleinanzeigen URL should be category-locked (contain 'c203') or it pulls cross-category junk."
      : null;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold">Feeds</h2>
        {!draft && (
          <button
            onClick={startAdd}
            className="rounded bg-zinc-700 px-3 py-1.5 text-sm font-medium text-zinc-100 hover:bg-zinc-600"
          >
            + Add feed
          </button>
        )}
      </div>

      {loadError && <p className="text-sm text-red-400">Failed to load feeds: {loadError}</p>}

      {draft && (
        <div className="space-y-3 rounded-lg border border-zinc-800 bg-zinc-900/40 p-4">
          <h3 className="text-sm font-medium text-zinc-300">
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
                {SOURCES.map((s) => (
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
            <label className="flex items-center gap-2 text-sm text-zinc-300">
              <input
                type="checkbox"
                className="accent-zinc-600"
                checked={draft.enabled}
                onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })}
              />
              Enabled
            </label>
          </div>

          {c203Warning && <p className="text-sm text-amber-400">{c203Warning}</p>}
          {formError && <p className="text-sm text-red-400">{formError}</p>}

          <div className="flex gap-2">
            <button
              onClick={save}
              disabled={saving}
              className="rounded bg-zinc-700 px-3 py-1.5 text-sm font-medium text-zinc-100 hover:bg-zinc-600 disabled:opacity-50"
            >
              {saving ? "Saving…" : "Save"}
            </button>
            <button
              onClick={cancel}
              className="rounded bg-zinc-800 px-3 py-1.5 text-sm text-zinc-300 hover:text-zinc-100"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="text-sm text-zinc-500">Loading…</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-zinc-800">
          <table className="w-full text-left text-sm">
            <thead className="bg-zinc-900/60 text-xs uppercase text-zinc-500">
              <tr>
                <th className="px-3 py-2">Feed</th>
                <th className="px-3 py-2">Source</th>
                <th className="px-3 py-2">District</th>
                <th className="px-3 py-2">Enabled</th>
                <th className="px-3 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {feeds.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-4 text-zinc-500">
                    No feeds configured. Add one to start scraping.
                  </td>
                </tr>
              )}
              {feeds.map((feed) => (
                <tr key={feed.id} className="border-t border-zinc-800">
                  <td className="px-3 py-2">
                    <div className="font-medium text-zinc-200">{feed.displayName}</div>
                    <a
                      href={feed.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="block max-w-md truncate text-xs text-zinc-500 hover:text-zinc-300"
                    >
                      {feed.id} · {feed.url}
                    </a>
                  </td>
                  <td className="px-3 py-2 text-zinc-400">{feed.source}</td>
                  <td className="px-3 py-2 text-zinc-400">{feed.district}</td>
                  <td className="px-3 py-2">
                    <button
                      onClick={() => toggleEnabled(feed)}
                      className={`rounded px-2 py-0.5 text-xs font-medium ${
                        feed.enabled
                          ? "bg-emerald-900/50 text-emerald-300"
                          : "bg-zinc-800 text-zinc-500"
                      }`}
                    >
                      {feed.enabled ? "on" : "off"}
                    </button>
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex justify-end gap-2">
                      <button
                        onClick={() => startEdit(feed)}
                        className="text-xs text-zinc-400 hover:text-zinc-100"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => remove(feed)}
                        className="text-xs text-red-400 hover:text-red-300"
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
    <label className="flex flex-col gap-1 text-xs text-zinc-400">
      {label}
      {children}
    </label>
  );
}
