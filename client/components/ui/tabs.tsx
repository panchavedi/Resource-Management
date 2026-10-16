"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/format";

/** Underline tabs for switching sections of a page. */
export function Tabs<T extends string>({ value, onChange, tabs }: { value: T; onChange: (value: T) => void; tabs: { value: T; label: ReactNode; count?: number }[] }) {
  return (
    <div role="tablist" className="scrollbar-thin flex gap-6 overflow-x-auto overflow-y-hidden border-b border-line">
      {tabs.map((tab) => (
        <button
          key={tab.value}
          role="tab"
          aria-selected={tab.value === value}
          onClick={() => onChange(tab.value)}
          className={cn(
            "-mb-px flex shrink-0 items-center gap-2 border-b-2 pb-3 text-[15px] font-medium transition-colors",
            tab.value === value ? "border-fg text-fg" : "border-transparent text-fg-2 hover:text-fg",
          )}
        >
          {tab.label}
          {tab.count !== undefined && (
            <span className="rounded-full bg-surface-3 px-2 py-0.5 text-[11px] font-semibold text-fg-2 tabular">{tab.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}
