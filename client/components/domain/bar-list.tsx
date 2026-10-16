"use client";

import Link from "next/link";
import { useState, type ReactNode } from "react";
import { cn } from "@/lib/format";

export interface BarRow {
  key: string;
  label: ReactNode;
  value: number;
  display: string;
  detail?: string;
  href?: string;
}

/**
 * Horizontal bar list for one measure (one hue, magnitude). Every bar carries a direct value label
 * and an optional detail line; hovering a row dims the others to focus it.
 */
export function BarList({ rows, max, emptyText = "No data" }: { rows: BarRow[]; max?: number; emptyText?: string }) {
  const [hovered, setHovered] = useState<string | null>(null);
  const top = max ?? Math.max(1, ...rows.map((r) => r.value));
  if (rows.length === 0) return <p className="py-6 text-center text-[14px] text-fg-3">{emptyText}</p>;

  return (
    <ul className="space-y-3" onMouseLeave={() => setHovered(null)}>
      {rows.map((row) => {
        const content = (
          <>
            <div className="mb-1.5 flex items-baseline justify-between gap-3 text-[13px]">
              <span className="truncate font-medium">{row.label}</span>
              <span className="shrink-0 font-semibold tabular">{row.display}</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-[var(--viz-track)]">
              <div
                className="h-full rounded-full bg-[var(--viz-1)] transition-[width] duration-700 ease-out"
                style={{ width: `${Math.max(row.value > 0 ? 2 : 0, Math.min(100, (row.value / top) * 100))}%` }}
              />
            </div>
            {row.detail && <div className="mt-1 text-[12px] text-fg-3">{row.detail}</div>}
          </>
        );
        return (
          <li
            key={row.key}
            onMouseEnter={() => setHovered(row.key)}
            className={cn("transition-opacity", hovered && hovered !== row.key && "opacity-55")}
          >
            {row.href ? (
              <Link href={row.href} className="block rounded-lg focus-visible:outline-offset-4">
                {content}
              </Link>
            ) : (
              content
            )}
          </li>
        );
      })}
    </ul>
  );
}
