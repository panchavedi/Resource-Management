package com.talent.server.service.ai.narration;

import java.util.List;

/** Turns gathered evidence into explanatory text. Implementations must not add facts. */
public interface StaffingNarrator {

    /**
     * @param correctionFeedback validation issues from a previous attempt (empty on the first attempt)
     */
    NarrationResult narrate(EvidencePack evidence, List<String> correctionFeedback);
}
