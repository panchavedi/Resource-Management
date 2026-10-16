"use client";

import { ArrowLeft, Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState, type FormEvent } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { Button, IconButton } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/feedback";
import { Field, Input, LevelPicker, Segmented, Select, Switch, Textarea } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { useCreateDemand, useSkills } from "@/lib/api/hooks";
import type { DemandPriority, Skill } from "@/lib/api/types";
import { useCan } from "@/lib/auth";
import { isoToday, levelName } from "@/lib/format";

interface Requirement {
  key: number;
  skillId: number | "";
  minimumProficiency: number;
  mandatory: boolean;
}

/** Client-side checks mirror the server rules for fast feedback; the server remains authoritative. */
function validate(form: { projectName: string; role: string; startDate: string; endDate: string; allocationPercent: number | ""; headcount: number | "" }, requirements: Requirement[]) {
  const errors: Record<string, string> = {};
  if (!form.projectName.trim()) errors.projectName = "Project name is required";
  if (!form.role.trim()) errors.role = "Role is required";
  if (!form.startDate) errors.startDate = "Start date is required";
  else if (form.startDate < isoToday()) errors.startDate = "Start date cannot be in the past";
  if (!form.endDate) errors.endDate = "End date is required";
  else if (form.startDate && form.endDate < form.startDate) errors.endDate = "End date must be on or after the start date";
  if (form.allocationPercent === "" || form.allocationPercent < 1 || form.allocationPercent > 100) errors.allocationPercent = "Between 1 and 100";
  if (form.headcount === "" || form.headcount < 1 || form.headcount > 50) errors.headcount = "Between 1 and 50";
  const ids = requirements.map((r) => r.skillId).filter(Boolean);
  if (requirements.some((r) => r.skillId === "")) errors.requiredSkills = "Choose a skill for every requirement or remove it";
  else if (new Set(ids).size !== ids.length) errors.requiredSkills = "Each skill can be listed once";
  return errors;
}

export function NewDemandView() {
  const router = useRouter();
  const toast = useToast();
  const canManage = useCan("RESOURCE_MANAGER");
  const skills = useSkills();
  const create = useCreateDemand();
  const [form, setForm] = useState({
    projectName: "",
    role: "",
    description: "",
    startDate: isoToday(7),
    endDate: isoToday(97),
    location: "",
    remoteAllowed: true,
    allocationPercent: 100 as number | "",
    headcount: 1 as number | "",
    priority: "HIGH" as DemandPriority,
  });
  const [requirements, setRequirements] = useState<Requirement[]>([{ key: 1, skillId: "", minimumProficiency: 3, mandatory: true }]);
  const [submitted, setSubmitted] = useState(false);
  const clientErrors = useMemo(() => validate(form, requirements), [form, requirements]);
  const serverErrors = create.error instanceof ApiError ? create.error.fieldErrors() : {};
  const errors = submitted ? { ...serverErrors, ...clientErrors } : serverErrors;
  const grouped = useMemo(() => {
    const map = new Map<string, Skill[]>();
    skills.data?.forEach((s) => map.set(s.category, [...(map.get(s.category) ?? []), s]));
    return [...map.entries()];
  }, [skills.data]);

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) => setForm((f) => ({ ...f, [key]: value }));
  const updateRequirement = (key: number, patch: Partial<Requirement>) =>
    setRequirements((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));

  function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitted(true);
    if (Object.keys(clientErrors).length) return;
    create.mutate(
      {
        projectName: form.projectName.trim(),
        role: form.role.trim(),
        description: form.description.trim() || undefined,
        startDate: form.startDate,
        endDate: form.endDate,
        location: form.location.trim() || undefined,
        remoteAllowed: form.remoteAllowed,
        allocationPercent: Number(form.allocationPercent),
        headcount: Number(form.headcount),
        priority: form.priority,
        requiredSkills: requirements.map((r) => ({ skillId: Number(r.skillId), minimumProficiency: r.minimumProficiency, mandatory: r.mandatory })),
      },
      {
        onSuccess: (demand) => {
          toast({ tone: "good", title: "Demand created", description: `${demand.role} · ${demand.projectName}` });
          router.push(`/demands/${demand.id}`);
        },
      },
    );
  }

  if (!canManage) {
    return <Notice tone="warn" title="Only resource managers can create demands." />;
  }

  return (
    <>
      <Link href="/demands" className="mb-4 inline-flex items-center gap-1 text-[14px] text-accent hover:underline">
        <ArrowLeft className="size-4" /> Demands
      </Link>
      <PageHeader eyebrow="Demand intake" title="New demand" subtitle="Describe the role, the period and the skills that matter. Mandatory skills are enforced; optional ones influence ranking." />

      <form onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-6">
          <Card className="space-y-5 p-6">
            <h2 className="text-[17px] font-semibold">Role</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Project" htmlFor="projectName" error={errors.projectName}>
                <Input id="projectName" value={form.projectName} aria-invalid={!!errors.projectName} onChange={(e) => set("projectName", e.target.value)} placeholder="e.g. Atlas Payments Platform" />
              </Field>
              <Field label="Role" htmlFor="role" error={errors.role}>
                <Input id="role" value={form.role} aria-invalid={!!errors.role} onChange={(e) => set("role", e.target.value)} placeholder="e.g. Senior Java Engineer" />
              </Field>
            </div>
            <Field label="Description" htmlFor="description" hint="Optional context for candidates and approvers.">
              <Textarea id="description" maxLength={2000} value={form.description} onChange={(e) => set("description", e.target.value)} />
            </Field>
            <Field label="Priority" htmlFor="priority">
              <Segmented
                label="Priority"
                value={form.priority}
                onChange={(v) => set("priority", v)}
                options={[
                  { value: "CRITICAL", label: "Critical" },
                  { value: "HIGH", label: "High" },
                  { value: "MEDIUM", label: "Medium" },
                  { value: "LOW", label: "Low" },
                ]}
              />
            </Field>
          </Card>

          <Card className="space-y-4 p-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-[17px] font-semibold">Skills</h2>
                <p className="text-[13px] text-fg-2">Proficiency 1 (awareness) to 5 (expert).</p>
              </div>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                icon={<Plus className="size-4" />}
                onClick={() => setRequirements((rs) => [...rs, { key: Date.now(), skillId: "", minimumProficiency: 3, mandatory: false }])}
              >
                Add skill
              </Button>
            </div>
            {errors.requiredSkills && <p role="alert" className="text-[13px] text-bad">{errors.requiredSkills}</p>}
            <ul className="space-y-3">
              {requirements.map((r) => (
                <li key={r.key} className="grid gap-3 rounded-2xl bg-surface-2 p-4 sm:grid-cols-[1fr_auto_auto_auto] sm:items-center">
                  <Select aria-label="Skill" value={r.skillId} onChange={(e) => updateRequirement(r.key, { skillId: e.target.value ? Number(e.target.value) : "" })}>
                    <option value="">Choose a skill…</option>
                    {grouped.map(([category, list]) => (
                      <optgroup key={category} label={category}>
                        {list.map((s) => (
                          <option key={s.id} value={s.id}>
                            {s.name}
                          </option>
                        ))}
                      </optgroup>
                    ))}
                  </Select>
                  <div className="flex flex-col gap-1">
                    <LevelPicker label="Minimum level" value={r.minimumProficiency} onChange={(v) => updateRequirement(r.key, { minimumProficiency: v })} />
                    <span className="text-[11px] text-fg-3">At least {levelName(r.minimumProficiency).toLowerCase()}</span>
                  </div>
                  <Segmented
                    size="sm"
                    label="Requirement type"
                    value={r.mandatory ? "m" : "o"}
                    onChange={(v) => updateRequirement(r.key, { mandatory: v === "m" })}
                    options={[
                      { value: "m", label: "Mandatory" },
                      { value: "o", label: "Optional" },
                    ]}
                  />
                  <IconButton type="button" label="Remove skill" onClick={() => setRequirements((rs) => rs.filter((x) => x.key !== r.key))}>
                    <Trash2 className="size-4" />
                  </IconButton>
                </li>
              ))}
            </ul>
            {requirements.length === 0 && <p className="text-[13px] text-fg-3">No skill requirements: every assignable employee will be considered.</p>}
          </Card>
        </div>

        <div className="space-y-6">
          <Card className="space-y-4 p-6 lg:sticky lg:top-20">
            <h2 className="text-[17px] font-semibold">Period &amp; capacity</h2>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Start" htmlFor="startDate" error={errors.startDate}>
                <Input id="startDate" type="date" min={isoToday()} value={form.startDate} aria-invalid={!!errors.startDate} onChange={(e) => set("startDate", e.target.value)} />
              </Field>
              <Field label="End" htmlFor="endDate" error={errors.endDate}>
                <Input id="endDate" type="date" min={form.startDate} value={form.endDate} aria-invalid={!!errors.endDate} onChange={(e) => set("endDate", e.target.value)} />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Allocation %" htmlFor="allocationPercent" error={errors.allocationPercent}>
                <Input id="allocationPercent" type="number" min={1} max={100} value={form.allocationPercent} aria-invalid={!!errors.allocationPercent} onChange={(e) => set("allocationPercent", e.target.value === "" ? "" : Number(e.target.value))} />
              </Field>
              <Field label="Headcount" htmlFor="headcount" error={errors.headcount}>
                <Input id="headcount" type="number" min={1} max={50} value={form.headcount} aria-invalid={!!errors.headcount} onChange={(e) => set("headcount", e.target.value === "" ? "" : Number(e.target.value))} />
              </Field>
            </div>
            <Field label="Location" htmlFor="location" hint="Leave empty for a location-agnostic demand.">
              <Input id="location" value={form.location} onChange={(e) => set("location", e.target.value)} placeholder="e.g. Bengaluru" />
            </Field>
            <Switch checked={form.remoteAllowed} onChange={(v) => set("remoteAllowed", v)} label="Remote allowed" description="Remote-eligible people elsewhere still score well." />
            {create.error && !(create.error instanceof ApiError && create.error.code === "VALIDATION_FAILED") && (
              <Notice tone="bad" title={create.error.message} />
            )}
            <Button type="submit" size="lg" className="w-full" loading={create.isPending}>
              Create demand
            </Button>
          </Card>
        </div>
      </form>
    </>
  );
}
