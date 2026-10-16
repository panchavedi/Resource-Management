package com.talent.server.service.ai.tools;

import com.talent.server.dto.request.CreateAllocationRequest;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.dto.response.EmployeeResponse;
import com.talent.server.dto.response.MatchResultResponse;
import com.talent.server.dto.response.TrainingRecommendationResponse;
import com.talent.server.enums.EvidenceStatus;
import com.talent.server.service.AllocationService;
import com.talent.server.service.AllocationService.ProposalResult;
import com.talent.server.service.CandidateMatchingService;
import com.talent.server.service.DemandService;
import com.talent.server.service.EmployeeService;
import com.talent.server.service.LearningPathService;
import com.talent.server.service.LearningPathService.LearningPath;
import com.talent.server.service.LearningPathService.SkillGapRef;
import com.talent.server.service.TrainingRecommendationService;
import com.talent.server.service.ai.QuestionAnalyzer;
import com.talent.server.service.ai.QuestionAnalyzer.QuestionEntities;
import com.talent.server.service.matching.AvailabilityProfile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The agent's tool belt. Each tool is a thin, typed adapter over a service that
 * already enforces the
 * business rules, so the agent cannot bypass eligibility, capacity or approval
 * checks.
 */
@Component
public class StaffingTools {

    public final GetDemand getDemand;
    public final RankCandidates rankCandidates;
    public final GetEmployeeEvidence getEmployeeEvidence;
    public final CheckAvailability checkAvailability;
    public final BuildLearningPaths buildLearningPaths;
    public final RecordTrainingRecommendations recordTraining;
    public final ProposeAllocation proposeAllocation;
    public final ResolveQuestionEntities resolveQuestion;

    public StaffingTools(DemandService demandService, CandidateMatchingService matchingService,
            EmployeeService employeeService, LearningPathService learningPathService,
            TrainingRecommendationService trainingService, AllocationService allocationService,
            QuestionAnalyzer questionAnalyzer) {
        this.getDemand = new GetDemand(demandService);
        this.rankCandidates = new RankCandidates(matchingService);
        this.getEmployeeEvidence = new GetEmployeeEvidence(employeeService);
        this.checkAvailability = new CheckAvailability(employeeService);
        this.buildLearningPaths = new BuildLearningPaths(learningPathService);
        this.recordTraining = new RecordTrainingRecommendations(trainingService);
        this.proposeAllocation = new ProposeAllocation(allocationService);
        this.resolveQuestion = new ResolveQuestionEntities(questionAnalyzer);
    }

    // ------------------------------------------------------------------ inputs

    public record DemandRef(Long demandId) {
    }

    public record EmployeeRef(Long employeeId) {
    }

    public record AvailabilityQuery(Long employeeId, LocalDate startDate, LocalDate endDate, int allocationPercent) {
    }

    public record GapQuery(List<SkillGapRef> gaps) {
    }

    public record TrainingCommand(Long demandId, List<LearningPath> paths, String actor) {
    }

    public record ProposalCommand(Long demandId, Long employeeId, String note, String actor) {
    }

    public record QuestionRef(String question) {
    }

    // ------------------------------------------------------------------ read-only
    // tools

    public static final class GetDemand implements AgentTool<DemandRef, DemandResponse> {
        private final DemandService service;

        GetDemand(DemandService service) {
            this.service = service;
        }

        public String name() {
            return "get_demand";
        }

        public String purpose() {
            return "Load the demand: role, period, allocation and skill requirements";
        }

        public DemandResponse execute(DemandRef input) {
            return service.get(input.demandId());
        }

        public String summarize(DemandResponse d) {
            return "%s / %s, %d%% from %s to %s, %d required skill(s), status %s".formatted(d.projectName(), d.role(),
                    d.allocationPercent(), d.startDate(), d.endDate(), d.requiredSkills().size(), d.status());
        }

        public Map<String, Object> auditArguments(DemandRef input) {
            return Map.of("demandId", input.demandId());
        }
    }

    public static final class RankCandidates implements AgentTool<DemandRef, MatchResultResponse> {
        private final CandidateMatchingService service;

        RankCandidates(CandidateMatchingService service) {
            this.service = service;
        }

        public String name() {
            return "rank_eligible_candidates";
        }

        public String purpose() {
            return "Apply deterministic eligibility rules and transparent scoring to every candidate";
        }

        public MatchResultResponse execute(DemandRef input) {
            return service.rankCandidates(input.demandId());
        }

        public String summarize(MatchResultResponse r) {
            return "Evaluated %d employee(s): %d ranked, %d excluded, %d without any mandatory skill".formatted(
                    r.candidatePoolSize(), r.rankedCandidates().size(), r.excludedCandidates().size(),
                    r.notConsideredCount());
        }

        public Map<String, Object> auditArguments(DemandRef input) {
            return Map.of("demandId", input.demandId());
        }
    }

    public static final class GetEmployeeEvidence implements AgentTool<EmployeeRef, EmployeeResponse> {
        private final EmployeeService service;

        GetEmployeeEvidence(EmployeeService service) {
            this.service = service;
        }

        public String name() {
            return "get_employee_skill_evidence";
        }

        public String purpose() {
            return "Read every stored proficiency and its validation evidence for an employee";
        }

        public EmployeeResponse execute(EmployeeRef input) {
            return service.get(input.employeeId());
        }

        public String summarize(EmployeeResponse e) {
            long validated = e.skills().stream().filter(s -> s.evidenceStatus() == EvidenceStatus.VALIDATED).count();
            return "%s: %d stored skill(s), %d with fresh validation".formatted(e.name(), e.skills().size(), validated);
        }

        public Map<String, Object> auditArguments(EmployeeRef input) {
            return Map.of("employeeId", input.employeeId());
        }
    }

    public static final class CheckAvailability implements AgentTool<AvailabilityQuery, AvailabilityProfile> {
        private final EmployeeService service;

        CheckAvailability(EmployeeService service) {
            this.service = service;
        }

        public String name() {
            return "check_availability";
        }

        public String purpose() {
            return "Compute day-by-day availability from pending and approved allocations";
        }

        public AvailabilityProfile execute(AvailabilityQuery q) {
            return service.availability(q.employeeId(), q.startDate(), q.endDate(), q.allocationPercent());
        }

        public String summarize(AvailabilityProfile a) {
            return a.describe();
        }

        public Map<String, Object> auditArguments(AvailabilityQuery q) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("employeeId", q.employeeId());
            args.put("startDate", q.startDate().toString());
            args.put("endDate", q.endDate().toString());
            args.put("allocationPercent", q.allocationPercent());
            return args;
        }
    }

    public static final class BuildLearningPaths implements AgentTool<GapQuery, List<LearningPath>> {
        private final LearningPathService service;

        BuildLearningPaths(LearningPathService service) {
            this.service = service;
        }

        public String name() {
            return "build_learning_paths";
        }

        public String purpose() {
            return "Match skill gaps to resources in the learning catalogue";
        }

        public List<LearningPath> execute(GapQuery q) {
            return service.pathsFor(q.gaps());
        }

        public String summarize(List<LearningPath> paths) {
            long uncovered = paths.stream().filter(p -> p.steps().isEmpty()).count();
            return "%d learning path(s) built, %d gap(s) without a catalogue resource".formatted(paths.size(),
                    uncovered);
        }

        public Map<String, Object> auditArguments(GapQuery q) {
            return Map.of("gaps", q.gaps().stream()
                    .map(g -> Map.of("employeeId", g.employeeId(), "skillId", g.skillId(), "requiredLevel",
                            g.requiredLevel()))
                    .toList());
        }
    }

    public static final class ResolveQuestionEntities implements AgentTool<QuestionRef, QuestionEntities> {
        private final QuestionAnalyzer analyzer;

        ResolveQuestionEntities(QuestionAnalyzer analyzer) {
            this.analyzer = analyzer;
        }

        public String name() {
            return "resolve_question_entities";
        }

        public String purpose() {
            return "Identify employees and skills named in the question and detect requests to assume data";
        }

        public QuestionEntities execute(QuestionRef input) {
            return analyzer.analyze(input.question());
        }

        public String summarize(QuestionEntities e) {
            return "%d employee(s) and %d skill(s) referenced%s".formatted(e.employees().size(), e.skills().size(),
                    e.fabricationRequested() ? "; question asks to assume or invent data" : "");
        }

        public Map<String, Object> auditArguments(QuestionRef input) {
            return Map.of("question", input.question());
        }
    }

    // ------------------------------------------------------------------
    // state-changing tools

    public static final class RecordTrainingRecommendations
            implements AgentTool<TrainingCommand, List<TrainingRecommendationResponse>> {
        private final TrainingRecommendationService service;

        RecordTrainingRecommendations(TrainingRecommendationService service) {
            this.service = service;
        }

        public String name() {
            return "record_training_recommendations";
        }

        public String purpose() {
            return "Persist learning recommendations for the explained candidates (deduplicated)";
        }

        public List<TrainingRecommendationResponse> execute(TrainingCommand c) {
            return service.recordFromAgent(c.paths(), c.demandId(), c.actor());
        }

        public String summarize(List<TrainingRecommendationResponse> saved) {
            return "%d open recommendation(s) on record for these gaps".formatted(saved.size());
        }

        public Map<String, Object> auditArguments(TrainingCommand c) {
            return Map.of("demandId", c.demandId(), "paths", c.paths().size());
        }

        public boolean mutating() {
            return true;
        }
    }

    public static final class ProposeAllocation implements AgentTool<ProposalCommand, ProposalResult> {
        private final AllocationService service;

        ProposeAllocation(AllocationService service) {
            this.service = service;
        }

        public String name() {
            return "propose_allocation";
        }

        public String purpose() {
            return "Submit a PENDING_APPROVAL allocation; eligibility and capacity are re-validated, a human must approve";
        }

        public ProposalResult execute(ProposalCommand c) {
            return service.propose(
                    new CreateAllocationRequest(c.employeeId(), c.demandId(), null, null, null, c.note()),
                    null, c.actor());
        }

        public String summarize(ProposalResult r) {
            return "Allocation #%d created with status %s".formatted(r.allocation().id(), r.allocation().status());
        }

        public Map<String, Object> auditArguments(ProposalCommand c) {
            return Map.of("demandId", c.demandId(), "employeeId", c.employeeId());
        }

        public boolean mutating() {
            return true;
        }
    }

    /** Names of all tools, used as the default allow-list. */
    public List<String> names() {
        return List.of(getDemand.name(), rankCandidates.name(), getEmployeeEvidence.name(), checkAvailability.name(),
                buildLearningPaths.name(), recordTraining.name(), proposeAllocation.name(), resolveQuestion.name());
    }
}
