"use client";

import { Briefcase, ChevronLeft, ChevronRight, MapPin, Plus, Search, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { useDeferredValue, useState } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { DemandStatusBadge, PriorityBadge } from "@/components/domain/badges";
import { Button, LinkButton } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { Chip, Input, Select } from "@/components/ui/form";
import { useDemands, useSkills } from "@/lib/api/hooks";
import type { DemandPriority, DemandStatus } from "@/lib/api/types";
import { demandStatusMeta, formatRange } from "@/lib/format";
import { useCan } from "@/lib/auth";

const STATUSES: DemandStatus[] = ["OPEN", "PARTIALLY_FILLED", "FILLED", "CANCELLED"];
const SORTS = [
  { value: "startDate,asc", label: "Start date" },
  { value: "createdAt,desc", label: "Newest" },
  { value: "endDate,asc", label: "End date" },
  { value: "projectName,asc", label: "Project name" },
];

export function DemandsView() {
  const canManage = useCan("RESOURCE_MANAGER");
  const skills = useSkills();
  const [query, setQuery] = useState("");
  const [statuses, setStatuses] = useState<DemandStatus[]>(["OPEN", "PARTIALLY_FILLED"]);
  const [priority, setPriority] = useState<DemandPriority | "">("");
  const [skillId, setSkillId] = useState<number | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [sort, setSort] = useState(SORTS[0].value);
  const [page, setPage] = useState(0);
  const [showFilters, setShowFilters] = useState(false);
  const q = useDeferredValue(query);

  const demands = useDemands({
    q: q || undefined,
    status: statuses,
    priority: priority || undefined,
    skillId: skillId || undefined,
    from: from || undefined,
    to: to || undefined,
    sort,
    page,
    size: 12,
  });

  const toggleStatus = (s: DemandStatus) => {
    setPage(0);
    setStatuses((current) => (current.includes(s) ? current.filter((x) => x !== s) : [...current, s]));
  };

  return (
    <>
      <PageHeader
        eyebrow="Demand"
        title="Project demands"
        subtitle="Every request for people, with its staffing progress."
        actions={canManage && <LinkButton href="/demands/new" icon={<Plus className="size-4" />}>New demand</LinkButton>}
      />

      <div className="mb-5 space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          {STATUSES.map((s) => (
            <Chip key={s} active={statuses.includes(s)} onClick={() => toggleStatus(s)}>
              {demandStatusMeta[s].label}
            </Chip>
          ))}
        </div>
        <div className="grid gap-3 md:grid-cols-3 lg:grid-cols-[2fr_1fr_1fr_1fr_1fr_1fr]">
          <div className="flex gap-2 md:col-span-3 lg:col-span-1">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-fg-3" />
              <Input
                aria-label="Search role or project"
                placeholder="Search role or project"
                className="pl-10"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setPage(0);
                }}
              />
            </div>
            <Button
              variant="secondary"
              className="h-11 md:hidden"
              aria-expanded={showFilters}
              onClick={() => setShowFilters((v) => !v)}
              icon={<SlidersHorizontal className="size-4" />}
            >
              Filters
            </Button>
          </div>
          <div className={`${showFilters ? "grid" : "hidden"} gap-3 sm:grid-cols-2 md:contents`}>
            <Select aria-label="Priority" value={priority} onChange={(e) => { setPriority(e.target.value as DemandPriority | ""); setPage(0); }}>
              <option value="">Any priority</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </Select>
            <Select aria-label="Required skill" value={skillId} onChange={(e) => { setSkillId(e.target.value ? Number(e.target.value) : ""); setPage(0); }}>
              <option value="">Any skill</option>
              {skills.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
            <Input aria-label="Active from" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
            <Input aria-label="Active until" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
            <Select aria-label="Sort by" value={sort} onChange={(e) => setSort(e.target.value)}>
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>
                  Sort: {s.label}
                </option>
              ))}
            </Select>
          </div>
        </div>
      </div>

      {demands.isPending ? (
        <SkeletonRows rows={6} />
      ) : demands.isError ? (
        <Card>
          <ErrorState error={demands.error} onRetry={() => demands.refetch()} />
        </Card>
      ) : demands.data.content.length === 0 ? (
        <Card>
          <EmptyState icon={Briefcase} title="No demands match" action={canManage && <LinkButton href="/demands/new" size="sm">Create a demand</LinkButton>}>
            Try clearing a filter or widening the date window.
          </EmptyState>
        </Card>
      ) : (
        <>
          <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-busy={demands.isFetching}>
            {demands.data.content.map((d, i) => {
              const filled = Math.min(d.approvedCount, d.headcount);
              return (
                <li key={d.id} className="animate-fade-up" style={{ animationDelay: `${i * 30}ms` }}>
                  <Link href={`/demands/${d.id}`} className="card group flex h-full flex-col p-5 transition-transform duration-300 hover:-translate-y-0.5 hover:shadow-[var(--shadow-lg)]">
                    <div className="flex items-start justify-between gap-3">
                      <PriorityBadge priority={d.priority} />
                      <DemandStatusBadge status={d.status} />
                    </div>
                    <h2 className="mt-4 text-[19px] leading-snug font-semibold tracking-tight">{d.role}</h2>
                    <p className="text-[14px] text-fg-2">{d.projectName}</p>
                    <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-fg-2">
                      <span>{formatRange(d.startDate, d.endDate)}</span>
                      <span>{d.allocationPercent}%</span>
                      <span className="inline-flex items-center gap-1">
                        <MapPin className="size-3.5" />
                        {d.location ?? "Any location"}
                        {d.remoteAllowed && d.location ? " · remote ok" : ""}
                      </span>
                    </div>
                    <div className="mt-4 flex flex-wrap gap-1.5">
                      {d.requiredSkills.slice(0, 5).map((s) => (
                        <span key={s.skillId} className={`rounded-full px-2.5 py-1 text-[12px] ${s.mandatory ? "bg-surface-3 text-fg" : "bg-surface-2 text-fg-3"}`}>
                          {s.skillName} {s.minimumProficiency}+
                        </span>
                      ))}
                    </div>
                    <div className="mt-auto pt-5">
                      <div className="mb-1.5 flex justify-between text-[12px] text-fg-3">
                        <span>
                          {filled} of {d.headcount} staffed{d.pendingCount ? ` · ${d.pendingCount} pending` : ""}
                        </span>
                        <span className="text-accent opacity-0 transition-opacity group-hover:opacity-100">View candidates →</span>
                      </div>
                      <div className="flex gap-1">
                        {Array.from({ length: d.headcount }, (_, k) => (
                          <span
                            key={k}
                            className={`h-1.5 flex-1 rounded-full ${k < filled ? "bg-[var(--viz-good)]" : k < filled + d.pendingCount ? "bg-[var(--viz-warn)]" : "bg-[var(--viz-track)]"}`}
                          />
                        ))}
                      </div>
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
          {demands.data.totalPages > 1 && (
            <div className="mt-8 flex items-center justify-center gap-3">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)} icon={<ChevronLeft className="size-4" />}>
                Previous
              </Button>
              <span className="text-[13px] text-fg-2 tabular">
                Page {page + 1} of {demands.data.totalPages}
              </span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= demands.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next <ChevronRight className="size-4" />
              </Button>
            </div>
          )}
        </>
      )}
    </>
  );
}
