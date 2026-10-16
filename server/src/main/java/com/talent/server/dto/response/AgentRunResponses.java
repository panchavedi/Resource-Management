package com.talent.server.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.talent.server.entity.AgentRun;
import com.talent.server.entity.AgentToolCall;
import com.talent.server.enums.AgentRunStatus;
import com.talent.server.enums.AiMode;
import com.talent.server.enums.NarrativeSource;

import java.time.Instant;
import java.util.List;

/** Read models of the AI audit trail. */
public final class AgentRunResponses {

    private AgentRunResponses() {
    }

    public record RunSummary(Long id, Long demandId, String goal, String question, AiMode mode, String model,
                             AgentRunStatus status, NarrativeSource narrativeSource, boolean validationPassed,
                             int llmAttempts, long durationMs, String requestedBy, Instant createdAt,
                             String errorMessage) {

        public static RunSummary of(AgentRun r) {
            return new RunSummary(r.getId(), r.getDemandId(), r.getGoal(), r.getQuestion(), r.getMode(), r.getModel(),
                    r.getStatus(), r.getNarrativeSource(), r.isValidationPassed(), r.getLlmAttempts(),
                    r.getDurationMs(), r.getRequestedBy(), r.getCreatedAt(), r.getErrorMessage());
        }
    }

    public record ToolCall(int sequence, String tool, @JsonRawValue String arguments, String summary, boolean success,
                           long durationMs) {

        static ToolCall of(AgentToolCall c) {
            return new ToolCall(c.getSequenceNo(), c.getToolName(), c.getArguments(), c.getSummary(), c.isSuccess(),
                    c.getDurationMs());
        }
    }

    /** Full audit record, including the exact response that was returned to the user. */
    public record RunDetail(RunSummary run, List<ToolCall> toolCalls, @JsonRawValue String validationIssues,
                            @JsonRawValue String response) {

        public static RunDetail of(AgentRun r) {
            return new RunDetail(RunSummary.of(r), r.getToolCalls().stream().map(ToolCall::of).toList(),
                    r.getValidationIssues(), r.getResponse());
        }
    }

    public record AiStatus(AiMode configuredMode, AiMode effectiveMode, String model, String baseUrl,
                           boolean reachable, boolean modelInstalled, String detail, Instant checkedAt) {
    }
}
