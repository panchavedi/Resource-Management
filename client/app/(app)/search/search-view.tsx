"use client";

import { MapPin, SearchX, X } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { AvailabilityBadge } from "@/components/domain/badges";
import { AvailabilityBar } from "@/components/domain/availability-bar";
import { ProposeDialog } from "@/components/domain/propose-dialog";
import { LevelBar } from "@/components/domain/skill-level";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { Field, Input, LevelPicker, Select, Switch } from "@/components/ui/form";
import { useFacets, useResourceSearch, useSkills } from "@/lib/api/hooks";
import type { AvailableResource } from "@/lib/api/types";
import { useCan } from "@/lib/auth";
import { initials, isoToday } from "@/lib/format";

export function SearchView() {
  const canManage = useCan("RESOURCE_MANAGER");
  const skills = useSkills();
  const facets = useFacets();
  const [startDate, setStartDate] = useState(isoToday(7));
  const [endDate, setEndDate] = useState(isoToday(66));
  const [percent, setPercent] = useState(100);
  const [skillIds, setSkillIds] = useState<number[]>([]);
  const [minProficiency, setMinProficiency] = useState(3);
  const [location, setLocation] = useState("");
  const [department, setDepartment] = useState("");
  const [includeUnavailable, setIncludeUnavailable] = useState(false);
  const [proposeFor, setProposeFor] = useState<AvailableResource | null>(null);

  const validRange = Boolean(startDate && endDate && endDate >= startDate);
  const query = useMemo(
    () => ({
      startDate,
      endDate,
      allocationPercent: percent,
      skillIds: skillIds.length ? skillIds : undefined,
      minProficiency: skillIds.length ? minProficiency : undefined,
      location: location || undefined,
      department: department || undefined,
      includeUnavailable,
    }),
    [startDate, endDate, percent, skillIds, minProficiency, location, department, includeUnavailable],
  );
  const results = useResourceSearch(query, validRange);
  const skillName = (id: number) => skills.data?.find((s) => s.id === id)?.name ?? `#${id}`;

  return (
    <>
      <PageHeader eyebrow="Resource search" title="Find available talent" subtitle="Who is free for this window, with the skills you need? Ranked by availability coverage, then stored skill level." />

      <div className="grid gap-6 lg:grid-cols-[320px_1fr]">
        <Card className="h-fit space-y-5 p-5 lg:sticky lg:top-20">
          <div className="grid grid-cols-2 gap-3">
            <Field label="From" htmlFor="from">
              <Input id="from" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </Field>
            <Field label="To" htmlFor="to" error={validRange ? undefined : "Must be after start"}>
              <Input id="to" type="date" value={endDate} min={startDate} onChange={(e) => setEndDate(e.target.value)} />
            </Field>
          </div>
          <Field label={`Allocation needed · ${percent}%`} htmlFor="pct">
            <input id="pct" type="range" min={10} max={100} step={10} value={percent} onChange={(e) => setPercent(Number(e.target.value))} className="w-full accent-[var(--accent)]" />
          </Field>
          <Field label="Skills (all required)" htmlFor="skill-add">
            <Select
              id="skill-add"
              value=""
              onChange={(e) => {
                const id = Number(e.target.value);
                if (id && !skillIds.includes(id)) setSkillIds((s) => [...s, id]);
              }}
            >
              <option value="">Add a skill…</option>
              {skills.data?.filter((s) => !skillIds.includes(s.id)).map((s) => (
                <option key={s.id} value={s.id}>{s.name} · {s.category}</option>
              ))}
            </Select>
          </Field>
          {skillIds.length > 0 && (
            <>
              <div className="flex flex-wrap gap-1.5">
                {skillIds.map((id) => (
                  <button key={id} onClick={() => setSkillIds((s) => s.filter((x) => x !== id))} className="inline-flex items-center gap-1 rounded-full bg-fg px-3 py-1 text-[12px] font-medium text-bg">
                    {skillName(id)} <X className="size-3" aria-label="Remove" />
                  </button>
                ))}
              </div>
              <Field label="Minimum proficiency" htmlFor="min">
                <LevelPicker value={minProficiency} onChange={setMinProficiency} />
              </Field>
            </>
          )}
          <Field label="Location" htmlFor="loc">
            <Select id="loc" value={location} onChange={(e) => setLocation(e.target.value)}>
              <option value="">Anywhere</option>
              {facets.data?.locations.map((l) => <option key={l}>{l}</option>)}
            </Select>
          </Field>
          <Field label="Department" htmlFor="dept">
            <Select id="dept" value={department} onChange={(e) => setDepartment(e.target.value)}>
              <option value="">All departments</option>
              {facets.data?.departments.map((d) => <option key={d}>{d}</option>)}
            </Select>
          </Field>
          <Switch checked={includeUnavailable} onChange={setIncludeUnavailable} label="Include fully booked" description="Show people with no free capacity too" />
        </Card>

        <section aria-live="polite">
          {!validRange ? (
            <Card><EmptyState title="Choose a valid date range" /></Card>
          ) : results.isPending ? (
            <SkeletonRows rows={5} />
          ) : results.isError ? (
            <Card><ErrorState error={results.error} onRetry={() => results.refetch()} /></Card>
          ) : results.data.length === 0 ? (
            <Card>
              <EmptyState icon={SearchX} title="Nobody matches">Lower the minimum level, widen the window or include fully booked people.</EmptyState>
            </Card>
          ) : (
            <>
              <p className="mb-3 text-[13px] text-fg-3">{results.data.length} people · ranked by coverage of {percent}% over the window</p>
              <ol className="space-y-3">
                {results.data.map((r, i) => (
                  <li key={r.employeeId} className="card animate-fade-up p-5" style={{ animationDelay: `${i * 25}ms` }}>
                    <div className="flex flex-col gap-4 md:flex-row md:items-center">
                      <div className="flex min-w-0 flex-1 items-center gap-4">
                        <span className="w-6 text-[13px] font-semibold text-fg-3 tabular">{i + 1}</span>
                        <div className="flex size-11 shrink-0 items-center justify-center rounded-full bg-surface-3 text-[14px] font-semibold text-fg-2">{initials(r.name)}</div>
                        <div className="min-w-0">
                          <Link href={`/people/${r.employeeId}`} className="text-[17px] font-semibold hover:underline">{r.name}</Link>
                          <div className="flex flex-wrap gap-x-3 text-[13px] text-fg-2">
                            <span>{r.jobTitle} · {r.department}</span>
                            <span className="inline-flex items-center gap-1"><MapPin className="size-3.5" />{r.location ?? "Unknown"}{r.remoteEligible ? " · remote ok" : ""}</span>
                          </div>
                        </div>
                      </div>
                      <div className="flex flex-1 flex-col gap-2 md:max-w-[320px]">
                        <div className="flex items-center justify-between">
                          <AvailabilityBadge status={r.availability.status} />
                          <span className="text-[12px] text-fg-3 tabular">{r.availability.coveragePercent}% coverage · cap {r.capacityPercent}%</span>
                        </div>
                        <AvailabilityBar availability={r.availability} compact />
                      </div>
                      {canManage && r.availability.status !== "NONE" && (
                        <Button size="sm" variant="secondary" onClick={() => setProposeFor(r)}>Propose</Button>
                      )}
                    </div>
                    {r.matchedSkills.length > 0 && (
                      <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 border-t border-line pt-3">
                        {r.matchedSkills.map((s) => (
                          <span key={s.skillId} className="inline-flex items-center gap-2 text-[12px] text-fg-2">
                            {s.skillName} <LevelBar level={s.proficiencyLevel} size="sm" />
                          </span>
                        ))}
                      </div>
                    )}
                  </li>
                ))}
              </ol>
            </>
          )}
        </section>
      </div>

      {proposeFor && <ProposeDialog open onClose={() => setProposeFor(null)} employee={{ id: proposeFor.employeeId, name: proposeFor.name }} />}
    </>
  );
}
