package com.talent.server.dto.response;

import com.talent.server.enums.AgentRunStatus;
import com.talent.server.enums.AiMode;
import com.talent.server.enums.NarrativeSource;
import com.talent.server.service.LearningPathService.LearningPath;
import com.talent.server.service.matching.AvailabilityProfile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of one staffing-agent run. Scores, eligibility, availability and gaps
 * come from the
 * deterministic backend; only {@code summary}, {@code answer} and the
 * per-candidate narrative text
 * come from the narrator (LLM or labelled simulation), and only after passing
 * grounding validation.
 *
 * @param simulationNotice non-null whenever the narrative was not produced by
 *                         the live LLM
 */
public record StaffingAdviceResponse(
        Long runId,
        String goal,
        AiMode mode,
        String model,
        String simulationNotice,
        AgentRunStatus status,
        NarrativeSource narrativeSource,
        DemandResponse demand,
        String question,
        String summary,
        String answer,
        List<Recommendation> recommendations,
        List<LearningPath> learningPaths,
        List<DataGap> dataGaps,
        List<ReferencedEmployee> referencedEmployees,
        ExclusionSummary exclusions,
        Validation validation,
        List<ToolCallTrace> toolCalls,
        List<TrainingRecommendationResponse> recordedTrainingRecommendations,
        ProposalOutcome proposal,
        List<String> guardrails,
        long durationMs,
        Instant generatedAt) {

    /** A ranked candidate: backend evidence plus validated narrative. */
    public record Recommendation(int rank, CandidateResponse candidate, String explanation, List<String> strengths,
            List<String> considerations) {
    }

    /** Something the agent could not state because it is not on record. */
    public record DataGap(Long employeeId, String employeeName, String subject, String detail) {
    }

    /**
     * An employee named in the question, with exactly what is stored about them.
     */
    public record ReferencedEmployee(Long employeeId, String employeeCode, String name, boolean inRanking,
            Integer rank, List<EmployeeSkillResponse> storedSkills,
            AvailabilityProfile availability, List<String> notes) {
    }

    public record ExclusionSummary(int evaluated, int ranked, int excluded, int notConsidered,
            Map<String, Long> reasonCounts) {
    }

    /**
     * @param passed         the returned narrative passed every check
     * @param repairedFields fields whose model text was replaced by deterministic
     *                       text
     */
    public record Validation(boolean passed, int llmAttempts, List<ValidationIssue> issues, List<String> repairedFields,
            String note) {
    }

    /** One rejected statement: the attempt, the field it was in, and why. */
    public record ValidationIssue(int attempt, String field, String code, String message, Long employeeId) {
    }

    public record ToolCallTrace(int sequence, String tool, String purpose, Map<String, Object> arguments,
            String summary, boolean success, long durationMs) {
    }

    public record ProposalOutcome(boolean submitted, AllocationResponse allocation, String errorCode, String message) {
    }
}
