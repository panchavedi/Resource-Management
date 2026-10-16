import { AlertTriangle, Info, RefreshCw, Sparkles, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { ApiError } from "@/lib/api/client";
import { cn } from "@/lib/format";

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={cn("inline-block size-5 animate-[orbit_0.8s_linear_infinite] rounded-full border-2 border-current border-r-transparent", className)}
    />
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn("skeleton", className)} />;
}

export function SkeletonRows({ rows = 4, className }: { rows?: number; className?: string }) {
  return (
    <div className={cn("space-y-3", className)} aria-busy>
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-16" />
      ))}
    </div>
  );
}

export function EmptyState({ icon: Icon = Sparkles, title, children, action }: { icon?: LucideIcon; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 px-6 py-14 text-center">
      <div className="flex size-12 items-center justify-center rounded-full bg-surface-2 text-fg-3">
        <Icon className="size-6" strokeWidth={1.6} />
      </div>
      <div className="text-[17px] font-semibold">{title}</div>
      {children && <p className="max-w-md text-[14px] text-fg-2">{children}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof ApiError ? error.message : "Something went wrong while loading this data.";
  return (
    <div role="alert" className="flex flex-col items-center gap-3 px-6 py-12 text-center">
      <div className="flex size-12 items-center justify-center rounded-full bg-bad-soft text-bad">
        <AlertTriangle className="size-6" strokeWidth={1.8} />
      </div>
      <div className="text-[17px] font-semibold">Couldn&apos;t load this</div>
      <p className="max-w-md text-[14px] text-fg-2">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="inline-flex items-center gap-1.5 text-[14px] font-medium text-accent hover:underline">
          <RefreshCw className="size-4" /> Try again
        </button>
      )}
    </div>
  );
}

const noticeTones = {
  info: "bg-accent-soft text-fg",
  warn: "bg-warn-soft text-fg",
  bad: "bg-bad-soft text-fg",
  ai: "bg-ai-soft text-fg",
  good: "bg-good-soft text-fg",
};

export function Notice({ tone = "info", title, children, icon: Icon = Info, className }: { tone?: keyof typeof noticeTones; title?: ReactNode; children?: ReactNode; icon?: LucideIcon; className?: string }) {
  const iconColor = { info: "text-accent", warn: "text-warn", bad: "text-bad", ai: "text-ai", good: "text-good" }[tone];
  return (
    <div className={cn("flex gap-3 rounded-2xl px-4 py-3 text-[14px]", noticeTones[tone], className)}>
      <Icon className={cn("mt-0.5 size-4 shrink-0", iconColor)} strokeWidth={2} />
      <div className="min-w-0 space-y-0.5">
        {title && <div className="font-semibold">{title}</div>}
        {children && <div className="text-fg-2">{children}</div>}
      </div>
    </div>
  );
}
