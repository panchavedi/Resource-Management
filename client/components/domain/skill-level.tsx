import type { EvidenceStatus } from "@/lib/api/types";
import { cn, evidenceMeta, levelName } from "@/lib/format";
import { Star } from "lucide-react";

const ratingColors = [
  { filled: "fill-fg-3 text-fg-3", empty: "text-fg-3/25" },
  { filled: "fill-yellow-400 text-yellow-500", empty: "text-yellow-700/25" },
  { filled: "fill-orange-400 text-orange-500", empty: "text-orange-700/25" },
  { filled: "fill-pink-400 text-pink-500", empty: "text-pink-700/25" },
  { filled: "fill-sky-400 text-sky-500", empty: "text-sky-700/25" },
  { filled: "fill-violet-400 text-violet-500", empty: "text-violet-700/25" },
];

const evidenceCellColors: Record<EvidenceStatus, string> = {
  VALIDATED: "bg-green-100 ring-green-300 dark:bg-green-400/15 dark:ring-green-400/30",
  STALE: "bg-amber-100 ring-amber-300 dark:bg-amber-400/15 dark:ring-amber-400/30",
  UNVALIDATED: "bg-gray-100 ring-gray-300 dark:bg-gray-400/15 dark:ring-gray-400/30",
  MISSING: "bg-gray-100 ring-gray-300 dark:bg-gray-400/15 dark:ring-gray-400/30",
};

/**
 * Five-segment proficiency indicator. Filled segments = stored level; a ring marks the required level.
 * `level === null` renders an explicit "no record" state rather than zero.
 */
export function LevelBar({ level, required, size = "md" }: { level: number | null; required?: number; size?: "sm" | "md" }) {
  if (level === null) {
    return <span className="text-[12px] text-fg-3 italic">No record</span>;
  }
  const meets = required === undefined || level >= required;
  return (
    <span className="inline-flex items-center gap-0.5" role="img" aria-label={`Level ${level} of 5 (${levelName(level)})${required ? `, requires ${required}` : ""}`}>
      {[1, 2, 3, 4, 5].map((i) => (
        <Star
          key={i}
          className={cn(size === "sm" ? "size-3" : "size-4", i <= level ? ratingColors[level].filled : ratingColors[level].empty, !meets && i <= level && "opacity-60", required === i && "drop-shadow-[0_0_2px_var(--fg)]")}
          aria-hidden="true"
        />
      ))}
    </span>
  );
}

/** Compact star rating in the matrix. Evidence remains available in the cell label and tooltip. */
export function LevelCell({ level, status }: { level: number; status: EvidenceStatus }) {
  return (
    <span
      className={cn("inline-flex h-9 min-w-14 items-center justify-center gap-0.5 rounded-xl px-1 ring-1 ring-inset", evidenceCellColors[status])}
      title={`${levelName(level)} · ${evidenceMeta[status].label} — ${evidenceMeta[status].hint}`}
      role="img"
      aria-label={`Level ${level} of 5, ${levelName(level)} · ${evidenceMeta[status].label}`}
    >
      {[1, 2, 3, 4, 5].map((i) => <Star key={i} aria-hidden="true" className={cn("size-3", i <= level ? ratingColors[level].filled : ratingColors[level].empty)} />)}
    </span>
  );
}
