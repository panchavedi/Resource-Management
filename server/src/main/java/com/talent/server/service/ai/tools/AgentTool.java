package com.talent.server.service.ai.tools;

import java.util.Map;

/**
 * A controlled capability the staffing agent may invoke. Tools wrap existing, rule-enforcing services:
 * the agent can read evidence and submit proposals, but every business rule is still applied by Java code.
 *
 * @param <I> typed, validated input
 * @param <O> output used as evidence
 */
public interface AgentTool<I, O> {

    /** Stable identifier recorded in the audit trail. */
    String name();

    /** Why the agent calls this tool, shown in the UI timeline. */
    String purpose();

    O execute(I input);

    /** One-line, data-derived summary of the result for the audit trail. */
    String summarize(O output);

    /** Arguments as recorded in the audit trail; override to keep large inputs compact. */
    default Map<String, Object> auditArguments(I input) {
        return Map.of("input", String.valueOf(input));
    }

    /** Whether the tool changes state (state-changing tools are only run when the goal explicitly asks). */
    default boolean mutating() {
        return false;
    }
}
