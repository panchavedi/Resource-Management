"use client";

import { useState } from "react";
import type { Availability, AvailabilityWindow } from "@/lib/api/types";
import { cn, formatShortDate } from "@/lib/format";

type Kind = "fits" | "partial" | "full";

function kindOf(w: AvailabilityWindow): Kind {
  if (w.meetsRequest) return "fits";
  return w.freePercent > 0 ? "partial" : "full";
}

const fill: Record<Kind, string> = {
  fits: "bg-[var(--viz-good)]",
  partial: "bg-[var(--viz-warn)]",
  full: "bg-[var(--viz-bad)]",
};

const legend: Record<Kind, string> = {
  fits: "Free for the request",
  partial: "Some capacity",
  full: "Fully booked",
};

function describe(w: AvailabilityWindow) {
  return `${formatShortDate(w.startDate)} – ${formatShortDate(w.endDate)} · ${w.days} day${w.days === 1 ? "" : "s"} · ${w.loadPercent}% booked (${w.confirmedLoadPercent}% approved) · ${w.freePercent}% free`;
}

/**
 * Day-proportional availability timeline. Colour shows whether the requested percentage fits;
 * the legend and the hover read-out carry the same information in text.
 */
export function AvailabilityBar({ availability, compact = false }: { availability: Availability; compact?: boolean }) {
  const [hovered, setHovered] = useState<AvailabilityWindow | null>(null);
  const kinds = new Set(availability.windows.map(kindOf));

  return (
    <div className="w-full">
      <div
        className={cn("flex w-full gap-[2px] overflow-hidden rounded-full", compact ? "h-2" : "h-3")}
        role="img"
        aria-label={`Availability: ${availability.availableDays} of ${availability.totalDays} days fit ${availability.requestedPercent}%`}
        onMouseLeave={() => setHovered(null)}
      >
        {availability.windows.map((w) => (
          <div
            key={w.startDate}
            title={describe(w)}
            onMouseEnter={() => setHovered(w)}
            className={cn("h-full min-w-[3px] transition-opacity", fill[kindOf(w)], hovered && hovered !== w && "opacity-50")}
            style={{ flexGrow: w.days, flexBasis: 0 }}
          />
        ))}
      </div>
      {!compact && (
        <div className="mt-2 flex flex-wrap items-center justify-between gap-x-4 gap-y-1 text-[12px] text-fg-3">
          <span className="tabular">{hovered ? describe(hovered) : `${formatShortDate(availability.startDate)} → ${formatShortDate(availability.endDate)}`}</span>
          <span className="flex gap-3">
            {(["fits", "partial", "full"] as Kind[])
              .filter((k) => kinds.has(k))
              .map((k) => (
                <span key={k} className="inline-flex items-center gap-1.5">
                  <span className={cn("size-2 rounded-full", fill[k])} />
                  {legend[k]}
                </span>
              ))}
          </span>
        </div>
      )}
    </div>
  );
}
