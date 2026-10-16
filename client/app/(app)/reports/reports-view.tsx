"use client";

import Link from "next/link";
import { useState, type ReactNode } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { PriorityBadge } from "@/components/domain/badges";
import { Meter } from "@/components/domain/score";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader } from "@/components/ui/card";
import { ErrorState, Skeleton } from "@/components/ui/feedback";
import { Field, Input, Select } from "@/components/ui/form";
import { DataTable } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import {
  useAvailableResourcesReport,
  useOpenDemandReport,
  useSkillGapReport,
  useSkillProficiencyReport,
  useTraining,
  useUnstaffableReport,
  useUpdateTraining,
  useUtilization,
} from "@/lib/api/hooks";
import type { TrainingStatus } from "@/lib/api/types";
import { useCan } from "@/lib/auth";
import { formatDate, humanize, isoToday, levelName } from "@/lib/format";

type Tab = "open" | "skills" | "available" | "utilization" | "unstaffable" | "gaps" | "learning";

export function ReportsView() {
  const [tab, setTab] = useState<Tab>("utilization");
  return (
    <>
      <PageHeader eyebrow="Reports" title="Insights" subtitle="The SQL reports behind staffing decisions, run live against PostgreSQL." />
      <div className="mb-6">
        <Tabs
          value={tab}
          onChange={setTab}
          tabs={[
            { value: "utilization", label: "Utilisation" },
            { value: "available", label: "Available resources" },
            { value: "open", label: "Open demands" },
            { value: "unstaffable", label: "No eligible candidate" },
            { value: "gaps", label: "Skill gaps" },
            { value: "skills", label: "Skills inventory" },
            { value: "learning", label: "Learning actions" },
          ]}
        />
      </div>
      {tab === "utilization" && <Utilization />}
      {tab === "available" && <Available />}
      {tab === "open" && <OpenDemands />}
      {tab === "unstaffable" && <Unstaffable />}
      {tab === "gaps" && <SkillGaps />}
      {tab === "skills" && <SkillsInventory />}
      {tab === "learning" && <Learning />}
    </>
  );
}

function Loadable<T>({ query, children }: { query: { isPending: boolean; isError: boolean; error: unknown; data?: T; refetch: () => unknown }; children: (data: T) => ReactNode }) {
  if (query.isPending) return <Skeleton className="h-72 rounded-[22px]" />;
  if (query.isError || query.data === undefined) return <Card><ErrorState error={query.error} onRetry={() => query.refetch()} /></Card>;
  return <>{children(query.data)}</>;
}

function RangeControls({ from, to, onChange, extra }: { from: string; to: string; onChange: (from: string, to: string) => void; extra?: ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-end gap-3">
      <Field label="From" htmlFor="r-from"><Input id="r-from" type="date" value={from} onChange={(e) => onChange(e.target.value, to)} /></Field>
      <Field label="To" htmlFor="r-to"><Input id="r-to" type="date" value={to} min={from} onChange={(e) => onChange(from, e.target.value)} /></Field>
      {extra}
    </div>
  );
}

function Utilization() {
  const [range, setRange] = useState({ from: isoToday(), to: isoToday(89) });
  const q = useUtilization(range);
  return (
    <>
      <RangeControls from={range.from} to={range.to} onChange={(from, to) => setRange({ from, to })} />
      <Loadable query={q}>
        {(data) => (
          <div className="grid gap-6 lg:grid-cols-[1fr_1.6fr]">
            <Card>
              <CardHeader title="By department" subtitle="Capacity-weighted, approved load" />
              <DataTable
                rowKey={(r) => r.department}
                rows={data.byDepartment}
                columns={[
                  { key: "d", header: "Department", cell: (r) => <span className="font-medium">{r.department}</span> },
                  { key: "n", header: "People", align: "right", cell: (r) => r.employeeCount },
                  { key: "u", header: "Utilisation", align: "right", cell: (r) => `${r.utilizationPercent ?? 0}%` },
                  { key: "t", header: "+ pending", align: "right", cell: (r) => `${r.tentativePercent ?? 0}%` },
                ]}
              />
            </Card>
            <Card>
              <CardHeader title="By employee" subtitle={`${formatDate(data.from)} – ${formatDate(data.to)}`} />
              <DataTable
                rowKey={(r) => r.employeeId}
                rows={data.byEmployee}
                columns={[
                  { key: "n", header: "Employee", cell: (r) => <Link href={`/people/${r.employeeId}`} className="font-medium hover:underline">{r.name}</Link> },
                  { key: "d", header: "Department", cell: (r) => <span className="text-fg-2">{r.department}</span> },
                  { key: "c", header: "Capacity", align: "right", cell: (r) => `${r.capacityPercent}%` },
                  { key: "u", header: "Utilisation", cell: (r) => <div className="flex min-w-[140px] items-center gap-2"><Meter value={r.utilizationPercent} label={`${r.name} utilisation`} /><span className="w-12 text-right tabular">{r.utilizationPercent}%</span></div> },
                  { key: "t", header: "+ pending", align: "right", cell: (r) => `${r.tentativePercent}%` },
                ]}
              />
            </Card>
          </div>
        )}
      </Loadable>
    </>
  );
}

function Available() {
  const [range, setRange] = useState({ from: isoToday(7), to: isoToday(36) });
  const [pct, setPct] = useState(100);
  const q = useAvailableResourcesReport({ ...range, allocationPercent: pct });
  return (
    <>
      <RangeControls
        from={range.from}
        to={range.to}
        onChange={(from, to) => setRange({ from, to })}
        extra={
          <Field label="Needs %" htmlFor="r-pct">
            <Select id="r-pct" value={pct} onChange={(e) => setPct(Number(e.target.value))}>
              {[25, 50, 75, 100].map((p) => <option key={p} value={p}>{p}%</option>)}
            </Select>
          </Field>
        }
      />
      <Loadable query={q}>
        {(rows) => (
          <Card>
            <CardHeader title="Free for at least one day" subtitle="Per-day load from pending and approved allocations" />
            <DataTable
              rowKey={(r) => r.employeeId}
              rows={rows}
              empty="Nobody has free capacity in this window."
              columns={[
                { key: "n", header: "Employee", cell: (r) => <Link href={`/people/${r.employeeId}`} className="font-medium hover:underline">{r.name}</Link> },
                { key: "d", header: "Department", cell: (r) => <span className="text-fg-2">{r.department}</span> },
                { key: "l", header: "Location", cell: (r) => r.location ?? <span className="text-fg-3">Unknown</span> },
                { key: "a", header: "Days free", align: "right", cell: (r) => `${r.availableDays}/${r.totalDays}` },
                { key: "p", header: "Peak load", align: "right", cell: (r) => `${r.peakLoadPercent}%` },
                { key: "m", header: "Min free", align: "right", cell: (r) => `${r.minFreePercent}%` },
              ]}
            />
          </Card>
        )}
      </Loadable>
    </>
  );
}

function OpenDemands() {
  const q = useOpenDemandReport();
  return (
    <Loadable query={q}>
      {(rows) => (
        <Card>
          <CardHeader title="Open demands by role and priority" />
          <DataTable
            rowKey={(r) => `${r.role}-${r.priority}`}
            rows={rows}
            columns={[
              { key: "p", header: "Priority", cell: (r) => <PriorityBadge priority={r.priority} /> },
              { key: "r", header: "Role", cell: (r) => <span className="font-medium">{r.role}</span> },
              { key: "c", header: "Demands", align: "right", cell: (r) => r.demandCount },
              { key: "h", header: "Headcount", align: "right", cell: (r) => r.totalHeadcount },
              { key: "a", header: "Approved", align: "right", cell: (r) => r.approvedCount },
              { key: "o", header: "Open positions", align: "right", cell: (r) => <span className="font-semibold">{r.openPositions}</span> },
              { key: "s", header: "Earliest start", cell: (r) => formatDate(r.earliestStart) },
            ]}
          />
        </Card>
      )}
    </Loadable>
  );
}

function Unstaffable() {
  const q = useUnstaffableReport();
  return (
    <Loadable query={q}>
      {(rows) => (
        <Card>
          <CardHeader title="Demands with no eligible candidate" subtitle="Nobody is assignable, holds every mandatory skill and has free capacity" />
          <DataTable
            rowKey={(r) => r.demandId}
            rows={rows}
            empty="Every open demand has at least one eligible candidate."
            columns={[
              { key: "p", header: "Priority", cell: (r) => <PriorityBadge priority={r.priority} /> },
              { key: "d", header: "Demand", cell: (r) => <Link href={`/demands/${r.demandId}`} className="font-medium hover:underline">{r.projectName} · {r.role}</Link> },
              { key: "s", header: "Starts", cell: (r) => formatDate(r.startDate) },
              { key: "q", header: "Skill-qualified", align: "right", cell: (r) => r.skillQualifiedCount },
              { key: "w", header: "Why", cell: (r) => <span className="text-fg-2">{r.reason}</span> },
            ]}
          />
        </Card>
      )}
    </Loadable>
  );
}

function SkillGaps() {
  const q = useSkillGapReport();
  return (
    <Loadable query={q}>
      {(data) => (
        <div className="space-y-6">
          <Card>
            <CardHeader title="By category" />
            <DataTable
              rowKey={(r) => r.category}
              rows={data.byCategory}
              columns={[
                { key: "c", header: "Category", cell: (r) => <span className="font-medium">{r.category}</span> },
                { key: "r", header: "Requirements", align: "right", cell: (r) => r.requirementCount },
                { key: "u", header: "Uncovered", align: "right", cell: (r) => r.uncoveredRequirements },
                { key: "p", header: "People short", align: "right", cell: (r) => <span className={r.peopleShort ? "font-semibold text-bad" : ""}>{r.peopleShort}</span> },
                { key: "n", header: "Near-miss", align: "right", cell: (r) => r.upskillCandidates },
                { key: "t", header: "Learning actions", align: "right", cell: (r) => r.openTrainingRecommendations },
                { key: "s", header: "Skills short", cell: (r) => <span className="text-fg-2">{r.skillsShort ?? "—"}</span> },
              ]}
            />
          </Card>
          <Card>
            <CardHeader title="By skill" subtitle="Demanded headcount vs. people at the required level" />
            <DataTable
              rowKey={(r) => r.skill}
              rows={data.bySkill}
              columns={[
                { key: "s", header: "Skill", cell: (r) => <span className="font-medium">{r.skill}</span> },
                { key: "c", header: "Category", cell: (r) => <span className="text-fg-2">{r.category}</span> },
                { key: "d", header: "Demanded", align: "right", cell: (r) => r.demandedHeadcount },
                { key: "q", header: "Qualified", align: "right", cell: (r) => r.qualifiedSupply },
                { key: "n", header: "Near-miss", align: "right", cell: (r) => r.nearMissSupply },
                { key: "p", header: "Short", align: "right", cell: (r) => <span className={r.peopleShort ? "font-semibold text-bad" : "text-good"}>{r.peopleShort}</span> },
              ]}
            />
          </Card>
        </div>
      )}
    </Loadable>
  );
}

function SkillsInventory() {
  const q = useSkillProficiencyReport();
  return (
    <Loadable query={q}>
      {(rows) => (
        <Card>
          <CardHeader title="Resources by skill and proficiency" subtitle="Assignable employees only" />
          <DataTable
            rowKey={(r) => `${r.skill}-${r.proficiencyLevel}`}
            rows={rows}
            columns={[
              { key: "c", header: "Category", cell: (r) => <span className="text-fg-2">{r.category}</span> },
              { key: "s", header: "Skill", cell: (r) => <span className="font-medium">{r.skill}</span> },
              { key: "l", header: "Level", cell: (r) => `${r.proficiencyLevel} · ${levelName(r.proficiencyLevel)}` },
              { key: "n", header: "People", align: "right", cell: (r) => r.employeeCount },
              { key: "v", header: "Validated", align: "right", cell: (r) => r.validatedCount },
              { key: "e", header: "Who", cell: (r) => <span className="text-fg-2">{r.employees}</span> },
            ]}
          />
        </Card>
      )}
    </Loadable>
  );
}

function Learning() {
  const [status, setStatus] = useState<TrainingStatus | "">("");
  const q = useTraining({ status: status ? [status] : undefined });
  const update = useUpdateTraining();
  const canManage = useCan("RESOURCE_MANAGER");
  return (
    <>
      <div className="mb-4 max-w-xs">
        <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value as TrainingStatus | "")}>
          <option value="">All statuses</option>
          <option value="RECOMMENDED">Recommended</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="COMPLETED">Completed</option>
          <option value="DISMISSED">Dismissed</option>
        </Select>
      </div>
      <Loadable query={q}>
        {(rows) => (
          <Card>
            <CardHeader title="Learning actions" subtitle="From the AI assistant and from managers" />
            <DataTable
              rowKey={(r) => r.id}
              rows={rows}
              empty="No learning actions yet. Run the assistant with “Record learning actions”."
              columns={[
                { key: "e", header: "Employee", cell: (r) => <Link href={`/people/${r.employeeId}`} className="font-medium hover:underline">{r.employeeName}</Link> },
                { key: "s", header: "Skill", cell: (r) => `${r.skillName} ${r.currentLevel ?? "–"} → ${r.targetLevel}` },
                { key: "r", header: "Resource", cell: (r) => <span className="text-fg-2">{r.learningResource ? `${r.learningResource.title} · ${r.learningResource.durationHours}h` : "—"}</span> },
                { key: "o", header: "Source", cell: (r) => <Badge tone={r.source === "AI_AGENT" ? "ai" : "neutral"}>{r.source === "AI_AGENT" ? "AI" : "Manual"}</Badge> },
                {
                  key: "st",
                  header: "Status",
                  cell: (r) =>
                    canManage && (r.status === "RECOMMENDED" || r.status === "IN_PROGRESS") ? (
                      <Select aria-label={`Status for ${r.employeeName}`} className="h-9 text-[13px]" value={r.status} onChange={(e) => update.mutate({ id: r.id, status: e.target.value as TrainingStatus })}>
                        <option value="RECOMMENDED">Recommended</option>
                        <option value="IN_PROGRESS">In progress</option>
                        <option value="COMPLETED">Completed</option>
                        <option value="DISMISSED">Dismissed</option>
                      </Select>
                    ) : (
                      <Badge>{humanize(r.status)}</Badge>
                    ),
                },
              ]}
            />
          </Card>
        )}
      </Loadable>
    </>
  );
}
