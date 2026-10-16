package com.talent.server.enums;

/**
 * Which component produced the narrative text returned to the user.
 * <ul>
 *   <li>LLM – every statement from the model passed fact-checking.</li>
 *   <li>LLM_WITH_REPAIRS – model text that passed was kept; failing parts were replaced deterministically.</li>
 *   <li>SIMULATED – deterministic template because the agent runs in (or fell back to) simulation mode.</li>
 *   <li>DETERMINISTIC_FALLBACK – the model produced nothing usable; the deterministic template is shown.</li>
 * </ul>
 */
public enum NarrativeSource {
    LLM,
    LLM_WITH_REPAIRS,
    SIMULATED,
    DETERMINISTIC_FALLBACK
}
