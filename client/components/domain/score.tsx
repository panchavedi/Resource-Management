import type { ScoreComponent } from "@/lib/api/types";
import { cn, formatScore } from "@/lib/format";

/** Circular score gauge (0–100). */
export function ScoreRing({ score, size = 56, muted = false }: { score: number; size?: number; muted?: boolean }) {
  const stroke = size >= 56 ? 5 : 4;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }} role="img" aria-label={`Score ${formatScore(score)} of 100`}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--viz-track)" strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke={muted ? "var(--fg-3)" : "var(--viz-1)"}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={c * (1 - Math.min(100, Math.max(0, score)) / 100)}
          className="transition-[stroke-dashoffset] duration-700"
        />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-[15px] font-semibold tabular">{formatScore(score)}</span>
    </div>
  );
}

/** Horizontal bar 0–100 with a value label; single hue (magnitude). */
export function Meter({ value, max = 100, className, label }: { value: number; max?: number; className?: string; label?: string }) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  return (
    <div className={cn("h-1.5 w-full overflow-hidden rounded-full bg-[var(--viz-track)]", className)} role="meter" aria-valuenow={value} aria-valuemin={0} aria-valuemax={max} aria-label={label}>
      <div className="h-full rounded-full bg-[var(--viz-1)] transition-[width] duration-700" style={{ width: `${pct}%` }} />
    </div>
  );
}

/** The four scoring factors with weight, raw score, contribution and the server's explanation. */
export function ScoreBreakdown({ components }: { components: ScoreComponent[] }) {
  return (
    <ul className="space-y-3">
      {components.map((c) => (
        <li key={c.factor}>
          <div className="mb-1.5 flex items-baseline justify-between gap-3 text-[13px]">
            <span className="font-medium">
              {c.label} <span className="font-normal text-fg-3">× {c.weight.toFixed(2)}</span>
            </span>
            <span className="tabular text-fg-2">
              {formatScore(c.score)} → <span className="font-semibold text-fg">+{formatScore(c.weightedScore)}</span>
            </span>
          </div>
          <Meter value={c.score} label={`${c.label} ${c.score}`} />
          <p className="mt-1 text-[12px] text-fg-3">{c.explanation}</p>
        </li>
      ))}
    </ul>
  );
}
