"use client";

import { ArrowLeft, Briefcase, Mail, MapPin, Pencil, Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { AllocationStatusBadge, AvailabilityBadge, EmploymentBadge, EvidenceBadge } from "@/components/domain/badges";
import { AvailabilityBar } from "@/components/domain/availability-bar";
import { ProposeDialog } from "@/components/domain/propose-dialog";
import { LevelBar } from "@/components/domain/skill-level";
import { Badge } from "@/components/ui/badge";
import { Button, IconButton } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState, ErrorState, Notice, Skeleton } from "@/components/ui/feedback";
import { Field, Input, LevelPicker, Select, Textarea } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import {
  useAvailability,
  useEmployee,
  useEmployeeAllocations,
  useRemoveEmployeeSkill,
  useSkills,
  useTraining,
  useUpdateEmployeeStatus,
  useUpdateTraining,
  useUpsertEmployeeSkill,
} from "@/lib/api/hooks";
import type { EmployeeSkill, EmploymentStatus, EvidenceSource, TrainingStatus } from "@/lib/api/types";
import { useCan } from "@/lib/auth";
import { formatDate, formatRange, humanize, initials, isoToday, levelName } from "@/lib/format";

const SOURCES: EvidenceSource[] = ["CERTIFICATION", "ASSESSMENT", "PROJECT_DELIVERY", "MANAGER_REVIEW", "SELF_DECLARED"];

export function EmployeeView() {
  const { id: rawId } = useParams<{ id: string }>();
  const id = Number(rawId);
  const canManage = useCan("RESOURCE_MANAGER");
  const employee = useEmployee(id);
  const allocations = useEmployeeAllocations(id);
  const training = useTraining({ employeeId: id });
  const updateStatus = useUpdateEmployeeStatus();
  const updateTraining = useUpdateTraining();
  const removeSkill = useRemoveEmployeeSkill(id);
  const toast = useToast();
  const [editing, setEditing] = useState<EmployeeSkill | "new" | null>(null);
  const [proposing, setProposing] = useState(false);
  const [range, setRange] = useState({ startDate: isoToday(), endDate: isoToday(89), allocationPercent: 100 });
  const availability = useAvailability(id, range);

  if (employee.isError) return <Card><ErrorState error={employee.error} onRetry={() => employee.refetch()} /></Card>;
  if (!employee.data) return <div className="space-y-4"><Skeleton className="h-32" /><Skeleton className="h-64" /></div>;
  const e = employee.data;

  return (
    <>
      <Link href="/people" className="mb-4 inline-flex items-center gap-1 text-[14px] text-accent hover:underline">
        <ArrowLeft className="size-4" /> People
      </Link>
      <PageHeader
        eyebrow={`${e.employeeCode} · ${e.department}`}
        title={
          <span className="flex items-center gap-4">
            <span className="ai-gradient flex size-14 shrink-0 items-center justify-center rounded-full text-[20px] font-semibold text-white">{initials(e.name)}</span>
            {e.name}
          </span>
        }
        subtitle={e.jobTitle ?? undefined}
        actions={
          canManage && (
            <>
              <Select
                aria-label="Employment status"
                value={e.employmentStatus}
                onChange={(ev) =>
                  updateStatus.mutate(
                    { id: e.id, employmentStatus: ev.target.value as EmploymentStatus },
                    { onSuccess: (u) => toast({ tone: "good", title: `Status set to ${humanize(u.employmentStatus)}` }) },
                  )
                }
              >
                <option value="ACTIVE">Active</option>
                <option value="BENCH">Bench</option>
                <option value="ON_LEAVE">On leave</option>
                <option value="INACTIVE">Inactive</option>
              </Select>
              <Button icon={<Briefcase className="size-4" />} onClick={() => setProposing(true)} disabled={e.employmentStatus === "ON_LEAVE" || e.employmentStatus === "INACTIVE"}>
                Propose to demand
              </Button>
            </>
          )
        }
      />

      <div className="mb-8 flex flex-wrap gap-2 text-[13px]">
        <EmploymentBadge status={e.employmentStatus} />
        <Badge icon={<MapPin className="size-3.5" />}>{e.location ?? "Location not recorded"}</Badge>
        {e.remoteEligible && <Badge tone="accent">Remote eligible</Badge>}
        <Badge>Capacity {e.capacityPercent}%</Badge>
        <Badge icon={<Mail className="size-3.5" />}>{e.email}</Badge>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
        <Card>
          <CardHeader
            title="Skills"
            subtitle="Stored proficiency and the evidence behind it"
            action={canManage && <Button size="sm" variant="secondary" icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>Record skill</Button>}
          />
          {e.skills.length === 0 ? (
            <EmptyState title="No skills recorded">Nothing is assumed: this person will not match skill requirements until skills are recorded.</EmptyState>
          ) : (
            <ul className="px-3 pb-3">
              {e.skills.map((s) => (
                <li key={s.id} className="flex flex-wrap items-center gap-4 rounded-2xl px-3 py-3 hover:bg-surface-2">
                  <div className="min-w-[140px] flex-1">
                    <div className="font-medium">{s.skillName}</div>
                    <div className="text-[12px] text-fg-3">{s.category}</div>
                  </div>
                  <div className="flex flex-col items-start gap-1">
                    <LevelBar level={s.proficiencyLevel} />
                    <span className="text-[11px] text-fg-3">{levelName(s.proficiencyLevel)}</span>
                  </div>
                  <div className="min-w-[180px] flex-1 text-[12px] text-fg-3">
                    <EvidenceBadge status={s.evidenceStatus} />
                    <div className="mt-1">{humanize(s.evidenceSource)}{s.lastValidatedAt ? ` · ${formatDate(s.lastValidatedAt)}` : ""}</div>
                    {s.evidence && <div className="truncate" title={s.evidence}>{s.evidence}</div>}
                  </div>
                  {canManage && (
                    <div className="flex">
                      <IconButton label={`Re-assess ${s.skillName}`} onClick={() => setEditing(s)}><Pencil className="size-4" /></IconButton>
                      <IconButton
                        label={`Remove ${s.skillName}`}
                        onClick={() => removeSkill.mutate(s.skillId, { onSuccess: () => toast({ tone: "good", title: `${s.skillName} removed` }) })}
                      >
                        <Trash2 className="size-4" />
                      </IconButton>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader title="Availability" subtitle="Day-accurate, from pending and approved allocations" />
            <div className="space-y-4 px-6 pb-6">
              <div className="grid grid-cols-3 gap-2">
                <Field label="From" htmlFor="av-from"><Input id="av-from" type="date" value={range.startDate} onChange={(ev) => setRange((w) => ({ ...w, startDate: ev.target.value }))} /></Field>
                <Field label="To" htmlFor="av-to"><Input id="av-to" type="date" value={range.endDate} min={range.startDate} onChange={(ev) => setRange((w) => ({ ...w, endDate: ev.target.value }))} /></Field>
                <Field label="Needs %" htmlFor="av-pct"><Input id="av-pct" type="number" min={1} max={100} value={range.allocationPercent} onChange={(ev) => setRange((w) => ({ ...w, allocationPercent: Math.min(100, Math.max(1, Number(ev.target.value) || 1)) }))} /></Field>
              </div>
              {availability.isPending ? (
                <Skeleton className="h-16" />
              ) : availability.isError ? (
                <Notice tone="bad" title={availability.error.message} />
              ) : (
                <>
                  <div className="flex items-center justify-between">
                    <AvailabilityBadge status={availability.data.status} />
                    <span className="text-[13px] text-fg-2 tabular">{availability.data.availableDays}/{availability.data.totalDays} days · peak {availability.data.peakLoadPercent}%</span>
                  </div>
                  <AvailabilityBar availability={availability.data} />
                  {availability.data.bookings.length > 0 && (
                    <ul className="space-y-1 text-[13px]">
                      {availability.data.bookings.map((b) => (
                        <li key={b.allocationId} className="flex justify-between gap-2">
                          <Link href={`/demands/${b.demandId}`} className="truncate hover:underline">{b.projectName}</Link>
                          <span className="shrink-0 text-fg-3 tabular">{b.allocationPercent}% · {b.status === "APPROVED" ? "approved" : "pending"}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )}
            </div>
          </Card>

          <Card>
            <CardHeader title="Learning" subtitle="Open and past training recommendations" />
            <div className="px-3 pb-3">
              {training.data?.length ? (
                <ul>
                  {training.data.map((t) => (
                    <li key={t.id} className="rounded-2xl px-3 py-3">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">{t.skillName} → level {t.targetLevel}</span>
                        <Badge tone={t.source === "AI_AGENT" ? "ai" : "neutral"}>{t.source === "AI_AGENT" ? "AI suggested" : "Manual"}</Badge>
                      </div>
                      <p className="mt-0.5 text-[12px] text-fg-3">{t.reason}</p>
                      {t.learningResource && <p className="text-[12px] text-fg-2">{t.learningResource.title} · {t.learningResource.durationHours}h</p>}
                      <div className="mt-2">
                        {canManage && (t.status === "RECOMMENDED" || t.status === "IN_PROGRESS") ? (
                          <Select aria-label="Training status" value={t.status} onChange={(ev) => updateTraining.mutate({ id: t.id, status: ev.target.value as TrainingStatus })} className="h-9 text-[13px]">
                            <option value="RECOMMENDED">Recommended</option>
                            <option value="IN_PROGRESS">In progress</option>
                            <option value="COMPLETED">Completed</option>
                            <option value="DISMISSED">Dismissed</option>
                          </Select>
                        ) : (
                          <Badge>{humanize(t.status)}</Badge>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="px-3 pb-3 text-[13px] text-fg-3">No learning actions yet.</p>
              )}
            </div>
          </Card>
        </div>
      </div>

      <Card className="mt-6">
        <CardHeader title="Allocation history" subtitle="All requests, including cancelled and rejected ones" />
        <div className="px-3 pb-3">
          {allocations.data?.length ? (
            <ul>
              {allocations.data.map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl px-3 py-3 hover:bg-surface-2">
                  <div>
                    <Link href={`/demands/${a.demandId}`} className="font-medium hover:underline">{a.projectName} · {a.role}</Link>
                    <div className="text-[13px] text-fg-2">{formatRange(a.startDate, a.endDate)} · {a.allocationPercent}%</div>
                  </div>
                  <AllocationStatusBadge status={a.status} />
                </li>
              ))}
            </ul>
          ) : (
            <p className="px-3 pb-3 text-[13px] text-fg-3">No allocations.</p>
          )}
        </div>
      </Card>

      {editing && <SkillDialog employeeId={e.id} existing={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
      {proposing && <ProposeDialog open onClose={() => setProposing(false)} employee={{ id: e.id, name: e.name }} />}
    </>
  );
}

function SkillDialog({ employeeId, existing, onClose }: { employeeId: number; existing: EmployeeSkill | null; onClose: () => void }) {
  const skills = useSkills();
  const upsert = useUpsertEmployeeSkill(employeeId);
  const toast = useToast();
  const [skillId, setSkillId] = useState<number | "">(existing?.skillId ?? "");
  const [level, setLevel] = useState(existing?.proficiencyLevel ?? 3);
  const [source, setSource] = useState<EvidenceSource>(existing?.evidenceSource ?? "ASSESSMENT");
  const [evidence, setEvidence] = useState(existing?.evidence ?? "");
  const [validatedAt, setValidatedAt] = useState(existing?.lastValidatedAt ?? isoToday());
  const errors = upsert.error instanceof ApiError ? upsert.error.fieldErrors() : {};

  return (
    <Dialog
      open
      onClose={onClose}
      title={existing ? `Re-assess ${existing.skillName}` : "Record a skill"}
      description="Levels must be backed by evidence. Leave the validation date empty for self-declared skills — they are disclosed as unvalidated."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            disabled={!skillId}
            loading={upsert.isPending}
            onClick={() =>
              upsert.mutate(
                { skillId: Number(skillId), proficiencyLevel: level, evidenceSource: source, evidence: evidence || undefined, lastValidatedAt: validatedAt || null },
                { onSuccess: (s) => { toast({ tone: "good", title: `${s.skillName} saved at level ${s.proficiencyLevel}` }); onClose(); } },
              )
            }
          >
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Skill" htmlFor="skill" error={errors.skillId}>
          <Select id="skill" value={skillId} disabled={!!existing} onChange={(ev) => setSkillId(Number(ev.target.value))}>
            <option value="" disabled>Choose…</option>
            {skills.data?.map((s) => <option key={s.id} value={s.id}>{s.name} · {s.category}</option>)}
          </Select>
        </Field>
        <Field label={`Proficiency · ${levelName(level)}`} htmlFor="level">
          <LevelPicker value={level} onChange={setLevel} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Evidence source" htmlFor="source">
            <Select id="source" value={source} onChange={(ev) => setSource(ev.target.value as EvidenceSource)}>
              {SOURCES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
            </Select>
          </Field>
          <Field label="Validated on" htmlFor="validated" error={errors.lastValidatedAt} hint="Empty = unvalidated">
            <Input id="validated" type="date" max={isoToday()} value={validatedAt} onChange={(ev) => setValidatedAt(ev.target.value)} />
          </Field>
        </div>
        <Field label="Evidence" htmlFor="evidence" error={errors.evidence}>
          <Textarea id="evidence" maxLength={500} value={evidence} onChange={(ev) => setEvidence(ev.target.value)} placeholder="Certification, assessment result, delivered project…" />
        </Field>
        {upsert.error && !(upsert.error instanceof ApiError && upsert.error.code === "VALIDATION_FAILED") && <Notice tone="bad" title={upsert.error.message} />}
      </div>
    </Dialog>
  );
}
