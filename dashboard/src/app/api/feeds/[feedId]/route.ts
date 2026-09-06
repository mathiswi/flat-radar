import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

type Params = { params: Promise<{ feedId: string }> };

export async function PUT(req: NextRequest, { params }: Params) {
  const { feedId } = await params;
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/feeds/${encodeURIComponent(feedId)}`, {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: await req.text(),
    });
    return new NextResponse(await res.text(), {
      status: res.status,
      headers: { "content-type": "application/json" },
    });
  } catch {
    return NextResponse.json({ error: "unreachable" }, { status: 502 });
  }
}

export async function DELETE(_req: NextRequest, { params }: Params) {
  const { feedId } = await params;
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/feeds/${encodeURIComponent(feedId)}`, {
      method: "DELETE",
    });
    return new NextResponse(await res.text(), {
      status: res.status,
      headers: { "content-type": "application/json" },
    });
  } catch {
    return NextResponse.json({ error: "unreachable" }, { status: 502 });
  }
}
