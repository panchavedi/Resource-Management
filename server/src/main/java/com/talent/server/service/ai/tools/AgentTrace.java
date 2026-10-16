package com.talent.server.service.ai.tools;

import com.talent.server.dto.response.StaffingAdviceResponse.ToolCallTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes tools for one agent run and records every call (arguments, summary,
 * outcome, duration).
 * Only tools on the run's allow-list can be executed.
 */
public final class AgentTrace {

    private final Set<String> allowedTools;
    private final List<ToolCallTrace> calls = new ArrayList<>();

    public AgentTrace(Set<String> allowedTools) {
        this.allowedTools = Set.copyOf(allowedTools);
    }

    public <I, O> O call(AgentTool<I, O> tool, I input) {
        if (!allowedTools.contains(tool.name())) {
            throw new IllegalStateException("Tool " + tool.name() + " is not allowed in this run");
        }
        long start = System.nanoTime();
        try {
            O output = tool.execute(input);
            record(tool.name(), tool.purpose(), tool.auditArguments(input), tool.summarize(output), true, start);
            return output;
        } catch (RuntimeException e) {
            record(tool.name(), tool.purpose(), tool.auditArguments(input), "Failed: " + e.getMessage(), false, start);
            throw e;
        }
    }

    /** Records a step that is not a tool invocation (LLM narration, validation). */
    public void recordStep(String name, String purpose, Map<String, Object> arguments, String summary,
            boolean success, long startNanos) {
        record(name, purpose, arguments, summary, success, startNanos);
    }

    private void record(String name, String purpose, Map<String, Object> arguments, String summary, boolean success,
            long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        calls.add(new ToolCallTrace(calls.size() + 1, name, purpose, arguments, summary, success, durationMs));
    }

    public List<ToolCallTrace> calls() {
        return Collections.unmodifiableList(calls);
    }
}
