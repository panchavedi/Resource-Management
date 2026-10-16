"use client";

import { Card } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/feedback";

/** Route-level error boundary: keeps the navigation usable when a page crashes. */
export default function AppError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <Card>
      <ErrorState error={error} onRetry={reset} />
    </Card>
  );
}
