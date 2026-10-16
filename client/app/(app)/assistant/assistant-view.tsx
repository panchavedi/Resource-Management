"use client";

import {
  AlertTriangle,
  BookOpen,
  Bot,
  CheckCircle2,
  ChevronDown,
  CircleHelp,
  Cpu,
  FileSearch,
  GraduationCap,
  History,
  ListChecks,
  ShieldCheck,
  Sparkles,
  Wand2,
  XCircle,
} from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { AvailabilityBar } from "@/components/domain/availability-bar";
import { SkillEvidenceTable } from "@/components/domain/candidate-card";
import { ScoreBreakdown, ScoreRing } from "@/components/domain/score";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { EmptyState, Notice, Skeleton } from "@/components/ui/feedback";
import { Field, Segmented, Select, Switch, Textarea } from "@/components/ui/form";
import { http } from "@/lib/api/client";
import { useAiRuns, useAiStatus, useDemands, useRecommendations, useStaffingAdvice } from "@/lib/api/hooks";
import type { AgentRunSummary, StaffingAdvice } from "@/lib/api/types";
import { useCan } from "@/lib/auth";
import { cn, formatRange, formatScore, relativeTime } from "@/lib/format";

const PLAN = [
  { tool: "get_demand", label: "Reading the demand" },
  { tool: "rank_eligible_candidates", label: "Applying eligibility rules and scoring" },
  { tool: "get_employee_skill_evidence", label: "Collecting stored skill evidence" },
  { tool: "resolve_question_entities", label: "Resolving people and skills in your question" },
  { tool: "build_learning_paths", label: "Matching gaps to the learning catalogue" },
  { tool: "llm_narrate", label: "Drafting the explanation" },
  { tool: "validate_narrative", label: "Fact-checking every statement" },
];

const SUGGESTIONS = [
  "Who is the strongest fit, and what are the risks?",
  "Which candidate can start immediately at full capacity?",
  "Assume the second candidate is an expert in Kubernetes. Are they the best choice now?",
];

export function AssistantView() {
  const params = useSearchParams();
  const canManage = useCan("RESOURCE_MANAGER");
  const status = useAiStatus();
  const demands = useDemands({ status: ["OPEN", "PARTIALLY_FILLED"], size: 100, sort: "startDate,asc" });
  const [chosenDemandId, setDemandId] = useState<number | "">(params.get("demandId") ? Number(params.get("demandId")) : "");
  const demandId = chosenDemandId === "" ? (demands.data?.content[0]?.id ?? "") : chosenDemandId;
  const [question, setQuestion] = useState("");
  const [topN, setTopN] = useState(3);
  const [recordTraining, setRecordTraining] = useState(false);
  const [proposeEmployeeId, setProposeEmployeeId] = useState<number | "">("");
  const ranking = useRecommendations(typeof demandId === "number" ? demandId : NaN);
  const advice = useStaffingAdvice();
  const [loaded, setLoaded] = useState<StaffingAdvice | null>(null);
  const result = loaded ?? advice.data;

  function run() {
    if (typeof demandId !== "number") return;
    setLoaded(null);
    advice.mutate({
      demandId,
      question: question.trim() || undefined,
      topN,
      recordTrainingRecommendations: recordTraining || undefined,
      proposeEmployeeId: proposeEmployeeId || undefined,
    });
  }

  const s = status.data;
  return (
    <>
      <PageHeader
        eyebrow="Agentic AI"
        title={<span className="ai-text">Staffing assistant</span>}
        subtitle="A goal-driven agent that gathers evidence through controlled tools, explains the ranking, discloses what is not on record and suggests learning paths. It never approves anything."
        actions={
          s && (
            <span className={cn("inline-flex items-center gap-2 rounded-full px-3.5 py-2 text-[13px] font-medium", s.effectiveMode === "LIVE" ? "bg-good-soft text-good" : "bg-warn-soft text-warn")}>
              <Cpu className="size-4" />
              {s.effectiveMode === "LIVE" ? `Live · ${s.model}` : "Simulation mode"}
            </span>
          )
        }
      />

      {s && s.effectiveMode !== "LIVE" && (
        <Notice tone="warn" className="mb-6" title="Clearly labelled simulation">
          {s.detail}. Narratives come from a deterministic template; eligibility, scores and evidence are identical to live mode.
        </Notice>
      )}

      <div className="grid gap-6 lg:grid-cols-[380px_1fr]">
        <Card className="h-fit space-y-5 p-6 lg:sticky lg:top-20">
          <div className="flex items-center gap-2 text-[15px] font-semibold"><Wand2 className="size-4 text-ai" /> Goal</div>
          <Field label="Demand to staff" htmlFor="demand">
            <Select id="demand" value={demandId} onChange={(e) => { setDemandId(Number(e.target.value)); setProposeEmployeeId(""); }}>
              {demands.data?.content.map((d) => (
                <option key={d.id} value={d.id}>{d.role} · {d.projectName}</option>
              ))}
            </Select>
          </Field>
          <Field label="Question (optional)" htmlFor="question" hint="Answered only from stored evidence. Requests to assume data are refused and disclosed.">
            <Textarea id="question" maxLength={1000} value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="Ask about fit, risks or a named person…" />
          </Field>
          <div className="flex flex-wrap gap-1.5">
            {SUGGESTIONS.map((q) => (
              <button key={q} onClick={() => setQuestion(q)} className="rounded-full bg-ai-soft px-3 py-1.5 text-left text-[12px] text-ai hover:brightness-110">{q}</button>
            ))}
          </div>
          <Field label="Candidates to explain" htmlFor="topn">
            <Segmented label="Candidates to explain" value={topN} onChange={setTopN} options={[1, 2, 3, 4, 5].map((n) => ({ value: n, label: String(n) }))} />
          </Field>
          <div className="space-y-4 border-t border-line pt-4">
            <Switch checked={recordTraining} onChange={setRecordTraining} label="Record learning actions" description="Save suggested learning paths (deduplicated)" />
            <Field label="Propose for approval (optional)" htmlFor="propose" hint="Creates a PENDING_APPROVAL request; a human approver decides.">
              <Select id="propose" value={proposeEmployeeId} onChange={(e) => setProposeEmployeeId(e.target.value ? Number(e.target.value) : "")}>
                <option value="">Don’t propose anyone</option>
                {ranking.data?.rankedCandidates.filter((c) => c.eligible).map((c) => (
                  <option key={c.employeeId} value={c.employeeId}>#{c.rank} {c.name} · {formatScore(c.totalScore)}</option>
                ))}
              </Select>
            </Field>
          </div>
          <Button variant="ai" size="lg" className="w-full" onClick={run} loading={advice.isPending} disabled={!canManage || typeof demandId !== "number"} icon={<Sparkles className="size-4" />}>
            {advice.isPending ? "Working…" : "Run assistant"}
          </Button>
          {!canManage && <p className="text-center text-[12px] text-fg-3">Running the assistant requires the resource manager role.</p>}
        </Card>

        <section aria-live="polite" className="min-w-0 space-y-6">
          {advice.isPending ? (
            <Working live={s?.effectiveMode === "LIVE"} hasQuestion={Boolean(question.trim())} />
          ) : advice.isError ? (
            <Card className="p-6"><Notice tone="bad" title="The assistant could not complete">{advice.error.message}</Notice></Card>
          ) : result ? (
            <Result advice={result} />
          ) : (
            <Card>
              <EmptyState icon={Bot} title="Set a goal and run the assistant">
                The agent will read the demand, rank candidates with the deterministic rules, collect evidence, draft an explanation with the language model and fact-check it before showing anything.
              </EmptyState>
            </Card>
          )}
          <RecentRuns onOpen={setLoaded} />
        </section>
      </div>
    </>
  );
}

function Working({ live, hasQuestion }: { live: boolean; hasQuestion: boolean }) {
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    const started = Date.now();
    const t = setInterval(() => setElapsed(Math.round((Date.now() - started) / 1000)), 1000);
    return () => clearInterval(t);
  }, []);
  const steps = PLAN.filter((p) => hasQuestion || p.tool !== "resolve_question_entities");
  // Data-gathering steps take milliseconds; the language model dominates the wait.
  const active = Math.min(elapsed < 2 ? Math.floor(elapsed * 2.5) : steps.length - 2, steps.length - 1);
  return (
    <Card className="overflow-hidden">
      <div className="ai-gradient h-1 w-full animate-pulse" />
      <div className="p-6">
        <div className="mb-1 flex items-center justify-between">
          <h2 className="text-[19px] font-semibold">Agent at work</h2>
          <span className="text-[13px] text-fg-3 tabular">{elapsed}s</span>
        </div>
        <p className="mb-5 text-[13px] text-fg-3">
          {live ? "The local model runs on this machine; drafting usually takes 30–120 seconds on CPU." : "Simulation mode: results in a moment."}
        </p>
        <ol className="space-y-3">
          {steps.map((step, i) => (
            <li key={step.tool} className={cn("flex items-center gap-3 text-[14px] transition-opacity", i > active && "opacity-40")}>
              {i < active ? (
                <CheckCircle2 className="size-5 text-good" />
              ) : i === active ? (
                <span className="flex size-5 items-center justify-center"><span className="size-3 animate-ping rounded-full bg-ai" /></span>
              ) : (
                <span className="size-5 rounded-full border-2 border-line" />
              )}
              <span>{step.label}</span>
              <code className="ml-auto hidden font-mono text-[11px] text-fg-3 sm:block">{step.tool}</code>
            </li>
          ))}
        </ol>
      </div>
    </Card>
  );
}

const SOURCE_LABEL: Record<StaffingAdvice["narrativeSource"], { label: string; tone: "good" | "warn" | "ai" | "neutral" }> = {
  LLM: { label: "Model narrative · fully grounded", tone: "good" },
  LLM_WITH_REPAIRS: { label: "Model narrative · partly repaired", tone: "warn" },
  SIMULATED: { label: "Simulated narrative", tone: "neutral" },
  DETERMINISTIC_FALLBACK: { label: "Deterministic fallback", tone: "warn" },
};

function Result({ advice }: { advice: StaffingAdvice }) {
  const src = SOURCE_LABEL[advice.narrativeSource];
  return (
    <div className="space-y-6 animate-fade-up">
      <Card className="p-6">
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <Badge tone={advice.mode === "LIVE" ? "ai" : "neutral"} icon={<Cpu className="size-3.5" />}>{advice.mode === "LIVE" ? advice.model : "Simulation"}</Badge>
          <Badge tone={src.tone}>{src.label}</Badge>
          <Badge tone={advice.validation.passed ? "good" : "bad"} icon={advice.validation.passed ? <ShieldCheck className="size-3.5" /> : <XCircle className="size-3.5" />}>
            {advice.validation.passed ? "Fact-check passed" : "Fact-check failed"}
          </Badge>
          <span className="ml-auto text-[12px] text-fg-3 tabular">Run #{advice.runId} · {(advice.durationMs / 1000).toFixed(1)}s</span>
        </div>
        {advice.simulationNotice && <Notice tone={advice.mode === "LIVE" ? "warn" : "info"} className="mb-4" title={advice.simulationNotice} />}
        <div className="eyebrow mb-2">{advice.demand.role} · {advice.demand.projectName} · {formatRange(advice.demand.startDate, advice.demand.endDate)}</div>
        <p className="display text-[22px] leading-snug font-medium sm:text-[26px]">{advice.summary}</p>
        {advice.question && (
          <div className="mt-5 rounded-2xl bg-ai-soft p-4">
            <div className="mb-1 flex items-center gap-1.5 text-[12px] font-semibold text-ai"><CircleHelp className="size-3.5" /> {advice.question}</div>
            <p className="text-[15px] leading-relaxed">{advice.answer}</p>
          </div>
        )}
      </Card>

      {advice.recommendations.length > 0 && (
        <div className="space-y-4">
          {advice.recommendations.map((r) => <RecommendationCard key={r.candidate.employeeId} rec={r} />)}
        </div>
      )}

      {advice.proposal && (
        <Notice tone={advice.proposal.submitted ? "good" : "bad"} icon={advice.proposal.submitted ? CheckCircle2 : AlertTriangle} title={advice.proposal.submitted ? "Allocation proposed for approval" : `Proposal refused (${advice.proposal.errorCode})`}>
          {advice.proposal.message}{" "}
          {advice.proposal.submitted && <Link href="/approvals" className="font-medium text-accent hover:underline">Open approvals</Link>}
        </Notice>
      )}

      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader title={<span className="flex items-center gap-2"><FileSearch className="size-4 text-warn" /> Not on record</span>} subtitle="Disclosed instead of guessed" />
          <div className="px-6 pb-6">
            {advice.dataGaps.length === 0 ? (
              <p className="text-[13px] text-fg-3">No missing data affects these candidates.</p>
            ) : (
              <ul className="space-y-2 text-[13px]">
                {advice.dataGaps.map((g, i) => (
                  <li key={i} className="flex gap-2">
                    <AlertTriangle className="mt-0.5 size-3.5 shrink-0 text-warn" />
                    <span><span className="font-medium">{g.employeeName ?? "Question"}</span> — {g.detail}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>

        <Card>
          <CardHeader title={<span className="flex items-center gap-2"><GraduationCap className="size-4 text-accent" /> Learning paths</span>} subtitle={advice.recommendations.length ? "For the explained candidates’ gaps" : "For near-miss people (nobody is eligible yet)"} />
          <div className="px-6 pb-6">
            {advice.learningPaths.length === 0 ? (
              <p className="text-[13px] text-fg-3">No skill gaps to close.</p>
            ) : (
              <ul className="space-y-4">
                {advice.learningPaths.map((p, i) => (
                  <li key={i}>
                    <div className="flex items-baseline justify-between gap-2 text-[14px]">
                      <span><span className="font-semibold">{p.employeeName}</span> · {p.skillName}</span>
                      <span className="text-[12px] text-fg-3 tabular">{p.currentLevel ?? "none"} → {p.targetLevel}{p.totalHours ? ` · ${p.totalHours}h` : ""}</span>
                    </div>
                    {p.steps.length > 0 ? (
                      <ol className="mt-1.5 space-y-1">
                        {p.steps.map((step) => (
                          <li key={step.id} className="flex items-center gap-2 text-[13px] text-fg-2">
                            <BookOpen className="size-3.5 shrink-0 text-fg-3" />
                            {step.title} <span className="text-fg-3">· {step.provider} · {step.durationHours}h</span>
                          </li>
                        ))}
                      </ol>
                    ) : (
                      <p className="mt-1 text-[12px] text-warn">{p.catalogNote}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {advice.recordedTrainingRecommendations.length > 0 && (
              <p className="mt-4 text-[12px] text-good">{advice.recordedTrainingRecommendations.length} learning action(s) on record.</p>
            )}
          </div>
        </Card>
      </div>

      {advice.referencedEmployees.length > 0 && (
        <Card>
          <CardHeader title="People named in your question" subtitle="Exactly what is stored about them" />
          <div className="grid gap-4 px-6 pb-6 md:grid-cols-2">
            {advice.referencedEmployees.map((r) => (
              <div key={r.employeeId} className="rounded-2xl bg-surface-2 p-4">
                <div className="flex justify-between text-[15px] font-semibold">
                  <Link href={`/people/${r.employeeId}`} className="hover:underline">{r.name}</Link>
                  <span className="text-[12px] font-normal text-fg-3">{r.inRanking ? `Ranked #${r.rank}` : "Not eligible"}</span>
                </div>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {r.storedSkills.map((sk) => <span key={sk.skillId} className="rounded-full bg-surface px-2.5 py-1 text-[12px]">{sk.skillName} {sk.proficiencyLevel}</span>)}
                </div>
                {r.availability && <div className="mt-3"><AvailabilityBar availability={r.availability} compact /></div>}
                {r.notes.map((n) => <p key={n} className="mt-2 text-[12px] text-fg-3">{n}</p>)}
              </div>
            ))}
          </div>
        </Card>
      )}

      <FactCheck advice={advice} />
      <Trace advice={advice} />

      <Card className="p-6">
        <div className="mb-3 flex items-center gap-2 text-[15px] font-semibold"><ShieldCheck className="size-4 text-good" /> Guardrails</div>
        <ul className="grid gap-2 text-[13px] text-fg-2 md:grid-cols-2">
          {advice.guardrails.map((g) => <li key={g} className="flex gap-2"><CheckCircle2 className="mt-0.5 size-3.5 shrink-0 text-good" />{g}</li>)}
        </ul>
      </Card>
    </div>
  );
}

function RecommendationCard({ rec }: { rec: StaffingAdvice["recommendations"][number] }) {
  const [open, setOpen] = useState(false);
  const c = rec.candidate;
  return (
    <Card className="overflow-hidden">
      <div className="flex gap-5 p-6">
        <ScoreRing score={c.totalScore} size={64} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[13px] font-semibold text-fg-3">#{rec.rank}</span>
            <Link href={`/people/${c.employeeId}`} className="text-[19px] font-semibold tracking-tight hover:underline">{c.name}</Link>
            <span className="text-[13px] text-fg-3">{c.jobTitle}</span>
          </div>
          <p className="mt-2 text-[15px] leading-relaxed text-fg-2">{rec.explanation}</p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <ul className="space-y-1.5 text-[13px]">
              {rec.strengths.map((t) => <li key={t} className="flex gap-2"><CheckCircle2 className="mt-0.5 size-3.5 shrink-0 text-good" />{t}</li>)}
            </ul>
            <ul className="space-y-1.5 text-[13px]">
              {rec.considerations.map((t) => <li key={t} className="flex gap-2"><AlertTriangle className="mt-0.5 size-3.5 shrink-0 text-warn" />{t}</li>)}
            </ul>
          </div>
        </div>
      </div>
      <button onClick={() => setOpen((v) => !v)} aria-expanded={open} className="flex w-full items-center justify-center gap-1 border-t border-line py-2.5 text-[13px] font-medium text-accent hover:bg-surface-2">
        Evidence behind this explanation <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} />
      </button>
      {open && (
        <div className="grid gap-6 border-t border-line bg-surface-2/50 p-6 lg:grid-cols-[1fr_1.4fr]">
          <div className="min-w-0">
            <ScoreBreakdown components={c.scoreBreakdown} />
          </div>
          <div className="min-w-0 space-y-4">
            <SkillEvidenceTable candidate={c} />
            <AvailabilityBar availability={c.availability} />
          </div>
        </div>
      )}
    </Card>
  );
}

function FactCheck({ advice }: { advice: StaffingAdvice }) {
  const v = advice.validation;
  return (
    <Card>
      <CardHeader
        title={<span className="flex items-center gap-2"><ListChecks className="size-4 text-accent" /> Fact-check report</span>}
        subtitle={`${v.llmAttempts} model attempt(s) · ${v.issues.length} rejected statement(s)${v.repairedFields.length ? ` · repaired: ${v.repairedFields.join(", ")}` : ""}`}
      />
      <div className="px-6 pb-6">
        <p className="mb-3 text-[13px] text-fg-2">{v.note}</p>
        {v.issues.length > 0 && (
          <ul className="space-y-2">
            {v.issues.map((i, k) => (
              <li key={k} className="flex flex-wrap items-start gap-2 rounded-xl bg-surface-2 px-3 py-2 text-[13px]">
                <Badge tone="bad">{i.code}</Badge>
                <span className="text-fg-3">attempt {i.attempt} · {i.field}</span>
                <span className="w-full text-fg-2 sm:w-auto">{i.message}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}

function Trace({ advice }: { advice: StaffingAdvice }) {
  return (
    <Card>
      <CardHeader title={<span className="flex items-center gap-2"><Bot className="size-4 text-ai" /> Agent trace</span>} subtitle="Every controlled tool call, in order — persisted for audit" />
      <ol className="relative mx-6 mb-6 space-y-4 border-l border-line pl-6">
        {advice.toolCalls.map((t) => (
          <li key={t.sequence} className="relative">
            <span className={cn("absolute -left-[31px] top-0.5 flex size-[13px] rounded-full ring-4 ring-surface", t.success ? "bg-good" : "bg-bad")} />
            <div className="flex flex-wrap items-baseline gap-x-3">
              <code className="font-mono text-[13px] font-semibold">{t.tool}</code>
              <span className="text-[12px] text-fg-3 tabular">{t.durationMs} ms</span>
            </div>
            <div className="text-[12px] text-fg-3">{t.purpose}</div>
            <div className="mt-0.5 text-[13px] text-fg-2">{t.summary}</div>
          </li>
        ))}
      </ol>
    </Card>
  );
}

const RUN_STATUS: Record<AgentRunSummary["status"], { label: string; tone: "good" | "warn" | "bad" | "accent" }> = {
  RUNNING: { label: "Running", tone: "accent" },
  COMPLETED: { label: "Completed", tone: "good" },
  COMPLETED_WITH_FALLBACK: { label: "With fallback", tone: "warn" },
  FAILED: { label: "Failed", tone: "bad" },
};

function RecentRuns({ onOpen }: { onOpen: (advice: StaffingAdvice) => void }) {
  const runs = useAiRuns();
  const [loadingId, setLoadingId] = useState<number | null>(null);
  async function open(id: number) {
    setLoadingId(id);
    try {
      const detail = await http.get<{ response: StaffingAdvice | null }>(`/api/ai/runs/${id}`);
      if (detail.response) onOpen(detail.response);
    } finally {
      setLoadingId(null);
    }
  }
  return (
    <Card>
      <CardHeader title={<span className="flex items-center gap-2"><History className="size-4 text-fg-2" /> Audit trail</span>} subtitle="Recent agent runs with their exact responses" />
      <div className="px-3 pb-3">
        {runs.isPending ? (
          <Skeleton className="mx-3 h-24" />
        ) : !runs.data?.length ? (
          <p className="px-3 pb-3 text-[13px] text-fg-3">No runs yet.</p>
        ) : (
          <ul>
            {runs.data.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 rounded-xl px-3 py-2.5 hover:bg-surface-2">
                <div className="min-w-0">
                  <div className="truncate text-[14px] font-medium">#{r.id} · {r.goal}</div>
                  <div className="truncate text-[12px] text-fg-3">
                    {r.requestedBy} · {relativeTime(r.createdAt)} · {r.mode}{r.narrativeSource ? ` · ${r.narrativeSource}` : ""} · {(r.durationMs / 1000).toFixed(1)}s
                    {r.question ? ` · “${r.question}”` : ""}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={RUN_STATUS[r.status].tone}>
                    {r.narrativeSource === "LLM_WITH_REPAIRS" ? "Partly repaired" : RUN_STATUS[r.status].label}
                  </Badge>
                  {(r.status === "COMPLETED" || r.status === "COMPLETED_WITH_FALLBACK") && (
                    <Button size="sm" variant="ghost" loading={loadingId === r.id} onClick={() => open(r.id)}>Open</Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
