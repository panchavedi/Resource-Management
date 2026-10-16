"use client";

import { AlertTriangle, ChevronDown, Info, MapPin } from "lucide-react";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import type { Candidate } from "@/lib/api/types";
import { cn, formatDate, initials, locationFitMeta } from "@/lib/format";
import { AvailabilityBadge, EvidenceBadge } from "./badges";
import { AvailabilityBar } from "./availability-bar";
import { ScoreBreakdown, ScoreRing } from "./score";
import { LevelBar } from "./skill-level";

/** Evidence table: each required skill against the stored proficiency and its validation. */
export function SkillEvidenceTable({ candidate }: { candidate: Candidate }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[520px] text-left text-[13px]">
        <thead className="text-[12px] text-fg-3">
          <tr className="border-b border-line">
            <th className="py-2 pr-3 font-medium">Skill</th>
            <th className="py-2 pr-3 font-medium">Stored vs required</th>
            <th className="py-2 pr-3 font-medium">Points</th>
            <th className="py-2 font-medium">Evidence</th>
          </tr>
        </thead>
        <tbody>
          {candidate.skillMatches.map((s) => (
            <tr key={s.skillId} className="border-b border-line last:border-0 align-top">
              <td className="py-2.5 pr-3">
                <div className="font-medium">{s.skillName}</div>
                <div className="text-[11px] text-fg-3">{s.mandatory ? "Mandatory" : "Optional"} · needs {s.requiredLevel}</div>
              </td>
              <td className="py-2.5 pr-3">
                <LevelBar level={s.actualLevel} required={s.requiredLevel} />
              </td>
              <td className="py-2.5 pr-3 tabular" title={s.explanation}>
                {s.points.toFixed(2)}
              </td>
              <td className="py-2.5">
                <EvidenceBadge status={s.evidenceStatus} />
                {s.evidence && <div className="mt-1 max-w-[260px] text-[12px] text-fg-3">{s.evidence}{s.lastValidatedAt ? ` · ${formatDate(s.lastValidatedAt)}` : ""}</div>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function CandidateCard({ candidate, selectable, selected, onSelect, action, index = 0 }: { candidate: Candidate; selectable?: boolean; selected?: boolean; onSelect?: () => void; action?: ReactNode; index?: number }) {
  const [open, setOpen] = useState(false);
  const c = candidate;

  return (
    <article className={cn("card animate-fade-up overflow-hidden transition-shadow", selected && "ring-2 ring-accent")} style={{ animationDelay: `${index * 40}ms` }}>
      <div className="flex flex-col gap-5 p-5 md:flex-row md:items-center">
        <div className="flex min-w-0 flex-1 items-center gap-4">
          {selectable && (
            <input
              type="checkbox"
              checked={selected}
              onChange={onSelect}
              aria-label={`Select ${c.name} for comparison`}
              className="size-5 shrink-0 accent-[var(--accent)]"
            />
          )}
          <div className="flex size-11 shrink-0 items-center justify-center rounded-full bg-surface-3 text-[14px] font-semibold text-fg-2">{initials(c.name)}</div>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              {c.rank && <span className="text-[13px] font-semibold text-fg-3 tabular">#{c.rank}</span>}
              <Link href={`/people/${c.employeeId}`} className="truncate text-[17px] font-semibold tracking-tight hover:underline">
                {c.name}
              </Link>
              {!c.eligible && c.rank && <span className="rounded-full bg-warn-soft px-2 py-0.5 text-[11px] font-medium text-warn">Flagged</span>}
            </div>
            <div className="mt-0.5 flex flex-wrap items-center gap-x-3 text-[13px] text-fg-2">
              <span>{c.jobTitle ?? "—"}</span>
              <span className="inline-flex items-center gap-1">
                <MapPin className="size-3.5" /> {c.location ?? "Location not recorded"} · {locationFitMeta[c.locationFit.fit]}
              </span>
            </div>
          </div>
        </div>
        <div className="flex flex-1 flex-col gap-2 md:max-w-[340px]">
          <div className="flex items-center justify-between gap-2">
            <AvailabilityBadge status={c.availability.status} />
            <span className="text-[12px] text-fg-3 tabular">
              {c.availability.availableDays}/{c.availability.totalDays} days · {c.availability.coveragePercent}%
            </span>
          </div>
          <AvailabilityBar availability={c.availability} compact />
        </div>
        <div className="flex items-center gap-3">
          <ScoreRing score={c.totalScore} muted={!c.eligible} />
          {action}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-line px-5 py-3">
        {c.skillMatches.map((s) => (
          <span key={s.skillId} className="inline-flex items-center gap-2 text-[12px] text-fg-2">
            {s.skillName}
            <LevelBar level={s.actualLevel} required={s.requiredLevel} size="sm" />
          </span>
        ))}
        <button
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="ml-auto inline-flex items-center gap-1 text-[13px] font-medium text-accent"
        >
          Why this score <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} />
        </button>
      </div>

      {open && (
        <div className="grid gap-6 border-t border-line bg-surface-2/50 p-5 lg:grid-cols-[1fr_1.4fr]">
          <div className="min-w-0">
            <div className="eyebrow mb-3">Score breakdown</div>
            <ScoreBreakdown components={c.scoreBreakdown} />
          </div>
          <div className="min-w-0 space-y-4">
            <div>
              <div className="eyebrow mb-2">Skill evidence</div>
              <SkillEvidenceTable candidate={c} />
            </div>
            <div>
              <div className="eyebrow mb-2">Availability</div>
              <AvailabilityBar availability={c.availability} />
            </div>
            {(c.flags.length > 0 || c.dataGaps.length > 0 || c.exclusionReasons.length > 0) && (
              <ul className="space-y-1.5 text-[13px]">
                {c.exclusionReasons.map((r) => (
                  <li key={r} className="flex gap-2 text-bad">
                    <AlertTriangle className="mt-0.5 size-3.5 shrink-0" /> {r}
                  </li>
                ))}
                {c.flags.map((f) => (
                  <li key={f} className="flex gap-2 text-warn">
                    <AlertTriangle className="mt-0.5 size-3.5 shrink-0" /> {f}
                  </li>
                ))}
                {c.dataGaps.map((g) => (
                  <li key={g} className="flex gap-2 text-fg-2">
                    <Info className="mt-0.5 size-3.5 shrink-0" /> Not on record: {g}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </article>
  );
}
