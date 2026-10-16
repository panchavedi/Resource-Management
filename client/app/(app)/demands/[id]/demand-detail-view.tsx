"use client";

import { ArrowLeft, Ban, CalendarRange, Columns3, MapPin, Percent, Sparkles, Users, UserX } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { AllocationStatusBadge, DemandStatusBadge, PriorityBadge } from "@/components/domain/badges";
import { CandidateCard } from "@/components/domain/candidate-card";
import { ProposeDialog } from "@/components/domain/propose-dialog";
import { Button, LinkButton } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState, ErrorState, Notice, Skeleton, SkeletonRows } from "@/components/ui/feedback";
import { Field, Input } from "@/components/ui/form";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import { useAllocations, useCancelDemand, useDemand, useRecommendations } from "@/lib/api/hooks";
import type { Candidate } from "@/lib/api/types";
import { useCan } from "@/lib/auth";
import { formatDateTime, formatRange, levelName } from "@/lib/format";

type Tab = "ranked" | "excluded" | "allocations" | "criteria";

export function DemandDetailView() {
  const { id: rawId } = useParams<{ id: string }>();
  const id = Number(rawId);
  const router = useRouter();
  const canManage = useCan("RESOURCE_MANAGER");
  const demand = useDemand(id);
  const ranking = useRecommendations(id);
  const allocations = useAllocations({ demandId: id, size: 50 });
  const [tab, setTab] = useState<Tab>("ranked");
  const [selected, setSelected] = useState<number[]>([]);
  const [proposeFor, setProposeFor] = useState<Candidate | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);

  if (demand.isError) {
    return (
      <Card>
        <ErrorState error={demand.error} onRetry={() => demand.refetch()} />
      </Card>
    );
  }
  if (!demand.data) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-28" />
        <SkeletonRows rows={3} />
      </div>
    );
  }

  const d = demand.data;
  const open = d.status === "OPEN" || d.status === "PARTIALLY_FILLED";
  const toggle = (employeeId: number) =>
    setSelected((s) => (s.includes(employeeId) ? s.filter((x) => x !== employeeId) : s.length < 3 ? [...s, employeeId] : s));

  return (
    <>
      <Link href="/demands" className="mb-4 inline-flex items-center gap-1 text-[14px] text-accent hover:underline">
        <ArrowLeft className="size-4" /> Demands
      </Link>
      <PageHeader
        eyebrow={d.projectName}
        title={d.role}
        subtitle={d.description ?? undefined}
        actions={
          <>
            <LinkButton href={`/assistant?demandId=${d.id}`} variant="ai" icon={<Sparkles className="size-4" />}>
              Explain with AI
            </LinkButton>
            {canManage && open && (
              <Button variant="danger" icon={<Ban className="size-4" />} onClick={() => setCancelOpen(true)}>
                Cancel demand
              </Button>
            )}
          </>
        }
      />

      <Card className="mb-8 grid grid-cols-2 gap-6 p-6 lg:grid-cols-5">
        <Fact icon={<CalendarRange className="size-4" />} label="Period" value={formatRange(d.startDate, d.endDate)} hint={`${d.durationDays} days`} />
        <Fact icon={<Percent className="size-4" />} label="Allocation" value={`${d.allocationPercent}%`} hint="per person" />
        <Fact icon={<Users className="size-4" />} label="Staffing" value={`${d.approvedCount} of ${d.headcount}`} hint={d.pendingCount ? `${d.pendingCount} pending approval` : "approved"} />
        <Fact icon={<MapPin className="size-4" />} label="Location" value={d.location ?? "Any"} hint={d.remoteAllowed ? "Remote allowed" : "Onsite"} />
        <div className="flex flex-col gap-2">
          <span className="text-[12px] text-fg-3">Status</span>
          <div className="flex flex-wrap gap-2">
            <PriorityBadge priority={d.priority} />
            <DemandStatusBadge status={d.status} />
          </div>
        </div>
        <div className="col-span-2 lg:col-span-5">
          <div className="mb-2 text-[12px] text-fg-3">Required skills</div>
          <div className="flex flex-wrap gap-2">
            {d.requiredSkills.length === 0 && <span className="text-[13px] text-fg-3">None — any assignable employee qualifies.</span>}
            {d.requiredSkills.map((s) => (
              <span key={s.skillId} className={`rounded-full px-3 py-1.5 text-[13px] ${s.mandatory ? "bg-fg text-bg" : "bg-surface-3 text-fg-2"}`}>
                {s.skillName} · {levelName(s.minimumProficiency)}+{!s.mandatory && " (optional)"}
              </span>
            ))}
          </div>
        </div>
      </Card>

      <div className="mb-5 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <Tabs
          value={tab}
          onChange={setTab}
          tabs={[
            { value: "ranked", label: "Ranked candidates", count: ranking.data?.rankedCandidates.length },
            { value: "excluded", label: "Excluded", count: ranking.data?.excludedCandidates.length },
            { value: "allocations", label: "Allocations", count: allocations.data?.totalElements },
            { value: "criteria", label: "Criteria" },
          ]}
        />
        {tab === "ranked" && (
          <Button
            variant="secondary"
            size="sm"
            className="self-start md:self-auto"
            disabled={selected.length < 2}
            icon={<Columns3 className="size-4" />}
            onClick={() => router.push(`/demands/${d.id}/compare?ids=${selected.join(",")}`)}
          >
            Compare {selected.length ? `(${selected.length})` : ""}
          </Button>
        )}
      </div>

      {ranking.isError && tab !== "allocations" ? (
        <Card>
          <ErrorState error={ranking.error} onRetry={() => ranking.refetch()} />
        </Card>
      ) : tab === "ranked" ? (
        !ranking.data ? (
          <SkeletonRows rows={3} />
        ) : ranking.data.rankedCandidates.length === 0 ? (
          <Card>
            <EmptyState icon={UserX} title="No eligible candidates" action={<LinkButton href={`/assistant?demandId=${d.id}`} variant="ai" size="sm">Get learning paths</LinkButton>}>
              {ranking.data.excludedCandidates.length} people were evaluated and excluded — see the Excluded tab for the exact reasons.
            </EmptyState>
          </Card>
        ) : (
          <div className="space-y-4">
            <p className="text-[13px] text-fg-3">Select two or three candidates to compare them side by side.</p>
            {ranking.data.rankedCandidates.map((c, i) => (
              <CandidateCard
                key={c.employeeId}
                index={i}
                candidate={c}
                selectable
                selected={selected.includes(c.employeeId)}
                onSelect={() => toggle(c.employeeId)}
                action={canManage && open && c.eligible && <Button size="sm" onClick={() => setProposeFor(c)}>Propose</Button>}
              />
            ))}
          </div>
        )
      ) : tab === "excluded" ? (
        !ranking.data ? (
          <SkeletonRows rows={3} />
        ) : ranking.data.excludedCandidates.length === 0 ? (
          <Card>
            <EmptyState title="Nobody was excluded">Every evaluated employee passed the hard rules.</EmptyState>
          </Card>
        ) : (
          <div className="space-y-4">
            <Notice tone="info" title={`${ranking.data.excludedCandidates.length} excluded · ${ranking.data.notConsideredCount} not evaluated`}>
              Employees without any of the mandatory skills are not evaluated. Excluded people failed a hard rule shown on each card.
            </Notice>
            {ranking.data.excludedCandidates.map((c, i) => (
              <CandidateCard key={c.employeeId} index={i} candidate={c} />
            ))}
          </div>
        )
      ) : tab === "allocations" ? (
        <Card className="p-2">
          {allocations.isPending ? (
            <SkeletonRows rows={2} className="p-4" />
          ) : !allocations.data || allocations.data.content.length === 0 ? (
            <EmptyState title="No allocations yet">Propose a ranked candidate to start the approval workflow.</EmptyState>
          ) : (
            <ul>
              {allocations.data.content.map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl px-4 py-3 hover:bg-surface-2">
                  <div>
                    <Link href={`/people/${a.employeeId}`} className="font-medium hover:underline">
                      {a.employeeName}
                    </Link>
                    <div className="text-[13px] text-fg-2">
                      {formatRange(a.startDate, a.endDate)} · {a.allocationPercent}% · requested by {a.requestedBy} {formatDateTime(a.requestedAt)}
                    </div>
                  </div>
                  <Link href={`/approvals?focus=${a.id}`}>
                    <AllocationStatusBadge status={a.status} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      ) : (
        <Card className="p-6">
          {ranking.data && (
            <div className="grid gap-8 md:grid-cols-2">
              <div>
                <div className="eyebrow mb-3">Rules applied</div>
                <ol className="list-decimal space-y-2 pl-5 text-[14px] text-fg-2">
                  {ranking.data.criteria.rules.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ol>
              </div>
              <div>
                <div className="eyebrow mb-3">Score weights</div>
                <dl className="grid grid-cols-2 gap-3 text-[14px]">
                  {Object.entries(ranking.data.criteria.weights).map(([k, v]) => (
                    <div key={k} className="rounded-2xl bg-surface-2 px-4 py-3">
                      <dt className="text-fg-3 capitalize">{k}</dt>
                      <dd className="display text-[24px] font-semibold">{Math.round(v * 100)}%</dd>
                    </div>
                  ))}
                </dl>
                <p className="mt-4 text-[13px] text-fg-3">
                  Evaluated {formatDateTime(ranking.data.evaluatedAt)} · evidence older than {ranking.data.criteria.evidenceFreshnessMonths} months is stale · capacity limit {ranking.data.criteria.capacityLimitPercent}%.
                </p>
              </div>
            </div>
          )}
        </Card>
      )}

      {proposeFor && (
        <ProposeDialog open onClose={() => setProposeFor(null)} employee={{ id: proposeFor.employeeId, name: proposeFor.name }} demand={d} />
      )}
      <CancelDemandDialog open={cancelOpen} onClose={() => setCancelOpen(false)} demandId={d.id} />
    </>
  );
}

function Fact({ icon, label, value, hint }: { icon: ReactNode; label: string; value: string; hint?: string }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="inline-flex items-center gap-1.5 text-[12px] text-fg-3">
        {icon}
        {label}
      </span>
      <span className="text-[17px] font-semibold">{value}</span>
      {hint && <span className="text-[12px] text-fg-3">{hint}</span>}
    </div>
  );
}

function CancelDemandDialog({ open, onClose, demandId }: { open: boolean; onClose: () => void; demandId: number }) {
  const cancel = useCancelDemand();
  const toast = useToast();
  const [reason, setReason] = useState("");
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Cancel this demand?"
      description="Pending and approved allocations for it are cancelled and their capacity released. History is kept."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Keep demand
          </Button>
          <Button
            variant="danger"
            loading={cancel.isPending}
            onClick={() =>
              cancel.mutate(
                { id: demandId, reason: reason || undefined },
                {
                  onSuccess: () => {
                    toast({ tone: "good", title: "Demand cancelled" });
                    onClose();
                  },
                  onError: (e) => toast({ tone: "bad", title: "Could not cancel", description: e.message }),
                },
              )
            }
          >
            Cancel demand
          </Button>
        </>
      }
    >
      <Field label="Reason (optional)" htmlFor="cancel-reason">
        <Input id="cancel-reason" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </Field>
    </Dialog>
  );
}
