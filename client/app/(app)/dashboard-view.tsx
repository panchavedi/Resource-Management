"use client";

import { ArrowUpRight, Briefcase, CalendarClock, Gauge, GraduationCap, Hourglass, Plus, Sparkles, UserX, Users } from "lucide-react";
import Link from "next/link";
import { PageHeader } from "@/components/layout/app-shell";
import { BarList } from "@/components/domain/bar-list";
import { PriorityBadge } from "@/components/domain/badges";
import { Meter } from "@/components/domain/score";
import { LinkButton } from "@/components/ui/button";
import { Card, CardHeader, Stat } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/feedback";
import { useDashboard, useSkillGapReport, useUnstaffableReport, useUtilization } from "@/lib/api/hooks";
import { useCan, useSession } from "@/lib/auth";
import { formatDate, formatShortDate, isoToday } from "@/lib/format";

function plural(n: number, one: string, many = `${one}s`) {
  return `${n} ${n === 1 ? one : many}`;
}

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}

export function DashboardView() {
  const session = useSession();
  const canManage = useCan("RESOURCE_MANAGER");
  const dashboard = useDashboard();
  const utilization = useUtilization({ from: isoToday(), to: isoToday(89) });
  const gaps = useSkillGapReport();
  const unstaffable = useUnstaffableReport();
  const firstName = session?.user.displayName.split(/[\s(]/)[0] ?? "";
  const d = dashboard.data;

  return (
    <>
      <PageHeader
        eyebrow={formatDate(isoToday())}
        title={`${greeting()}, ${firstName}.`}
        subtitle="Utilisation, open demand and skill gaps across the organisation — all computed from stored records."
        actions={
          canManage && (
            <>
              <LinkButton href="/assistant" variant="ai" icon={<Sparkles className="size-4" />}>
                Ask the assistant
              </LinkButton>
              <LinkButton href="/demands/new" icon={<Plus className="size-4" />}>
                New demand
              </LinkButton>
            </>
          )
        }
      />

      {dashboard.isError ? (
        <Card>
          <ErrorState error={dashboard.error} onRetry={() => dashboard.refetch()} />
        </Card>
      ) : (
        <section className="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6" aria-label="Key figures">
          {!d ? (
            Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-[132px] rounded-[22px]" />)
          ) : (
            <>
              <Stat label="Open demands" value={d.openDemands} hint={`${d.criticalOpenDemands} critical`} icon={<Briefcase className="size-4" />} />
              <Stat label="Open positions" value={d.openPositions} hint="Headcount not yet approved" icon={<Users className="size-4" />} />
              <Link href="/approvals" className="rounded-[22px]">
                <Stat label="Pending approvals" value={d.pendingApprovals} tone={d.pendingApprovals ? "warn" : undefined} hint="Awaiting a decision →" icon={<Hourglass className="size-4" />} />
              </Link>
              <Stat label="Utilisation · 30 days" value={`${d.averageUtilizationNext30Days}%`} hint="Approved load / capacity" icon={<Gauge className="size-4" />} />
              <Stat label="Unstaffable demands" value={d.unstaffableDemands} tone={d.unstaffableDemands ? "bad" : "good"} hint="No eligible candidate" icon={<UserX className="size-4" />} />
              <Stat label="On the bench" value={d.benchEmployees} hint={`${d.openTrainingRecommendations} open learning actions`} icon={<GraduationCap className="size-4" />} />
            </>
          )}
        </section>
      )}

      <section className="mt-6 grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader title="Utilisation by department" subtitle="Approved allocations, next 90 days" />
          <div className="px-6 pb-6">
            {utilization.isPending ? (
              <Skeleton className="h-48" />
            ) : utilization.isError ? (
              <ErrorState error={utilization.error} onRetry={() => utilization.refetch()} />
            ) : (
              <BarList
                max={100}
                rows={utilization.data.byDepartment.map((row) => ({
                  key: row.department,
                  label: row.department,
                  value: row.utilizationPercent ?? 0,
                  display: `${row.utilizationPercent ?? 0}%`,
                  detail: `${plural(row.employeeCount, "person", "people")} · ${row.tentativePercent ?? 0}% more pending approval`,
                }))}
              />
            )}
          </div>
        </Card>

        <Card>
          <CardHeader title="Skill gaps by category" subtitle="People short across open demands" action={<Link href="/reports" className="text-[13px] text-accent hover:underline">Details</Link>} />
          <div className="px-6 pb-6">
            {gaps.isPending ? (
              <Skeleton className="h-48" />
            ) : gaps.isError ? (
              <ErrorState error={gaps.error} onRetry={() => gaps.refetch()} />
            ) : (
              <BarList
                emptyText="No skill gaps across open demands."
                rows={gaps.data.byCategory
                  .filter((row) => row.peopleShort > 0)
                  .map((row) => ({
                    key: row.category,
                    label: row.category,
                    value: row.peopleShort,
                    display: `${row.peopleShort} short`,
                    detail: `${row.skillsShort ?? "—"} · ${row.upskillCandidates} near-miss to upskill · ${plural(row.openTrainingRecommendations, "learning action")}`,
                  }))}
              />
            )}
          </div>
        </Card>
      </section>

      <section className="mt-6 grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader title="Starting soon" subtitle="Unfilled demands, next 30 days" />
          <div className="px-3 pb-4">
            {!d ? (
              <Skeleton className="mx-3 h-40" />
            ) : d.startingSoon.length === 0 ? (
              <EmptyState icon={CalendarClock} title="Nothing urgent">
                No unfilled demand starts in the next 30 days.
              </EmptyState>
            ) : (
              <ul>
                {d.startingSoon.map((s) => (
                  <li key={s.demandId}>
                    <Link href={`/demands/${s.demandId}`} className="flex items-center justify-between gap-3 rounded-xl px-3 py-2.5 hover:bg-surface-2">
                      <span className="min-w-0">
                        <span className="block truncate text-[14px] font-medium">{s.projectName}</span>
                        <span className="block truncate text-[12px] text-fg-3">
                          {s.role} · starts {formatShortDate(s.startDate)} · {s.openPositions} open
                        </span>
                      </span>
                      <PriorityBadge priority={s.priority} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>

        <Card className="lg:col-span-1">
          <CardHeader title="Needs attention" subtitle="Open demands with no eligible candidate" />
          <div className="px-3 pb-4">
            {unstaffable.isPending ? (
              <Skeleton className="mx-3 h-40" />
            ) : unstaffable.isError ? (
              <ErrorState error={unstaffable.error} />
            ) : unstaffable.data.length === 0 ? (
              <EmptyState title="Every open demand has a candidate" />
            ) : (
              <ul>
                {unstaffable.data.map((row) => (
                  <li key={row.demandId} className="rounded-xl px-3 py-2.5">
                    <div className="flex items-center justify-between gap-3">
                      <Link href={`/demands/${row.demandId}`} className="truncate text-[14px] font-medium hover:underline">
                        {row.projectName} · {row.role}
                      </Link>
                      <PriorityBadge priority={row.priority} />
                    </div>
                    <p className="mt-0.5 text-[12px] text-fg-3">{row.reason}</p>
                    <Link href={`/assistant?demandId=${row.demandId}`} className="mt-1 inline-flex items-center gap-1 text-[12px] font-medium text-ai hover:underline">
                      <Sparkles className="size-3" /> Learning paths for near-miss people
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>

        <Card className="lg:col-span-1">
          <CardHeader title="Most utilised" subtitle="Approved load, next 90 days" />
          <div className="px-6 pb-6">
            {utilization.isPending ? (
              <Skeleton className="h-40" />
            ) : utilization.isError ? null : (
              <ul className="space-y-3">
                {[...utilization.data.byEmployee]
                  .sort((a, b) => b.utilizationPercent - a.utilizationPercent)
                  .slice(0, 6)
                  .map((row) => (
                    <li key={row.employeeId}>
                      <Link href={`/people/${row.employeeId}`} className="block">
                        <div className="mb-1 flex justify-between text-[13px]">
                          <span className="font-medium">{row.name}</span>
                          <span className="tabular text-fg-2">{row.utilizationPercent}%</span>
                        </div>
                        <Meter value={row.utilizationPercent} label={`${row.name} utilisation`} />
                      </Link>
                    </li>
                  ))}
              </ul>
            )}
            <Link href="/reports" className="mt-4 inline-flex items-center gap-1 text-[13px] text-accent hover:underline">
              Full utilisation report <ArrowUpRight className="size-3.5" />
            </Link>
          </div>
        </Card>
      </section>
    </>
  );
}
