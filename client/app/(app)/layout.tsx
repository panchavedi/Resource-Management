import { Suspense } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Spinner } from "@/components/ui/feedback";

/**
 * The signed-in area renders on the client only: the access token lives in sessionStorage, so the
 * server can prerender just the loading shell. Navigations therefore cannot be instant by design,
 * and this segment opts out of instant-navigation validation.
 */
export const instant = false;

/** Authenticated area. The shell reads the URL, so it renders behind Suspense (Cache Components). */
export default function AppLayout({ children }: LayoutProps<"/">) {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center text-fg-3">
          <Spinner />
        </div>
      }
    >
      <AppShell>{children}</AppShell>
    </Suspense>
  );
}
