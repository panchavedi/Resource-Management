import { AlertCircle, CheckCircle2, CircleDashed, Clock, MinusCircle, XCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { AllocationStatus, AvailabilityStatus, DemandPriority, DemandStatus, EmploymentStatus, EvidenceStatus } from "@/lib/api/types";
import { allocationStatusMeta, availabilityMeta, demandStatusMeta, employmentMeta, evidenceMeta, priorityMeta } from "@/lib/format";

const icon = "size-3.5";

export function DemandStatusBadge({ status }: { status: DemandStatus }) {
  const m = demandStatusMeta[status];
  return <Badge tone={m.tone}>{m.label}</Badge>;
}

export function PriorityBadge({ priority }: { priority: DemandPriority }) {
  const m = priorityMeta[priority];
  return (
    <Badge tone={m.tone} icon={priority === "CRITICAL" ? <AlertCircle className={icon} /> : undefined}>
      {m.label}
    </Badge>
  );
}

export function AllocationStatusBadge({ status }: { status: AllocationStatus }) {
  const m = allocationStatusMeta[status];
  const Icon = { PENDING_APPROVAL: Clock, APPROVED: CheckCircle2, REJECTED: XCircle, CANCELLED: MinusCircle }[status];
  return (
    <Badge tone={m.tone} icon={<Icon className={icon} />}>
      {m.label}
    </Badge>
  );
}

export function EmploymentBadge({ status }: { status: EmploymentStatus }) {
  const m = employmentMeta[status];
  return <Badge tone={m.tone}>{m.label}</Badge>;
}

export function AvailabilityBadge({ status }: { status: AvailabilityStatus }) {
  const m = availabilityMeta[status];
  const Icon = { FULL: CheckCircle2, PARTIAL: CircleDashed, NONE: XCircle }[status];
  return (
    <Badge tone={m.tone} icon={<Icon className={icon} />}>
      {m.label}
    </Badge>
  );
}

export function EvidenceBadge({ status }: { status: EvidenceStatus }) {
  const m = evidenceMeta[status];
  return (
    <span title={m.hint}>
      <Badge tone={m.tone}>{m.label}</Badge>
    </span>
  );
}
