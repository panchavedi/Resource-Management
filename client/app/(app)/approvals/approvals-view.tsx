"use client";

import { Check, CheckCircle2, CircleSlash, Clock, History, Inbox, MinusCircle, ShieldCheck, X, XCircle } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { AllocationStatusBadge } from "@/components/domain/badges";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState, ErrorState, Notice, Skeleton, SkeletonRows } from "@/components/ui/feedback";
import { Field, Textarea } from "@/components/ui/form";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import { useAllocation, useAllocationDecision, useAllocations, useCancelAllocation } from "@/lib/api/hooks";
import type { Allocation, AllocationStatus } from "@/lib/api/types";
import { useCan, useSession } from "@/lib/auth";
import { formatDateTime, formatRange, initials, relativeTime } from "@/lib/format";

type Tab = "pending" | "approved" | "history";
const FILTER: Record<Tab, AllocationStatus[]> = {
  pending: ["PENDING_APPROVAL"],
  approved: ["APPROVED"],
  history: ["REJECTED", "CANCELLED"],
};

type Action = { kind: "approve" | "reject" | "cancel"; allocation: Allocation };

export function ApprovalsView() {
  const params = useSearchParams();
  const focus = params.get("focus");
  const [tab, setTab] = useState<Tab>("pending");
  const [page, setPage] = useState(0);
  const [action, setAction] = useState<Action | null>(null);
  const [historyId, setHistoryId] = useState<number | null>(focus ? Number(focus) : null);
  const session = useSession();
  const canApprove = useCan("APPROVER");
  const canCancel = useCan("RESOURCE_MANAGER", "APPROVER");
  const list = useAllocations({ status: FILTER[tab], page, size: 10 });
  const counts = {
    pending: useAllocations({ status: FILTER.pending, size: 1 }).data?.totalElements,
    approved: useAllocations({ status: FILTER.approved, size: 1 }).data?.totalElements,
    history: useAllocations({ status: FILTER.history, size: 1 }).data?.totalElements,
  };

  return (
    <>
      <PageHeader eyebrow="Allocation workflow" title="Approvals" subtitle="Every allocation needs an authorised approver. Eligibility and capacity are re-validated at the moment of approval." />

      <Notice tone="info" icon={ShieldCheck} className="mb-6" title="Four-eyes rule">
        The person who proposed an allocation cannot approve it. Approval re-checks status, mandatory skills, headcount and day-by-day capacity under a database lock.
      </Notice>

      <div className="mb-5">
        <Tabs
          value={tab}
          onChange={(t) => {
            setTab(t);
            setPage(0);
          }}
          tabs={[
            { value: "pending", label: "Pending", count: counts.pending },
            { value: "approved", label: "Approved", count: counts.approved },
            { value: "history", label: "Rejected & cancelled", count: counts.history },
          ]}
        />
      </div>

      {list.isPending ? (
        <SkeletonRows rows={4} />
      ) : list.isError ? (
        <Card><ErrorState error={list.error} onRetry={() => list.refetch()} /></Card>
      ) : list.data.content.length === 0 ? (
        <Card>
          <EmptyState icon={Inbox} title={tab === "pending" ? "Inbox zero" : "Nothing here yet"}>
            {tab === "pending" ? "No allocations are waiting for a decision." : "Allocations will appear here as they move through the workflow."}
          </EmptyState>
        </Card>
      ) : (
        <ul className="space-y-3">
          {list.data.content.map((a, i) => {
            const own = session?.user.username === a.requestedBy;
            return (
              <li key={a.id} className="card animate-fade-up p-5" style={{ animationDelay: `${i * 30}ms` }}>
                <div className="flex flex-col gap-4 lg:flex-row lg:items-center">
                  <div className="flex min-w-0 flex-1 items-center gap-4">
                    <div className="flex size-11 shrink-0 items-center justify-center rounded-full bg-surface-3 text-[14px] font-semibold text-fg-2">{initials(a.employeeName)}</div>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Link href={`/people/${a.employeeId}`} className="text-[17px] font-semibold hover:underline">{a.employeeName}</Link>
                        <span className="text-fg-3">→</span>
                        <Link href={`/demands/${a.demandId}`} className="text-[15px] font-medium hover:underline">{a.projectName}</Link>
                      </div>
                      <div className="text-[13px] text-fg-2">
                        {a.role} · {formatRange(a.startDate, a.endDate)} · <span className="font-medium text-fg">{a.allocationPercent}%</span>
                      </div>
                      <div className="mt-1 text-[12px] text-fg-3">
                        Requested by {a.requestedBy} {relativeTime(a.requestedAt)}
                        {a.requestNote && <> · “{a.requestNote}”</>}
                        {a.decidedBy && <> · decided by {a.decidedBy}</>}
                        {a.cancellationReason && <> · cancelled: {a.cancellationReason}</>}
                      </div>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <AllocationStatusBadge status={a.status} />
                    <Button size="sm" variant="ghost" icon={<History className="size-4" />} onClick={() => setHistoryId(a.id)}>History</Button>
                    {a.status === "PENDING_APPROVAL" && canApprove && (
                      <>
                        <Button size="sm" variant="secondary" icon={<X className="size-4" />} onClick={() => setAction({ kind: "reject", allocation: a })}>Reject</Button>
                        <Button size="sm" icon={<Check className="size-4" />} disabled={own} title={own ? "You requested this allocation" : undefined} onClick={() => setAction({ kind: "approve", allocation: a })}>
                          Approve
                        </Button>
                      </>
                    )}
                    {(a.status === "PENDING_APPROVAL" || a.status === "APPROVED") && canCancel && (
                      <Button size="sm" variant="danger" icon={<CircleSlash className="size-4" />} onClick={() => setAction({ kind: "cancel", allocation: a })}>
                        {a.status === "PENDING_APPROVAL" ? "Withdraw" : "Cancel"}
                      </Button>
                    )}
                  </div>
                </div>
                {own && a.status === "PENDING_APPROVAL" && canApprove && (
                  <p className="mt-3 text-[12px] text-fg-3">You proposed this allocation, so another approver must confirm it.</p>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {list.data && list.data.totalPages > 1 && (
        <div className="mt-6 flex justify-center gap-3">
          <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
          <span className="self-center text-[13px] text-fg-2 tabular">Page {page + 1} of {list.data.totalPages}</span>
          <Button size="sm" variant="secondary" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage((p) => p + 1)}>Next</Button>
        </div>
      )}

      {action && <ActionDialog action={action} onClose={() => setAction(null)} />}
      <HistoryDialog id={historyId} onClose={() => setHistoryId(null)} />
    </>
  );
}

function ActionDialog({ action, onClose }: { action: Action; onClose: () => void }) {
  const decide = useAllocationDecision();
  const cancel = useCancelAllocation();
  const toast = useToast();
  const [text, setText] = useState("");
  const a = action.allocation;
  const pending = decide.isPending || cancel.isPending;
  const error = decide.error ?? cancel.error;
  const copy = {
    approve: { title: "Approve allocation", button: "Approve", label: "Note (optional)", required: false },
    reject: { title: "Reject allocation", button: "Reject", label: "Reason for the requester", required: false },
    cancel: { title: a.status === "APPROVED" ? "Cancel allocation" : "Withdraw request", button: "Confirm", label: "Reason (required)", required: true },
  }[action.kind];

  function submit() {
    const done = (title: string) => () => {
      toast({ tone: "good", title, description: `${a.employeeName} · ${a.projectName}` });
      onClose();
    };
    if (action.kind === "cancel") {
      cancel.mutate({ id: a.id, reason: text.trim() }, { onSuccess: done("Allocation cancelled — capacity released") });
    } else {
      decide.mutate({ id: a.id, action: action.kind, note: text.trim() || undefined }, { onSuccess: done(action.kind === "approve" ? "Allocation approved" : "Allocation rejected") });
    }
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title={copy.title}
      description={`${a.employeeName} → ${a.projectName} · ${formatRange(a.startDate, a.endDate)} · ${a.allocationPercent}%`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Back</Button>
          <Button variant={action.kind === "approve" ? "primary" : "danger"} loading={pending} disabled={copy.required && !text.trim()} onClick={submit}>
            {copy.button}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {action.kind === "approve" && (
          <Notice tone="info" icon={ShieldCheck}>On approval the server re-validates eligibility and capacity. If something changed since the request, approval is refused with the reason.</Notice>
        )}
        <Field label={copy.label} htmlFor="action-text">
          <Textarea id="action-text" maxLength={500} value={text} onChange={(e) => setText(e.target.value)} />
        </Field>
        {error && <Notice tone="bad" title={error.message} />}
      </div>
    </Dialog>
  );
}

const EVENT_ICON = { REQUESTED: Clock, APPROVED: CheckCircle2, REJECTED: XCircle, CANCELLED: MinusCircle };
const EVENT_TONE = { REQUESTED: "text-accent", APPROVED: "text-good", REJECTED: "text-bad", CANCELLED: "text-fg-3" };

function HistoryDialog({ id, onClose }: { id: number | null; onClose: () => void }) {
  const allocation = useAllocation(id);
  return (
    <Dialog open={id !== null} onClose={onClose} title="Allocation history" description="Append-only record of every state change.">
      {allocation.isPending ? (
        <Skeleton className="h-40" />
      ) : allocation.isError ? (
        <ErrorState error={allocation.error} />
      ) : (
        <div>
          <div className="mb-4 rounded-2xl bg-surface-2 px-4 py-3 text-[14px]">
            <div className="font-semibold">{allocation.data.employeeName} → {allocation.data.projectName}</div>
            <div className="text-fg-2">{formatRange(allocation.data.startDate, allocation.data.endDate)} · {allocation.data.allocationPercent}%</div>
          </div>
          <ol className="relative space-y-5 border-l border-line pl-6">
            {allocation.data.history?.map((h, i) => {
              const Icon = EVENT_ICON[h.eventType];
              return (
                <li key={i} className="relative">
                  <span className="absolute -left-[33px] flex size-5 items-center justify-center rounded-full bg-surface">
                    <Icon className={`size-5 ${EVENT_TONE[h.eventType]}`} />
                  </span>
                  <div className="text-[14px] font-semibold">{h.eventType.charAt(0) + h.eventType.slice(1).toLowerCase()} by {h.actor}</div>
                  <div className="text-[12px] text-fg-3">{formatDateTime(h.occurredAt)} · {h.fromStatus ?? "—"} → {h.toStatus}</div>
                  {h.note && <div className="mt-1 text-[13px] text-fg-2">“{h.note}”</div>}
                </li>
              );
            })}
          </ol>
        </div>
      )}
    </Dialog>
  );
}
