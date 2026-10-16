"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { http } from "./client";
import type {
  AgentRunSummary,
  AiStatus,
  Allocation,
  AllocationStatus,
  AvailableResource,
  AvailableResourceRow,
  Availability,
  DashboardSummary,
  Demand,
  DemandPriority,
  DemandStatus,
  Employee,
  EmployeeSkill,
  EmploymentStatus,
  EvidenceSource,
  MatchResult,
  ManagedAccount,
  AccountRegistration,
  OpenDemandRow,
  Page,
  Skill,
  SkillGapReport,
  SkillProficiencyRow,
  SkillsMatrix,
  StaffingAdvice,
  TrainingRecommendation,
  TrainingStatus,
  UnstaffableDemandRow,
  UtilizationReport,
} from "./types";

/** Central query keys: mutations invalidate by prefix, so related screens refresh together. */
export const keys = {
  accounts: ["accounts"] as const,
  skills: ["skills"] as const,
  demands: (filters: object) => ["demands", filters] as const,
  demand: (id: number) => ["demands", "detail", id] as const,
  recommendations: (id: number) => ["demands", "recommendations", id] as const,
  employees: (filters: object) => ["employees", filters] as const,
  employee: (id: number) => ["employees", "detail", id] as const,
  employeeAllocations: (id: number) => ["employees", "allocations", id] as const,
  availability: (id: number, q: object) => ["employees", "availability", id, q] as const,
  matrix: (filters: object) => ["employees", "matrix", filters] as const,
  facets: ["employees", "facets"] as const,
  resources: (q: object) => ["resources", q] as const,
  allocations: (filters: object) => ["allocations", filters] as const,
  allocation: (id: number) => ["allocations", "detail", id] as const,
  training: (filters: object) => ["training", filters] as const,
  reports: (name: string, q?: object) => ["reports", name, q ?? {}] as const,
  aiStatus: ["ai", "status"] as const,
  aiRuns: (demandId?: number) => ["ai", "runs", demandId ?? null] as const,
};

export const useManagedAccounts = (enabled = true) => useQuery({ queryKey: keys.accounts, queryFn: () => http.get<ManagedAccount[]>("/api/auth/accounts"), enabled });

export function useSetManagerAccess() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ username, enabled }: { username: string; enabled: boolean }) => http.put<ManagedAccount>(`/api/auth/accounts/${encodeURIComponent(username)}/manager-access`, { enabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.accounts }),
  });
}

export const usePendingRegistrations = (enabled = true) => useQuery({
  queryKey: ["account-registrations", "pending"],
  queryFn: () => http.get<AccountRegistration[]>("/api/auth/registrations"),
  enabled,
});

export function useReviewRegistration() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ username, decision }: { username: string; decision: "approve" | "reject" }) =>
      http.put<AccountRegistration>(`/api/auth/registrations/${encodeURIComponent(username)}/${decision}`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["account-registrations"] }),
  });
}

// ------------------------------------------------------------------ skills
export const useSkills = () => useQuery({ queryKey: keys.skills, queryFn: () => http.get<Skill[]>("/api/skills"), staleTime: 300_000 });

export function useCreateSkill() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; category: string; description?: string }) => http.post<Skill>("/api/skills", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.skills }),
  });
}

// ------------------------------------------------------------------ demands
export interface DemandFilters {
  status?: DemandStatus[];
  priority?: DemandPriority;
  skillId?: number;
  q?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const useDemands = (filters: DemandFilters, enabled = true) =>
  useQuery({
    queryKey: keys.demands(filters),
    queryFn: ({ signal }) => http.get<Page<Demand>>("/api/demands", { ...filters }, signal),
    placeholderData: keepPreviousData,
    enabled,
  });

export const useDemand = (id: number) =>
  useQuery({ queryKey: keys.demand(id), queryFn: () => http.get<Demand>(`/api/demands/${id}`), enabled: Number.isFinite(id) });

export const useRecommendations = (id: number) =>
  useQuery({
    queryKey: keys.recommendations(id),
    queryFn: () => http.post<MatchResult>(`/api/demands/${id}/recommendations`),
    enabled: Number.isFinite(id),
  });

export interface CreateDemandBody {
  projectName: string;
  role: string;
  description?: string;
  startDate: string;
  endDate: string;
  location?: string;
  remoteAllowed: boolean;
  allocationPercent: number;
  headcount: number;
  priority: DemandPriority;
  requiredSkills: { skillId: number; minimumProficiency: number; mandatory: boolean }[];
}

export function useCreateDemand() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateDemandBody) => http.post<Demand>("/api/demands", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["demands"] });
      qc.invalidateQueries({ queryKey: ["reports"] });
    },
  });
}

export function useCancelDemand() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason?: string }) => http.put<Demand>(`/api/demands/${id}/cancel`, { reason }),
    onSuccess: () => invalidateStaffing(qc),
  });
}

// ------------------------------------------------------------------ employees
export interface EmployeeFilters {
  q?: string;
  department?: string;
  location?: string;
  status?: EmploymentStatus[];
  skillId?: number;
  minLevel?: number;
  page?: number;
  size?: number;
}

export const useEmployees = (filters: EmployeeFilters) =>
  useQuery({
    queryKey: keys.employees(filters),
    queryFn: ({ signal }) => http.get<Page<Employee>>("/api/employees", { ...filters }, signal),
    placeholderData: keepPreviousData,
  });

export const useEmployee = (id: number) =>
  useQuery({ queryKey: keys.employee(id), queryFn: () => http.get<Employee>(`/api/employees/${id}`), enabled: Number.isFinite(id) });

export const useEmployeeAllocations = (id: number) =>
  useQuery({ queryKey: keys.employeeAllocations(id), queryFn: () => http.get<Allocation[]>(`/api/employees/${id}/allocations`) });

export const useFacets = () =>
  useQuery({
    queryKey: keys.facets,
    queryFn: () => http.get<{ departments: string[]; locations: string[] }>("/api/employees/facets"),
    staleTime: 300_000,
  });

export const useSkillsMatrix = (filters: { q?: string; department?: string; category?: string }) =>
  useQuery({
    queryKey: keys.matrix(filters),
    queryFn: ({ signal }) => http.get<SkillsMatrix>("/api/employees/skills-matrix", filters, signal),
    placeholderData: keepPreviousData,
  });

export const useAvailability = (id: number, q: { startDate: string; endDate: string; allocationPercent: number }, enabled = true) =>
  useQuery({
    queryKey: keys.availability(id, q),
    queryFn: () => http.get<Availability>(`/api/employees/${id}/availability`, q),
    enabled: enabled && Boolean(q.startDate && q.endDate) && q.endDate >= q.startDate,
  });

export interface CreateEmployeeBody {
  employeeCode: string;
  name: string;
  email: string;
  jobTitle?: string;
  department: string;
  location?: string;
  remoteEligible: boolean;
  employmentStatus: EmploymentStatus;
  capacityPercent: number;
}

export function useCreateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateEmployeeBody) => http.post<Employee>("/api/employees", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["employees"] }),
  });
}

export function useUpdateEmployeeStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, employmentStatus }: { id: number; employmentStatus: EmploymentStatus }) =>
      http.patch<Employee>(`/api/employees/${id}/status`, { employmentStatus }),
    onSuccess: () => invalidateStaffing(qc),
  });
}

export interface UpsertSkillBody {
  skillId: number;
  proficiencyLevel: number;
  evidence?: string;
  evidenceSource: EvidenceSource;
  lastValidatedAt?: string | null;
}

export function useUpsertEmployeeSkill(employeeId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpsertSkillBody) => http.post<EmployeeSkill>(`/api/employees/${employeeId}/skills`, body),
    onSuccess: () => invalidateStaffing(qc),
  });
}

export function useRemoveEmployeeSkill(employeeId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (skillId: number) => http.delete<void>(`/api/employees/${employeeId}/skills/${skillId}`),
    onSuccess: () => invalidateStaffing(qc),
  });
}

// ------------------------------------------------------------------ resource search
export interface ResourceQuery {
  startDate: string;
  endDate: string;
  skillIds?: number[];
  minProficiency?: number;
  location?: string;
  department?: string;
  allocationPercent: number;
  includeUnavailable?: boolean;
}

export const useResourceSearch = (q: ResourceQuery, enabled: boolean) =>
  useQuery({
    queryKey: keys.resources(q),
    queryFn: ({ signal }) => http.get<AvailableResource[]>("/api/resources/available", { ...q }, signal),
    enabled,
    placeholderData: keepPreviousData,
  });

// ------------------------------------------------------------------ allocations
export const useAllocations = (
  filters: { status?: AllocationStatus[]; employeeId?: number; demandId?: number; page?: number; size?: number },
  enabled = true,
) =>
  useQuery({
    queryKey: keys.allocations(filters),
    queryFn: ({ signal }) => http.get<Page<Allocation>>("/api/allocations", { ...filters }, signal),
    placeholderData: keepPreviousData,
    enabled,
  });

export const useAllocation = (id: number | null) =>
  useQuery({ queryKey: keys.allocation(id ?? -1), queryFn: () => http.get<Allocation>(`/api/allocations/${id}`), enabled: id != null });

function invalidateStaffing(qc: ReturnType<typeof useQueryClient>) {
  for (const prefix of ["allocations", "demands", "employees", "resources", "reports", "training"]) {
    qc.invalidateQueries({ queryKey: [prefix] });
  }
}

export function useProposeAllocation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { employeeId: number; demandId: number; startDate?: string; endDate?: string; allocationPercent?: number; note?: string }) =>
      http.post<Allocation>("/api/allocations", body, { "Idempotency-Key": crypto.randomUUID() }),
    onSuccess: () => invalidateStaffing(qc),
  });
}

export function useAllocationDecision() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action, note }: { id: number; action: "approve" | "reject"; note?: string }) =>
      http.put<Allocation>(`/api/allocations/${id}/${action}`, { note }),
    onSuccess: () => invalidateStaffing(qc),
  });
}

export function useCancelAllocation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => http.put<Allocation>(`/api/allocations/${id}/cancel`, { reason }),
    onSuccess: () => invalidateStaffing(qc),
  });
}

// ------------------------------------------------------------------ training
export const useTraining = (filters: { employeeId?: number; status?: TrainingStatus[]; category?: string }) =>
  useQuery({
    queryKey: keys.training(filters),
    queryFn: () => http.get<TrainingRecommendation[]>("/api/training-recommendations", filters),
  });

export function useUpdateTraining() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: TrainingStatus }) =>
      http.patch<TrainingRecommendation>(`/api/training-recommendations/${id}/status`, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["training"] });
      qc.invalidateQueries({ queryKey: ["reports"] });
    },
  });
}

// ------------------------------------------------------------------ reports
export const useDashboard = () =>
  useQuery({ queryKey: keys.reports("dashboard"), queryFn: () => http.get<DashboardSummary>("/api/reports/dashboard") });
export const useUtilization = (q: { from?: string; to?: string }) =>
  useQuery({ queryKey: keys.reports("utilization", q), queryFn: () => http.get<UtilizationReport>("/api/reports/utilization", q) });
export const useOpenDemandReport = () =>
  useQuery({ queryKey: keys.reports("open-demands"), queryFn: () => http.get<OpenDemandRow[]>("/api/reports/open-demands") });
export const useSkillProficiencyReport = () =>
  useQuery({ queryKey: keys.reports("skill-proficiency"), queryFn: () => http.get<SkillProficiencyRow[]>("/api/reports/skill-proficiency") });
export const useAvailableResourcesReport = (q: { from?: string; to?: string; allocationPercent: number }) =>
  useQuery({
    queryKey: keys.reports("available-resources", q),
    queryFn: () => http.get<AvailableResourceRow[]>("/api/reports/available-resources", q),
  });
export const useUnstaffableReport = () =>
  useQuery({ queryKey: keys.reports("unstaffable"), queryFn: () => http.get<UnstaffableDemandRow[]>("/api/reports/unstaffable-demands") });
export const useSkillGapReport = () =>
  useQuery({ queryKey: keys.reports("skill-gaps"), queryFn: () => http.get<SkillGapReport>("/api/reports/skill-gaps") });

// ------------------------------------------------------------------ AI agent
export const useAiStatus = () =>
  useQuery({ queryKey: keys.aiStatus, queryFn: () => http.get<AiStatus>("/api/ai/status"), refetchInterval: 30_000 });

export const useAiRuns = (demandId?: number) =>
  useQuery({ queryKey: keys.aiRuns(demandId), queryFn: () => http.get<AgentRunSummary[]>("/api/ai/runs", { demandId, limit: 10 }) });

export function useStaffingAdvice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { demandId: number; question?: string; topN?: number; proposeEmployeeId?: number; recordTrainingRecommendations?: boolean }) =>
      http.post<StaffingAdvice>("/api/ai/staffing-advice", body),
    onSuccess: (advice) => {
      qc.invalidateQueries({ queryKey: ["ai"] });
      if (advice.proposal?.submitted || advice.recordedTrainingRecommendations.length) invalidateStaffing(qc);
    },
  });
}
