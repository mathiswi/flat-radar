import Link from "next/link";
import { FeedEditor } from "@/components/FeedEditor";

export const dynamic = "force-dynamic";

export default function FeedsAdminPage() {
  return (
    <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <header className="mb-6 flex items-center justify-between">
        <h1 className="font-display text-2xl font-semibold tracking-tight">Feed configuration</h1>
        <Link
          href="/"
          className="rounded-md border border-border px-3 py-1.5 text-sm text-muted transition-colors hover:border-muted hover:text-text"
        >
          Listings
        </Link>
      </header>
      <FeedEditor />
    </main>
  );
}
