"use client";

import { ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Notice } from "@/components/ui/feedback";
import { Field, Input, Select, Textarea } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { useDemands, useProposeAllocation } from "@/lib/api/hooks";
import type { Demand } from "@/lib/api/types";
import { formatRange } from "@/lib/format";

/**
 * Proposes an allocation (PENDING_APPROVAL). The server re-checks eligibility and capacity now and
 * again at approval, so the dialog only collects intent and shows the server's verdict.
 */
export function ProposeDialog({ open, onClose, employee, demand }: { open: boolean; onClose: () => void; employee: { id: number; name: string }; demand?: Demand }) {
  return (
    <Dialog open={open} onClose={onClose} title={`Propose ${employee.name}`} description="Creates a request that an approver must confirm.">
      {open && <ProposeForm employee={employee} demand={demand} onDone={onClose} />}
    </Dialog>
  );
}

function ProposeForm({ employee, demand, onDone }: { employee: { id: number; name: string }; demand?: Demand; onDone: () => void }) {
  const toast = useToast();
  const propose = useProposeAllocation();
  const openDemands = useDemands({ status: ["OPEN", "PARTIALLY_FILLED"], size: 100, sort: "startDate" }, !demand);
  const [demandId, setDemandId] = useState<number | undefined>(demand?.id);
  const selected = demand ?? openDemands.data?.content.find((d) => d.id === demandId);
  const [startDate, setStartDate] = useState(demand?.startDate ?? "");
  const [endDate, setEndDate] = useState(demand?.endDate ?? "");
  const [percent, setPercent] = useState<number | "">(demand?.allocationPercent ?? "");
  const [note, setNote] = useState("");
  const error = propose.error instanceof ApiError ? propose.error : null;
  const fieldErrors = error?.fieldErrors() ?? {};

  function choose(id: number) {
    setDemandId(id);
    const d = openDemands.data?.content.find((x) => x.id === id);
    if (d) {
      setStartDate(d.startDate);
      setEndDate(d.endDate);
      setPercent(d.allocationPercent);
    }
  }

  function submit() {
    if (!selected) return;
    propose.mutate(
      {
        employeeId: employee.id,
        demandId: selected.id,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
        allocationPercent: percent === "" ? undefined : percent,
        note: note || undefined,
      },
      {
        onSuccess: (allocation) => {
          toast({ tone: "good", title: "Allocation proposed", description: `${allocation.employeeName} → ${allocation.projectName} is awaiting approval.` });
          onDone();
        },
      },
    );
  }

  return (
    <div className="space-y-4">
      {!demand && (
        <Field label="Demand" htmlFor="demand">
          <Select id="demand" value={demandId ?? ""} onChange={(e) => choose(Number(e.target.value))}>
            <option value="" disabled>
              Choose an open demand…
            </option>
            {openDemands.data?.content.map((d) => (
              <option key={d.id} value={d.id}>
                {d.projectName} · {d.role} ({formatRange(d.startDate, d.endDate)})
              </option>
            ))}
          </Select>
        </Field>
      )}
      {selected && (
        <>
          <div className="rounded-2xl bg-surface-2 px-4 py-3 text-[14px]">
            <div className="font-semibold">
              {selected.projectName} · {selected.role}
            </div>
            <div className="text-fg-2">
              {formatRange(selected.startDate, selected.endDate)} · {selected.allocationPercent}% requested
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Start" htmlFor="start" error={fieldErrors.startDate}>
              <Input id="start" type="date" value={startDate} min={selected.startDate} max={selected.endDate} onChange={(e) => setStartDate(e.target.value)} />
            </Field>
            <Field label="End" htmlFor="end" error={fieldErrors.endDate}>
              <Input id="end" type="date" value={endDate} min={selected.startDate} max={selected.endDate} onChange={(e) => setEndDate(e.target.value)} />
            </Field>
          </div>
          <Field label="Allocation %" htmlFor="pct" error={fieldErrors.allocationPercent}>
            <Input id="pct" type="number" min={1} max={100} value={percent} onChange={(e) => setPercent(e.target.value === "" ? "" : Number(e.target.value))} />
          </Field>
          <Field label="Note for the approver" htmlFor="note">
            <Textarea id="note" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Why this person?" />
          </Field>
        </>
      )}
      {error && (
        <Notice tone="bad" title={error.message}>
          {error.code === "CAPACITY_EXCEEDED" && "Pick a smaller percentage or a shorter period, or check their allocations."}
          {error.code === "SELF_APPROVAL_NOT_ALLOWED" && "A different approver must confirm this."}
        </Notice>
      )}
      <Notice tone="info" icon={ShieldCheck}>
        Eligibility and capacity are validated now and again when an approver confirms. Track it in{" "}
        <Link href="/approvals" className="font-medium text-accent hover:underline">
          Approvals
        </Link>
        .
      </Notice>
      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button onClick={submit} disabled={!selected} loading={propose.isPending}>
          Submit for approval
        </Button>
      </div>
    </div>
  );
}
