import Link from "next/link";
import { FeedEditor } from "@/components/FeedEditor";
import { ThemeToggle } from "@/components/ThemeToggle";

export const dynamic = "force-dynamic";

export default function FeedsAdminPage() {
  return (
    <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <header className="mb-6 flex items-end justify-between border-b-2 border-border pb-4">
        <h1 className="font-display text-3xl font-extrabold leading-none tracking-tight">
          Feed configuration<span className="text-signal">.</span>
        </h1>
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <Link
            href="/"
            className="border border-border px-3 py-1.5 text-sm font-semibold uppercase tracking-wide transition-colors hover:bg-border hover:text-bg"
          >
            Listings
          </Link>
        </div>
      </header>
      <FeedEditor />
    </main>
  );
}
