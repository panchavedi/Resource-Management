import type { ComponentProps, ReactNode } from "react";
import { cn } from "@/lib/format";

export function Card({ className, ...rest }: ComponentProps<"div">) {
  return <div className={cn("card", className)} {...rest} />;
}

export function CardHeader({ title, subtitle, action, className }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div className={cn("flex items-start justify-between gap-4 px-6 pt-5 pb-3", className)}>
      <div className="min-w-0">
        <h3 className="text-[17px] font-semibold tracking-tight">{title}</h3>
        {subtitle && <p className="mt-0.5 text-[13px] text-fg-2">{subtitle}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

/** Large KPI tile; the number is the headline, the label explains it. */
export function Stat({ label, value, hint, tone, icon }: { label: string; value: ReactNode; hint?: ReactNode; tone?: "bad" | "warn" | "good"; icon?: ReactNode }) {
  return (
    <Card className="flex flex-col gap-3 p-5 animate-fade-up">
      <div className="flex items-center justify-between text-[13px] text-fg-2">
        <span>{label}</span>
        <span className="text-fg-3">{icon}</span>
      </div>
      <div
        className={cn(
          "display text-[34px] font-semibold",
          tone === "bad" && "text-bad",
          tone === "warn" && "text-warn",
          tone === "good" && "text-good",
        )}
      >
        {value}
      </div>
      {hint && <div className="text-[12px] text-fg-3">{hint}</div>}
    </Card>
  );
}
