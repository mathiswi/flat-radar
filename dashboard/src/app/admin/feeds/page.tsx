import Link from "next/link";
import { FeedEditor } from "@/components/FeedEditor";

export const dynamic = "force-dynamic";

export default function FeedsAdminPage() {
  return (
    <main className="mx-auto max-w-6xl px-4 py-8">
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-3xl font-bold">Feed configuration</h1>
        <Link href="/" className="text-sm text-zinc-400 hover:text-zinc-100">
          ← Listings
        </Link>
      </div>
      <FeedEditor />
    </main>
  );
}
