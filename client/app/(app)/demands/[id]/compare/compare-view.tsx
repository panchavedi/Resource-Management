"use client";

import { ArrowLeft, Crown } from "lucide-react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useState, type CSSProperties, type ReactNode } from "react";
import { PageHeader } from "@/components/layout/app-shell";
import { AvailabilityBadge, EvidenceBadge } from "@/components/domain/badges";
import { AvailabilityBar } from "@/components/domain/availability-bar";
import { ProposeDialog } from "@/components/domain/propose-dialog";
import { Meter, ScoreRing } from "@/components/domain/score";
import { LevelBar } from "@/components/domain/skill-level";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/feedback";
import { useRecommendations } from "@/lib/api/hooks";
import type { Candidate } from "@/lib/api/types";
import { useCan } from "@/lib/auth";
import { cn, formatScore, initials, locationFitMeta } from "@/lib/format";

export function CompareView() {
  const { id } = useParams<{ id: string }>();
  const params = useSearchParams();
  const demandId = Number(id);
  const ids = (params.get("ids") ?? "").split(",").map(Number).filter(Boolean).slice(0, 3);
  const ranking = useRecommendations(demandId);
  const canManage = useCan("RESOURCE_MANAGER");
  const [proposeFor, setProposeFor] = useState<Candidate | null>(null);

  if (ranking.isError) return <Card><ErrorState error={ranking.error} onRetry={() => ranking.refetch()} /></Card>;
  if (!ranking.data) return <Skeleton className="h-96" />;

  const all = [...ranking.data.rankedCandidates, ...ranking.data.excludedCandidates];
  const candidates = ids.map((i) => all.find((c) => c.employeeId === i)).filter((c): c is Candidate => Boolean(c));
  const d = ranking.data.demand;
  const open = d.status === "OPEN" || d.status === "PARTIALLY_FILLED";

  if (candidates.length < 2) {
    return (
      <Card>
        <EmptyState title="Pick at least two candidates" action={<Link href={`/demands/${demandId}`} className="text-accent hover:underline">Back to candidates</Link>}>
          Select candidates on the demand page, then choose Compare.
        </EmptyState>
      </Card>
    );
  }

  const best = (value: (c: Candidate) => number) => Math.max(...candidates.map(value));
  const cols = { gridTemplateColumns: `minmax(150px, 200px) repeat(${candidates.length}, minmax(220px, 1fr))` };

  return (
    <>
      <Link href={`/demands/${demandId}`} className="mb-4 inline-flex items-center gap-1 text-[14px] text-accent hover:underline">
        <ArrowLeft className="size-4" /> {d.role}
      </Link>
      <PageHeader eyebrow="Candidate comparison" title="Side by side" subtitle={`${d.role} · ${d.projectName}. Every value comes from stored records; the best value in each row is marked.`} />

      <Card className="scrollbar-thin overflow-x-auto">
        <div className="min-w-fit">
          {/* header */}
          <div className="sticky top-0 grid gap-4 border-b border-line bg-surface px-6 py-5" style={cols}>
            <div />
            {candidates.map((c) => (
              <div key={c.employeeId} className="flex flex-col items-center gap-3 text-center">
                <div className="flex size-14 items-center justify-center rounded-full bg-surface-3 text-[17px] font-semibold text-fg-2">{initials(c.name)}</div>
                <div>
                  <Link href={`/people/${c.employeeId}`} className="text-[17px] font-semibold hover:underline">{c.name}</Link>
                  <div className="text-[13px] text-fg-2">{c.jobTitle}</div>
                  <div className="mt-1 text-[12px] text-fg-3">{c.rank ? `Ranked #${c.rank}` : "Excluded"}</div>
                </div>
                {canManage && open && c.eligible && (
                  <Button size="sm" onClick={() => setProposeFor(c)}>Propose</Button>
                )}
              </div>
            ))}
          </div>

          <Row label="Total score" cols={cols}>
            {candidates.map((c) => (
              <Cell key={c.employeeId} best={c.totalScore === best((x) => x.totalScore)}>
                <ScoreRing score={c.totalScore} size={64} muted={!c.eligible} />
              </Cell>
            ))}
          </Row>

          {candidates[0].scoreBreakdown.map((factor) => (
            <Row key={factor.factor} label={factor.label} sub={`weight ${Math.round(factor.weight * 100)}%`} cols={cols}>
              {candidates.map((c) => {
                const comp = c.scoreBreakdown.find((x) => x.factor === factor.factor)!;
                return (
                  <Cell key={c.employeeId} best={comp.score === best((x) => x.scoreBreakdown.find((y) => y.factor === factor.factor)!.score)}>
                    <div className="w-full">
                      <div className="mb-1 flex justify-between text-[13px]">
                        <span className="font-semibold tabular">{formatScore(comp.score)}</span>
                        <span className="text-fg-3 tabular">+{formatScore(comp.weightedScore)}</span>
                      </div>
                      <Meter value={comp.score} label={`${factor.label} ${comp.score}`} />
                      <p className="mt-1.5 text-[12px] text-fg-3">{comp.explanation}</p>
                    </div>
                  </Cell>
                );
              })}
            </Row>
          ))}

          {d.requiredSkills.map((req) => (
            <Row key={req.skillId} label={req.skillName} sub={`${req.mandatory ? "Mandatory" : "Optional"} · needs ${req.minimumProficiency}`} cols={cols}>
              {candidates.map((c) => {
                const m = c.skillMatches.find((s) => s.skillId === req.skillId);
                const level = (x: Candidate) => x.skillMatches.find((s) => s.skillId === req.skillId)?.actualLevel ?? 0;
                return (
                  <Cell key={c.employeeId} best={!!m?.actualLevel && level(c) === best(level)}>
                    <div className="flex flex-col items-start gap-1.5">
                      <LevelBar level={m?.actualLevel ?? null} required={req.minimumProficiency} />
                      {m && <EvidenceBadge status={m.evidenceStatus} />}
                    </div>
                  </Cell>
                );
              })}
            </Row>
          ))}

          <Row label="Availability" sub={`${d.allocationPercent}% requested`} cols={cols}>
            {candidates.map((c) => (
              <Cell key={c.employeeId} best={c.availability.coveragePercent === best((x) => x.availability.coveragePercent)}>
                <div className="w-full space-y-2">
                  <AvailabilityBadge status={c.availability.status} />
                  <AvailabilityBar availability={c.availability} compact />
                  <div className="text-[12px] text-fg-3 tabular">
                    {c.availability.availableDays}/{c.availability.totalDays} days · peak load {c.availability.peakLoadPercent}%
                  </div>
                </div>
              </Cell>
            ))}
          </Row>

          <Row label="Location" cols={cols}>
            {candidates.map((c) => (
              <Cell key={c.employeeId} best={c.locationFit.score === best((x) => x.locationFit.score)}>
                <div className="text-[13px]">
                  <div className="font-medium">{locationFitMeta[c.locationFit.fit]}</div>
                  <div className="text-fg-3">{c.locationFit.explanation}</div>
                </div>
              </Cell>
            ))}
          </Row>

          <Row label="Gaps & disclosures" cols={cols} last>
            {candidates.map((c) => (
              <Cell key={c.employeeId}>
                <ul className="space-y-1 text-[12px]">
                  {[...c.exclusionReasons.map((t) => ({ t, tone: "text-bad" })), ...c.gaps.map((g) => ({ t: `${g.skillName}: ${g.actualLevel ?? "no record"} → ${g.requiredLevel}`, tone: "text-warn" })), ...c.dataGaps.map((t) => ({ t: `Not on record: ${t}`, tone: "text-fg-3" }))].map(({ t, tone }) => (
                    <li key={t} className={tone}>{t}</li>
                  ))}
                  {c.exclusionReasons.length + c.gaps.length + c.dataGaps.length === 0 && <li className="text-good">No gaps</li>}
                </ul>
              </Cell>
            ))}
          </Row>
        </div>
      </Card>

      {proposeFor && <ProposeDialog open onClose={() => setProposeFor(null)} employee={{ id: proposeFor.employeeId, name: proposeFor.name }} demand={d} />}
    </>
  );
}

function Row({ label, sub, cols, children, last }: { label: string; sub?: string; cols: CSSProperties; children: ReactNode; last?: boolean }) {
  return (
    <div className={cn("grid items-center gap-4 px-6 py-4", !last && "border-b border-line")} style={cols}>
      <div>
        <div className="text-[14px] font-medium">{label}</div>
        {sub && <div className="text-[12px] text-fg-3">{sub}</div>}
      </div>
      {children}
    </div>
  );
}

function Cell({ children, best }: { children: ReactNode; best?: boolean }) {
  return (
    <div className={cn("relative flex items-center justify-center rounded-2xl px-3 py-2", best && "bg-good-soft/60")}>
      {children}
      {best && (
        <span className="absolute top-1.5 right-2 inline-flex items-center gap-0.5 text-[10px] font-semibold text-good">
          <Crown className="size-3" /> Best
        </span>
      )}
    </div>
  );
}
