"use client";

import { Grid3x3, Plus, Search, Users, Star } from "lucide-react";
import Link from "next/link";
import { useDeferredValue, useMemo, useState } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { EmploymentBadge } from "@/components/domain/badges";
import { LevelCell } from "@/components/domain/skill-level";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState, ErrorState, Notice, Skeleton } from "@/components/ui/feedback";
import { Field, Input, Select, Switch } from "@/components/ui/form";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { useCreateEmployee, useCreateSkill, useFacets, useSkills, useSkillsMatrix } from "@/lib/api/hooks";
import type { EmploymentStatus } from "@/lib/api/types";
import { useCan } from "@/lib/auth";
import { levelName } from "@/lib/format";

export function PeopleView() {
  const canManage = useCan("RESOURCE_MANAGER");
  const facets = useFacets();
  const skills = useSkills();
  const [query, setQuery] = useState("");
  const [department, setDepartment] = useState("");
  const [category, setCategory] = useState("");
  const [adding, setAdding] = useState(false);
  const [addingSkill, setAddingSkill] = useState(false);
  const q = useDeferredValue(query);
  const matrix = useSkillsMatrix({ q: q || undefined, department: department || undefined, category: category || undefined });
  const categories = useMemo(() => [...new Set(skills.data?.map((s) => s.category))], [skills.data]);

  // Group the visible skills by category for the two-level header.
  const groups = useMemo(() => {
    const map = new Map<string, { id: number; name: string }[]>();
    matrix.data?.skills.forEach((s) => map.set(s.category, [...(map.get(s.category) ?? []), { id: s.id, name: s.name }]));
    return [...map.entries()];
  }, [matrix.data]);
  const columns = groups.flatMap(([, list]) => list);

  return (
    <>
      <PageHeader
        eyebrow="People"
        title="Skills matrix"
        subtitle="Stored proficiency for every person and skill. Empty means no record — never assumed to be zero."
        actions={canManage && <div className="flex gap-2"><Button variant="secondary" icon={<Star className="size-4" />} onClick={() => setAddingSkill(true)}>Add skill</Button><Button icon={<Plus className="size-4" />} onClick={() => setAdding(true)}>Add employee</Button></div>}
      />

      <div className="mb-5 grid gap-3 sm:grid-cols-[2fr_1fr_1fr]">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-fg-3" />
          <Input aria-label="Search people" placeholder="Search name, code or title" className="pl-10" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <Select aria-label="Department" value={department} onChange={(e) => setDepartment(e.target.value)}>
          <option value="">All departments</option>
          {facets.data?.departments.map((d) => <option key={d}>{d}</option>)}
        </Select>
        <Select aria-label="Skill category" value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">All skill categories</option>
          {categories.map((c) => <option key={c}>{c}</option>)}
        </Select>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-[12px] text-fg-3">
        <span className="font-medium text-fg-2">Proficiency</span>
        {[1, 2, 3, 4, 5].map((l) => (
          <span key={l} className="inline-flex items-center gap-1.5">
            <LevelCell level={l} status="VALIDATED" /> {levelName(l)}
          </span>
        ))}
        <span className="ml-auto text-fg-3">Evidence status is available on each rated cell.</span>
      </div>

      {matrix.isPending ? (
        <Skeleton className="h-[480px] rounded-[22px]" />
      ) : matrix.isError ? (
        <Card><ErrorState error={matrix.error} onRetry={() => matrix.refetch()} /></Card>
      ) : matrix.data.rows.length === 0 ? (
        <Card><EmptyState icon={Users} title="No people match">Clear the search or choose another department.</EmptyState></Card>
      ) : columns.length === 0 ? (
        <Card><EmptyState icon={Grid3x3} title="No skills in this category" /></Card>
      ) : (
        <Card className="scrollbar-thin overflow-auto">
          <table className="border-separate border-spacing-0 text-[13px]">
            <thead>
              <tr>
                <th rowSpan={2} className="sticky top-0 left-0 z-20 min-w-[240px] border-r border-b border-line bg-surface px-5 py-3 text-left align-bottom font-medium text-fg-3">
                  {matrix.data.rows.length} people
                </th>
                {groups.map(([cat, list]) => (
                  <th key={cat} colSpan={list.length} className="sticky top-0 z-10 border-r border-b border-line bg-surface px-2 pt-3 pb-1 text-left text-[11px] font-semibold tracking-wide text-fg-3 uppercase">
                    {cat}
                  </th>
                ))}
              </tr>
              <tr>
                {columns.map((s) => (
                  <th key={s.id} className="sticky top-[34px] z-10 border-r border-b border-line bg-surface px-1 pb-2 font-medium text-fg-2">
                    <div className="mx-auto line-clamp-2 w-14 text-center text-[11px] leading-tight break-words" title={s.name}>{s.name}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {matrix.data.rows.map((row) => (
                <tr key={row.employeeId} className="group">
                  <th scope="row" className="sticky left-0 z-10 border-r border-b border-line bg-surface px-5 py-2.5 text-left font-normal group-hover:bg-surface-2">
                    <Link href={`/people/${row.employeeId}`} className="block">
                      <span className="flex items-center gap-2">
                        <span className="font-semibold text-fg hover:underline">{row.name}</span>
                        {row.employmentStatus !== "ACTIVE" && <EmploymentBadge status={row.employmentStatus} />}
                      </span>
                      <span className="block text-[12px] text-fg-3">{row.jobTitle} · {row.department}</span>
                    </Link>
                  </th>
                  {columns.map((s) => {
                    const cell = row.cells[String(s.id)];
                    return (
                      <td key={s.id} className="border-r border-b border-line px-1 py-1.5 text-center group-hover:bg-surface-2">
                        {cell ? <LevelCell level={cell.level} status={cell.evidenceStatus} /> : <span className="inline-flex h-9 min-w-12 items-center justify-center rounded-lg text-fg-3/40">—</span>}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <AddEmployeeDialog open={adding} onClose={() => setAdding(false)} departments={facets.data?.departments ?? []} />
      <AddSkillDialog open={addingSkill} onClose={() => setAddingSkill(false)} categories={categories} />
    </>
  );
}

function AddSkillDialog({ open, onClose, categories }: { open: boolean; onClose: () => void; categories: string[] }) {
  const create = useCreateSkill();
  const toast = useToast();
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [description, setDescription] = useState("");
  const errors = create.error instanceof ApiError ? create.error.fieldErrors() : {};
  return <Dialog open={open} onClose={onClose} title="Add a skill" description="Extend the shared taxonomy. Existing employee profiles can use this skill immediately." footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={create.isPending} disabled={!name.trim() || !category.trim()} onClick={() => create.mutate({ name: name.trim(), category: category.trim(), description: description.trim() || undefined }, { onSuccess: (skill) => { toast({ tone: "good", title: `${skill.name} added` }); setName(""); setCategory(""); setDescription(""); onClose(); } })}>Add skill</Button></>}>
    <div className="space-y-4">
      <Field label="Skill name" htmlFor="new-skill-name" error={errors.name}><Input id="new-skill-name" maxLength={80} value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Kubernetes" /></Field>
      <Field label="Category" htmlFor="new-skill-category" error={errors.category} hint="Choose an existing category or enter a new one."><Input id="new-skill-category" maxLength={60} list="skill-categories" value={category} onChange={(e) => setCategory(e.target.value)} placeholder="e.g. Cloud & DevOps" /><datalist id="skill-categories">{categories.map((item) => <option key={item} value={item} />)}</datalist></Field>
      <Field label="Description" htmlFor="new-skill-description" error={errors.description}><Input id="new-skill-description" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Optional context for managers" /></Field>
      {create.error && !(create.error instanceof ApiError && create.error.code === "VALIDATION_FAILED") && <Notice tone="bad" title={create.error.message} />}
    </div>
  </Dialog>;
}

function AddEmployeeDialog({ open, onClose, departments }: { open: boolean; onClose: () => void; departments: string[] }) {
  const create = useCreateEmployee();
  const toast = useToast();
  const [form, setForm] = useState({
    employeeCode: "",
    name: "",
    email: "",
    jobTitle: "",
    department: "",
    location: "",
    remoteEligible: false,
    employmentStatus: "ACTIVE" as EmploymentStatus,
    capacityPercent: 100,
  });
  const errors = create.error instanceof ApiError ? create.error.fieldErrors() : {};
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm((f) => ({ ...f, [k]: v }));

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Add employee"
      description="Only staffing-relevant facts are stored. No protected characteristics."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            onClick={() =>
              create.mutate(
                { ...form, employeeCode: form.employeeCode.toUpperCase(), jobTitle: form.jobTitle || undefined, location: form.location || undefined },
                {
                  onSuccess: (e) => {
                    toast({ tone: "good", title: `${e.name} added`, description: "Record their skills on the profile page." });
                    onClose();
                  },
                },
              )
            }
          >
            Add employee
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Employee code" htmlFor="code" error={errors.employeeCode} hint="e.g. E2001">
          <Input id="code" value={form.employeeCode} onChange={(e) => set("employeeCode", e.target.value)} />
        </Field>
        <Field label="Full name" htmlFor="name" error={errors.name}>
          <Input id="name" value={form.name} onChange={(e) => set("name", e.target.value)} />
        </Field>
        <Field label="Work email" htmlFor="email" error={errors.email}>
          <Input id="email" type="email" value={form.email} onChange={(e) => set("email", e.target.value)} />
        </Field>
        <Field label="Job title" htmlFor="title" error={errors.jobTitle}>
          <Input id="title" value={form.jobTitle} onChange={(e) => set("jobTitle", e.target.value)} />
        </Field>
        <Field label="Department" htmlFor="dept" error={errors.department}>
          <Input id="dept" list="departments" value={form.department} onChange={(e) => set("department", e.target.value)} />
          <datalist id="departments">{departments.map((d) => <option key={d} value={d} />)}</datalist>
        </Field>
        <Field label="Location" htmlFor="loc" hint="Leave empty if unknown — it will be disclosed as unknown.">
          <Input id="loc" value={form.location} onChange={(e) => set("location", e.target.value)} />
        </Field>
        <Field label="Status" htmlFor="status">
          <Select id="status" value={form.employmentStatus} onChange={(e) => set("employmentStatus", e.target.value as EmploymentStatus)}>
            <option value="ACTIVE">Active</option>
            <option value="BENCH">Bench</option>
            <option value="ON_LEAVE">On leave</option>
            <option value="INACTIVE">Inactive</option>
          </Select>
        </Field>
        <Field label="Capacity %" htmlFor="cap" error={errors.capacityPercent} hint="100 = full time">
          <Input id="cap" type="number" min={1} max={100} value={form.capacityPercent} onChange={(e) => set("capacityPercent", Number(e.target.value))} />
        </Field>
        <div className="sm:col-span-2">
          <Switch checked={form.remoteEligible} onChange={(v) => set("remoteEligible", v)} label="Remote eligible" />
        </div>
      </div>
      {create.error && !(create.error instanceof ApiError && create.error.code === "VALIDATION_FAILED") && (
        <Notice tone="bad" className="mt-4" title={create.error.message} />
      )}
    </Dialog>
  );
}
