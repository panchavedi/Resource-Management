package com.talent.server.service.ai;

import com.talent.server.dto.response.AgentRunResponses.RunDetail;
import com.talent.server.dto.response.AgentRunResponses.RunSummary;
import com.talent.server.dto.response.StaffingAdviceResponse.ToolCallTrace;
import com.talent.server.entity.AgentRun;
import com.talent.server.enums.AgentRunStatus;
import com.talent.server.enums.AiMode;
import com.talent.server.enums.NarrativeSource;
import com.talent.server.exception.ApiException;
import com.talent.server.repository.AgentRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Persists agent runs in their own transactions, so a run is recorded even when
 * the agent itself
 * fails, and the long-running LLM call never holds a database transaction open.
 */
@Service
public class AgentAuditService {

    private final AgentRunRepository repository;
    private final JsonMapper jsonMapper;

    public AgentAuditService(AgentRunRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(Long demandId, String requestedBy, String goal, String question, AiMode mode, String model) {
        return repository.save(new AgentRun(demandId, requestedBy, goal, question, mode, model)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long runId, AiMode mode, String model, AgentRunStatus status, NarrativeSource source,
            boolean validationPassed, int llmAttempts, Object validationIssues, Object response,
            List<ToolCallTrace> toolCalls, long durationMs) {
        AgentRun run = repository.findById(runId).orElseThrow(() -> ApiException.notFound("Agent run", runId));
        run.changeMode(mode, model);
        toolCalls.forEach(c -> run.addToolCall(c.sequence(), c.tool(), json(c.arguments()), c.summary(), c.success(),
                c.durationMs()));
        run.complete(status, source, validationPassed, llmAttempts, json(validationIssues), json(response), durationMs);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long runId, List<ToolCallTrace> toolCalls, String error, long durationMs) {
        repository.findById(runId).ifPresent(run -> {
            toolCalls.forEach(c -> run.addToolCall(c.sequence(), c.tool(), json(c.arguments()), c.summary(),
                    c.success(), c.durationMs()));
            run.fail(error, durationMs);
        });
    }

    @Transactional(readOnly = true)
    public List<RunSummary> recent(Long demandId, int limit) {
        return repository.findRecent(demandId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100))).stream()
                .map(RunSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public RunDetail get(Long id) {
        return RunDetail
                .of(repository.findWithToolCallsById(id).orElseThrow(() -> ApiException.notFound("Agent run", id)));
    }

    private String json(Object value) {
        return value == null ? null : jsonMapper.writeValueAsString(value);
    }
}
