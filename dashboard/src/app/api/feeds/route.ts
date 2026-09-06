import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export async function GET() {
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/feeds`, { cache: "no-store" });
    if (!res.ok) {
      return NextResponse.json({ error: `upstream: ${res.status}` }, { status: 502 });
    }
    return NextResponse.json(await res.json());
  } catch {
    return NextResponse.json({ error: "unreachable" }, { status: 502 });
  }
}

export async function POST(req: NextRequest) {
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/feeds`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: await req.text(),
    });
    // Pass the backend's status and body through so the editor sees validation errors (400).
    return new NextResponse(await res.text(), {
      status: res.status,
      headers: { "content-type": "application/json" },
    });
  } catch {
    return NextResponse.json({ error: "unreachable" }, { status: 502 });
  }
}
