package com.talent.server.service.ai.narration;

import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.dto.response.EmployeeSkillResponse;
import com.talent.server.dto.response.StaffingAdviceResponse.DataGap;
import com.talent.server.dto.response.StaffingAdviceResponse.ExclusionSummary;
import com.talent.server.dto.response.StaffingAdviceResponse.ReferencedEmployee;
import com.talent.server.service.LearningPathService.LearningPath;

import java.util.List;

/**
 * Everything the narrator is allowed to know, gathered through tool calls.
 * Anything not in here
 * must not appear in the narrative; the validator enforces that.
 *
 * @param fabricationRequested the question asked to assume, invent or guess
 *                             data
 * @param mentionedSkills      catalogue skills named in the question
 */
public record EvidencePack(
        DemandResponse demand,
        String question,
        boolean fabricationRequested,
        List<CandidateEvidence> candidates,
        List<ReferencedEmployee> referencedEmployees,
        List<LearningPath> learningPaths,
        List<DataGap> dataGaps,
        ExclusionSummary exclusions,
        List<String> mentionedSkills) {

    /**
     * A ranked candidate plus every skill stored for them (not only the required
     * ones).
     */
    public record CandidateEvidence(CandidateResponse candidate, List<EmployeeSkillResponse> storedSkills) {
    }

    public boolean hasQuestion() {
        return question != null && !question.isBlank();
    }
}
