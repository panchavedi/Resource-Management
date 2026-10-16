// TypeScript mirrors of the backend DTOs (server/src/main/java/.../dto). Keep in sync with the API.

export type Role = "RESOURCE_MANAGER" | "APPROVER" | "ADMIN";
export type EmploymentStatus = "ACTIVE" | "BENCH" | "ON_LEAVE" | "INACTIVE";
export type DemandPriority = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
export type DemandStatus = "OPEN" | "PARTIALLY_FILLED" | "FILLED" | "CANCELLED";
export type AllocationStatus = "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "CANCELLED";
export type AvailabilityStatus = "FULL" | "PARTIAL" | "NONE";
export type EvidenceStatus = "VALIDATED" | "STALE" | "UNVALIDATED" | "MISSING";
export type EvidenceSource = "CERTIFICATION" | "ASSESSMENT" | "PROJECT_DELIVERY" | "MANAGER_REVIEW" | "SELF_DECLARED";
export type LocationFit =
  | "ANY_LOCATION"
  | "SAME_LOCATION"
  | "REMOTE"
  | "DIFFERENT_LOCATION_REMOTE_ALLOWED"
  | "DIFFERENT_LOCATION"
  | "UNKNOWN";
export type TrainingStatus = "RECOMMENDED" | "IN_PROGRESS" | "COMPLETED" | "DISMISSED";
export type AiMode = "LIVE" | "SIMULATED" | "AUTO";
export type NarrativeSource = "LLM" | "LLM_WITH_REPAIRS" | "SIMULATED" | "DETERMINISTIC_FALLBACK";
export type AgentRunStatus = "RUNNING" | "COMPLETED" | "COMPLETED_WITH_FALLBACK" | "FAILED";

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserInfo {
  username: string;
  displayName: string;
  roles: Role[];
}

export interface ManagedAccount {
  username: string;
  displayName: string;
  roles: Role[];
  managerAccess: boolean;
  managerAccessFixed: boolean;
}

export interface AccountRegistration {
  username: string;
  displayName: string;
  status: "PENDING_APPROVAL" | "ACTIVE" | "REJECTED";
  requestedAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: UserInfo;
}

export interface Skill {
  id: number;
  name: string;
  category: string;
  description: string | null;
}

export interface EmployeeSkill {
  id: number;
  skillId: number;
  skillName: string;
  category: string;
  proficiencyLevel: number;
  evidence: string | null;
  evidenceSource: EvidenceSource;
  lastValidatedAt: string | null;
  validatedBy: string | null;
  evidenceStatus: EvidenceStatus;
}

export interface Employee {
  id: number;
  employeeCode: string;
  name: string;
  email: string;
  jobTitle: string | null;
  department: string;
  location: string | null;
  remoteEligible: boolean;
  employmentStatus: EmploymentStatus;
  capacityPercent: number;
  skills: EmployeeSkill[];
  createdAt: string;
  updatedAt: string;
}

export interface RequiredSkill {
  skillId: number;
  skillName: string;
  category: string;
  minimumProficiency: number;
  mandatory: boolean;
}

export interface Demand {
  id: number;
  projectName: string;
  role: string;
  description: string | null;
  startDate: string;
  endDate: string;
  durationDays: number;
  location: string | null;
  remoteAllowed: boolean;
  allocationPercent: number;
  headcount: number;
  priority: DemandPriority;
  status: DemandStatus;
  approvedCount: number;
  pendingCount: number;
  requiredSkills: RequiredSkill[];
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface Booking {
  allocationId: number;
  demandId: number;
  projectName: string;
  startDate: string;
  endDate: string;
  allocationPercent: number;
  status: AllocationStatus;
}

export interface AvailabilityWindow {
  startDate: string;
  endDate: string;
  days: number;
  loadPercent: number;
  confirmedLoadPercent: number;
  freePercent: number;
  meetsRequest: boolean;
}

export interface Availability {
  startDate: string;
  endDate: string;
  capacityPercent: number;
  requestedPercent: number;
  status: AvailabilityStatus;
  peakLoadPercent: number;
  minFreePercent: number;
  totalDays: number;
  availableDays: number;
  coveragePercent: number;
  windows: AvailabilityWindow[];
  bookings: Booking[];
}

export interface ScoreComponent {
  factor: "SKILL_FIT" | "AVAILABILITY" | "LOCATION" | "EVIDENCE_QUALITY";
  label: string;
  weight: number;
  score: number;
  weightedScore: number;
  explanation: string;
}

export interface SkillMatch {
  skillId: number;
  skillName: string;
  category: string;
  requiredLevel: number;
  mandatory: boolean;
  actualLevel: number | null;
  meetsRequirement: boolean;
  points: number;
  explanation: string;
  evidence: string | null;
  evidenceSource: EvidenceSource | null;
  lastValidatedAt: string | null;
  evidenceStatus: EvidenceStatus;
}

export interface SkillGap {
  skillId: number;
  skillName: string;
  category: string;
  mandatory: boolean;
  requiredLevel: number;
  actualLevel: number | null;
  levelsShort: number;
}

export interface LocationAssessment {
  employeeLocation: string | null;
  demandLocation: string | null;
  remoteAllowed: boolean;
  employeeRemoteEligible: boolean;
  fit: LocationFit;
  score: number;
  explanation: string;
}

export interface Candidate {
  employeeId: number;
  employeeCode: string;
  name: string;
  jobTitle: string | null;
  department: string;
  location: string | null;
  employmentStatus: EmploymentStatus;
  rank: number | null;
  eligible: boolean;
  meetsMandatorySkills: boolean;
  totalScore: number;
  scoreBreakdown: ScoreComponent[];
  skillMatches: SkillMatch[];
  gaps: SkillGap[];
  availability: Availability;
  locationFit: LocationAssessment;
  flags: string[];
  dataGaps: string[];
  exclusionReasons: string[];
}

export interface MatchResult {
  demand: Demand;
  evaluatedAt: string;
  criteria: {
    mandatorySkillPolicy: "EXCLUDE" | "FLAG";
    weights: { skill: number; availability: number; location: number; evidence: number };
    evidenceFreshnessMonths: number;
    capacityLimitPercent: number;
    rules: string[];
  };
  candidatePoolSize: number;
  notConsideredCount: number;
  rankedCandidates: Candidate[];
  excludedCandidates: Candidate[];
}

export interface AllocationHistoryEntry {
  eventType: "REQUESTED" | "APPROVED" | "REJECTED" | "CANCELLED";
  fromStatus: AllocationStatus | null;
  toStatus: AllocationStatus;
  actor: string;
  note: string | null;
  occurredAt: string;
}

export interface Allocation {
  id: number;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  demandId: number;
  projectName: string;
  role: string;
  startDate: string;
  endDate: string;
  allocationPercent: number;
  status: AllocationStatus;
  requestedBy: string;
  requestedAt: string;
  requestNote: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  cancelledBy: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  version: number;
  history: AllocationHistoryEntry[] | null;
}

export interface AvailableResource {
  employeeId: number;
  employeeCode: string;
  name: string;
  jobTitle: string | null;
  department: string;
  location: string | null;
  remoteEligible: boolean;
  employmentStatus: EmploymentStatus;
  capacityPercent: number;
  availability: Availability;
  matchedSkills: EmployeeSkill[];
  matchedSkillAverage: number | null;
}

export interface SkillsMatrix {
  skills: Skill[];
  rows: {
    employeeId: number;
    employeeCode: string;
    name: string;
    jobTitle: string | null;
    department: string;
    location: string | null;
    employmentStatus: EmploymentStatus;
    cells: Record<string, { level: number; evidenceStatus: EvidenceStatus; lastValidatedAt: string | null }>;
  }[];
}

export interface LearningResource {
  id: number;
  skillId: number;
  skillName: string;
  title: string;
  provider: string;
  format: "COURSE" | "CERTIFICATION" | "LAB" | "MENTORING" | "PROJECT";
  targetLevel: number;
  durationHours: number;
  url: string | null;
}

export interface TrainingRecommendation {
  id: number;
  employeeId: number;
  employeeName: string;
  skillId: number;
  skillName: string;
  skillCategory: string;
  demandId: number | null;
  projectName: string | null;
  currentLevel: number | null;
  targetLevel: number;
  reason: string;
  learningResource: LearningResource | null;
  source: "AI_AGENT" | "MANUAL";
  status: TrainingStatus;
  createdBy: string;
  createdAt: string;
}

export interface LearningPath {
  employeeId: number;
  employeeName: string;
  skillId: number;
  skillName: string;
  category: string;
  mandatory: boolean;
  currentLevel: number | null;
  targetLevel: number;
  steps: LearningResource[];
  totalHours: number;
  catalogNote: string | null;
}

export interface ToolCallTrace {
  sequence: number;
  tool: string;
  purpose: string;
  arguments: Record<string, unknown>;
  summary: string;
  success: boolean;
  durationMs: number;
}

export interface ValidationIssue {
  attempt: number;
  field: string;
  code: string;
  message: string;
  employeeId: number | null;
}

export interface StaffingAdvice {
  runId: number;
  goal: string;
  mode: AiMode;
  model: string;
  simulationNotice: string | null;
  status: AgentRunStatus;
  narrativeSource: NarrativeSource;
  demand: Demand;
  question: string | null;
  summary: string;
  answer: string | null;
  recommendations: {
    rank: number;
    candidate: Candidate;
    explanation: string;
    strengths: string[];
    considerations: string[];
  }[];
  learningPaths: LearningPath[];
  dataGaps: { employeeId: number | null; employeeName: string | null; subject: string; detail: string }[];
  referencedEmployees: {
    employeeId: number;
    employeeCode: string;
    name: string;
    inRanking: boolean;
    rank: number | null;
    storedSkills: EmployeeSkill[];
    availability: Availability | null;
    notes: string[];
  }[];
  exclusions: { evaluated: number; ranked: number; excluded: number; notConsidered: number; reasonCounts: Record<string, number> };
  validation: { passed: boolean; llmAttempts: number; issues: ValidationIssue[]; repairedFields: string[]; note: string };
  toolCalls: ToolCallTrace[];
  recordedTrainingRecommendations: TrainingRecommendation[];
  proposal: { submitted: boolean; allocation: Allocation | null; errorCode: string | null; message: string } | null;
  guardrails: string[];
  durationMs: number;
  generatedAt: string;
}

export interface AiStatus {
  configuredMode: AiMode;
  effectiveMode: AiMode;
  model: string;
  baseUrl: string;
  reachable: boolean;
  modelInstalled: boolean;
  detail: string;
  checkedAt: string;
}

export interface AgentRunSummary {
  id: number;
  demandId: number | null;
  goal: string;
  question: string | null;
  mode: AiMode;
  model: string | null;
  status: AgentRunStatus;
  narrativeSource: NarrativeSource | null;
  validationPassed: boolean;
  llmAttempts: number;
  durationMs: number;
  requestedBy: string;
  createdAt: string;
  errorMessage: string | null;
}

// ---------------------------------------------------------------- reports
export interface DashboardSummary {
  openDemands: number;
  criticalOpenDemands: number;
  openPositions: number;
  pendingApprovals: number;
  activeEmployees: number;
  benchEmployees: number;
  averageUtilizationNext30Days: number;
  unstaffableDemands: number;
  openTrainingRecommendations: number;
  startingSoon: { demandId: number; projectName: string; role: string; priority: DemandPriority; startDate: string; openPositions: number }[];
}

export interface OpenDemandRow {
  role: string;
  priority: DemandPriority;
  demandCount: number;
  totalHeadcount: number;
  approvedCount: number;
  openPositions: number;
  earliestStart: string;
}

export interface SkillProficiencyRow {
  category: string;
  skill: string;
  proficiencyLevel: number;
  employeeCount: number;
  validatedCount: number;
  employees: string;
}

export interface AvailableResourceRow {
  employeeId: number;
  employeeCode: string;
  name: string;
  department: string;
  location: string | null;
  capacityPercent: number;
  peakLoadPercent: number;
  minFreePercent: number;
  availableDays: number;
  totalDays: number;
}

export interface UtilizationReport {
  from: string;
  to: string;
  byEmployee: {
    employeeId: number;
    employeeCode: string;
    name: string;
    department: string;
    capacityPercent: number;
    utilizationPercent: number;
    tentativePercent: number;
  }[];
  byDepartment: { department: string; employeeCount: number; utilizationPercent: number | null; tentativePercent: number | null }[];
}

export interface UnstaffableDemandRow {
  demandId: number;
  projectName: string;
  role: string;
  priority: DemandPriority;
  startDate: string;
  endDate: string;
  skillQualifiedCount: number;
  reason: string;
}

export interface SkillGapReport {
  byCategory: {
    category: string;
    requirementCount: number;
    uncoveredRequirements: number;
    peopleShort: number;
    upskillCandidates: number;
    skillsShort: string | null;
    openTrainingRecommendations: number;
  }[];
  bySkill: {
    category: string;
    skill: string;
    openDemands: number;
    demandedHeadcount: number;
    qualifiedSupply: number;
    nearMissSupply: number;
    peopleShort: number;
  }[];
}

/** RFC 9457 problem document returned by every failing API call. */
export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
  details?: Record<string, unknown>;
}
