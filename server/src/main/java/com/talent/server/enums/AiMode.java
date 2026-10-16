package com.talent.server.enums;

/**
 * How the staffing agent produces narrative text.
 * <ul>
 *   <li>LIVE – always call the configured LLM; fail if it is unreachable.</li>
 *   <li>SIMULATED – never call an LLM; a deterministic, clearly labelled narrator is used.</li>
 *   <li>AUTO – use the LLM when reachable, otherwise fall back to SIMULATED (labelled).</li>
 * </ul>
 */
public enum AiMode {
    LIVE,
    SIMULATED,
    AUTO
}
