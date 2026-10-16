import Link from "next/link";

export default function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="eyebrow">404</div>
      <h1 className="display text-[44px] font-semibold">This page isn&apos;t here.</h1>
      <p className="max-w-md text-[17px] text-fg-2">The link may be outdated or the record may have been removed.</p>
      <Link href="/" className="text-[17px] text-accent hover:underline">
        Back to overview
      </Link>
    </main>
  );
}
