package com.talent.server.service.ai;

import com.talent.server.config.AiProperties;
import com.talent.server.dto.request.StaffingAdviceRequest;
import com.talent.server.dto.response.AgentRunResponses.AiStatus;
import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.dto.response.EmployeeResponse;
import com.talent.server.dto.response.EmployeeSkillResponse;
import com.talent.server.dto.response.MatchResultResponse;
import com.talent.server.dto.response.StaffingAdviceResponse;
import com.talent.server.dto.response.StaffingAdviceResponse.DataGap;
import com.talent.server.dto.response.StaffingAdviceResponse.ExclusionSummary;
import com.talent.server.dto.response.StaffingAdviceResponse.ProposalOutcome;
import com.talent.server.dto.response.StaffingAdviceResponse.Recommendation;
import com.talent.server.dto.response.StaffingAdviceResponse.ReferencedEmployee;
import com.talent.server.dto.response.StaffingAdviceResponse.Validation;
import com.talent.server.dto.response.StaffingAdviceResponse.ValidationIssue;
import com.talent.server.dto.response.TrainingRecommendationResponse;
import com.talent.server.enums.AgentRunStatus;
import com.talent.server.enums.AiMode;
import com.talent.server.enums.AvailabilityStatus;
import com.talent.server.enums.NarrativeSource;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.service.LearningPathService;
import com.talent.server.service.LearningPathService.LearningPath;
import com.talent.server.service.ai.QuestionAnalyzer.EmployeeMention;
import com.talent.server.service.ai.QuestionAnalyzer.QuestionEntities;
import com.talent.server.service.ai.narration.EvidencePack;
import com.talent.server.service.ai.narration.EvidencePack.CandidateEvidence;
import com.talent.server.service.ai.narration.LlmStaffingNarrator;
import com.talent.server.service.ai.narration.NarrationResult;
import com.talent.server.service.ai.narration.NarrationResult.CandidateNarrative;
import com.talent.server.service.ai.narration.OllamaClient;
import com.talent.server.service.ai.narration.SimulatedStaffingNarrator;
import com.talent.server.service.ai.tools.AgentTrace;
import com.talent.server.service.ai.tools.StaffingTools;
import com.talent.server.service.ai.tools.StaffingTools.AvailabilityQuery;
import com.talent.server.service.ai.tools.StaffingTools.DemandRef;
import com.talent.server.service.ai.tools.StaffingTools.EmployeeRef;
import com.talent.server.service.ai.tools.StaffingTools.GapQuery;
import com.talent.server.service.ai.tools.StaffingTools.ProposalCommand;
import com.talent.server.service.ai.tools.StaffingTools.QuestionRef;
import com.talent.server.service.ai.tools.StaffingTools.TrainingCommand;
import com.talent.server.service.ai.validation.NarrativeValidator;
import com.talent.server.service.ai.validation.NarrativeValidator.Issue;
import com.talent.server.service.matching.AvailabilityProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Goal-driven staffing agent.
 *
 * <p>
 * <b>Goal:</b> "recommend and explain candidates for demand X" (optionally
 * answering a question,
 * persisting learning recommendations and proposing an allocation for human
 * approval).
 *
 * <p>
 * <b>Plan</b> (each step is a controlled, audited tool call):
 * <ol>
 * <li>{@code get_demand} – load the demand.</li>
 * <li>{@code rank_eligible_candidates} – deterministic eligibility and
 * scoring.</li>
 * <li>{@code get_employee_skill_evidence} – full stored evidence for each
 * candidate explained.</li>
 * <li>{@code resolve_question_entities} – people/skills named in the question
 * (only if asked);
 * then evidence and {@code check_availability} for named people outside the
 * ranking.</li>
 * <li>{@code build_learning_paths} – for gaps of the explained candidates; when
 * nobody is eligible the
 * agent re-plans and builds paths for the closest near-miss candidates
 * instead.</li>
 * <li>{@code llm_narrate} → {@code validate_narrative} – narrative from the LLM
 * (or labelled simulation),
 * fact-checked against the evidence; retried with feedback, then replaced by a
 * deterministic narrative.</li>
 * <li>{@code record_training_recommendations} / {@code propose_allocation} –
 * only when requested;
 * a proposal is PENDING_APPROVAL and is re-validated when a human approves
 * it.</li>
 * </ol>
 * The run, every step and the exact response are persisted for audit.
 *
 * <p>
 * No database transaction is held while the LLM runs; each tool uses its own
 * short transaction.
 */
@Service
public class StaffingAgentService {

    private static final Logger log = LoggerFactory.getLogger(StaffingAgentService.class);

    static final List<String> GUARDRAILS = List.of(
            "Eligibility, scores and availability are computed by deterministic backend rules, never by the language model.",
            "Narrative text is fact-checked against stored records; text that fails is replaced by a deterministic explanation.",
            "Missing skills, proficiency or availability are disclosed as data gaps, never estimated.",
            "The agent cannot approve allocations: proposals are PENDING_APPROVAL and are re-validated when a human approves.",
            "Advisory only: no hiring, promotion or performance decisions; protected characteristics are neither stored nor inferred.");

    private final StaffingTools tools;
    private final LlmStaffingNarrator llmNarrator;
    private final SimulatedStaffingNarrator simulatedNarrator;
    private final NarrativeValidator validator;
    private final QuestionAnalyzer questionAnalyzer;
    private final OllamaClient ollama;
    private final AgentAuditService audit;
    private final AiProperties properties;
    private final Clock clock;

    public StaffingAgentService(StaffingTools tools, LlmStaffingNarrator llmNarrator,
            SimulatedStaffingNarrator simulatedNarrator, NarrativeValidator validator,
            QuestionAnalyzer questionAnalyzer, OllamaClient ollama, AgentAuditService audit,
            AiProperties properties, Clock clock) {
        this.tools = tools;
        this.llmNarrator = llmNarrator;
        this.simulatedNarrator = simulatedNarrator;
        this.validator = validator;
        this.questionAnalyzer = questionAnalyzer;
        this.ollama = ollama;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ mode

    private record ModeDecision(AiMode effective, String model, String simulationNotice) {
    }

    public AiStatus status() {
        OllamaClient.Health health = ollama.health();
        AiMode effective = switch (properties.mode()) {
            case SIMULATED -> AiMode.SIMULATED;
            case LIVE -> AiMode.LIVE;
            case AUTO -> health.usable() ? AiMode.LIVE : AiMode.SIMULATED;
        };
        return new AiStatus(properties.mode(), effective, ollama.model(), ollama.baseUrl(), health.reachable(),
                health.modelInstalled(), health.detail(), health.checkedAt());
    }

    private ModeDecision decideMode() {
        if (properties.mode() == AiMode.SIMULATED) {
            return new ModeDecision(AiMode.SIMULATED, "deterministic-simulator",
                    "SIMULATION: app.ai.mode is SIMULATED, so the narrative comes from a deterministic template, not an LLM. "
                            + "Eligibility, scores and evidence are identical in both modes.");
        }
        OllamaClient.Health health = ollama.health();
        if (health.usable()) {
            return new ModeDecision(AiMode.LIVE, ollama.model(), null);
        }
        if (properties.mode() == AiMode.LIVE) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE, "Live AI mode is configured but the model is unavailable: "
                    + health.detail());
        }
        return new ModeDecision(AiMode.SIMULATED, "deterministic-simulator",
                "SIMULATION: " + health.detail() + ". The narrative comes from a deterministic template, not an LLM. "
                        + "Eligibility, scores and evidence are identical in both modes.");
    }

    // ------------------------------------------------------------------ run

    public StaffingAdviceResponse advise(StaffingAdviceRequest request, String actor) {
        long started = System.nanoTime();
        String question = request.question() == null || request.question().isBlank() ? null : request.question().trim();
        String goal = "Recommend and explain candidates for demand #" + request.demandId()
                + (request.proposeEmployeeId() != null ? " and propose employee #" + request.proposeEmployeeId() : "");
        ModeDecision mode = decideMode();
        Long runId = audit.start(request.demandId(), actor, goal, question, mode.effective(), mode.model());

        Set<String> allowed = new HashSet<>(tools.names());
        if (request.proposeEmployeeId() == null) {
            allowed.remove(tools.proposeAllocation.name());
        }
        if (!Boolean.TRUE.equals(request.recordTrainingRecommendations())) {
            allowed.remove(tools.recordTraining.name());
        }
        AgentTrace trace = new AgentTrace(allowed);

        try {
            StaffingAdviceResponse response = execute(request, question, goal, actor, mode, trace, runId, started);
            audit.complete(runId, mode.effective(), mode.model(), response.status(), response.narrativeSource(),
                    response.validation().passed(), response.validation().llmAttempts(),
                    response.validation().issues(), response, trace.calls(), response.durationMs());
            log.info("Agent run {} for demand {} finished: {} / {}", runId, request.demandId(), response.status(),
                    response.narrativeSource());
            return response;
        } catch (RuntimeException e) {
            audit.fail(runId, trace.calls(), e.getMessage(), elapsedMs(started));
            throw e;
        }
    }

    private StaffingAdviceResponse execute(StaffingAdviceRequest request, String question, String goal, String actor,
            ModeDecision mode, AgentTrace trace, Long runId, long started) {
        // 1-2. Demand and deterministic ranking
        DemandResponse demand = trace.call(tools.getDemand, new DemandRef(request.demandId()));
        MatchResultResponse match = trace.call(tools.rankCandidates, new DemandRef(request.demandId()));
        int topN = request.topN() != null ? request.topN() : properties.maxCandidates();
        List<CandidateResponse> top = match.rankedCandidates().stream()
                .filter(CandidateResponse::eligible).limit(topN).toList();

        // 3. Full stored evidence for each explained candidate
        Map<Long, EmployeeResponse> evidenceById = new LinkedHashMap<>();
        for (CandidateResponse c : top) {
            evidenceById.put(c.employeeId(), trace.call(tools.getEmployeeEvidence, new EmployeeRef(c.employeeId())));
        }
        List<CandidateEvidence> candidates = top.stream()
                .map(c -> new CandidateEvidence(c, evidenceById.get(c.employeeId()).skills())).toList();

        // 4. Entities in the question
        QuestionEntities entities = question == null ? QuestionEntities.none()
                : trace.call(tools.resolveQuestion, new QuestionRef(question));
        List<ReferencedEmployee> referenced = new ArrayList<>();
        for (EmployeeMention mention : entities.employees()) {
            referenced.add(referencedEmployee(mention, demand, match, evidenceById, trace));
        }

        // 5. Learning paths (re-plan to near-miss candidates when nobody is eligible)
        List<CandidateResponse> learners = top.isEmpty() ? nearMisses(match, topN) : top;
        var gaps = LearningPathService.gapsOf(learners);
        List<LearningPath> paths = gaps.isEmpty() ? List.of()
                : trace.call(tools.buildLearningPaths, new GapQuery(gaps));

        List<DataGap> dataGaps = dataGaps(candidates, referenced, entities);
        ExclusionSummary exclusions = exclusionSummary(match);
        EvidencePack evidence = new EvidencePack(demand, question, entities.fabricationRequested(), candidates,
                List.copyOf(referenced), paths, dataGaps, exclusions, entities.skills());

        // 6. Narrate + validate
        NarrationOutcome narration = narrate(evidence, mode, trace);

        // 7. Optional state changes, each re-validated by the owning service
        List<TrainingRecommendationResponse> recorded = List.of();
        if (Boolean.TRUE.equals(request.recordTrainingRecommendations()) && !paths.isEmpty()) {
            recorded = trace.call(tools.recordTraining, new TrainingCommand(demand.id(), paths, actor));
        }
        ProposalOutcome proposal = request.proposeEmployeeId() == null ? null
                : propose(demand.id(), request.proposeEmployeeId(), runId, actor, trace);

        List<Recommendation> recommendations = candidates.stream().map(ce -> {
            CandidateResponse c = ce.candidate();
            CandidateNarrative n = narration.result().candidates().stream()
                    .filter(x -> c.employeeId().equals(x.employeeId())).findFirst()
                    .orElse(new CandidateNarrative(c.employeeId(), "", List.of(), List.of()));
            return new Recommendation(c.rank(), c, n.explanation(), n.strengths(), n.considerations());
        }).toList();

        AgentRunStatus status = narration.source() == NarrativeSource.DETERMINISTIC_FALLBACK
                || narration.source() == NarrativeSource.LLM_WITH_REPAIRS
                || (properties.mode() == AiMode.AUTO && mode.effective() == AiMode.SIMULATED)
                        ? AgentRunStatus.COMPLETED_WITH_FALLBACK
                        : AgentRunStatus.COMPLETED;
        String notice = switch (narration.source()) {
            case DETERMINISTIC_FALLBACK -> "FALLBACK: no usable model output, so a deterministic explanation is shown.";
            case LLM_WITH_REPAIRS -> "PARTIALLY REPAIRED: statements that failed fact-checking were replaced with "
                    + "deterministic text (" + String.join(", ", narration.validation().repairedFields()) + ").";
            default -> mode.simulationNotice();
        };

        return new StaffingAdviceResponse(runId, goal, mode.effective(), mode.model(), notice, status,
                narration.source(), demand, question, narration.result().summary(),
                question == null ? null : narration.result().answer(), recommendations, paths, dataGaps,
                List.copyOf(referenced), exclusions, narration.validation(), trace.calls(), recorded, proposal,
                GUARDRAILS, elapsedMs(started), clock.instant());
    }

    // ------------------------------------------------------------------ narration
    // loop

    private record NarrationOutcome(NarrationResult result, NarrativeSource source, Validation validation) {
    }

    private NarrationOutcome narrate(EvidencePack evidence, ModeDecision mode, AgentTrace trace) {
        List<String> directory = questionAnalyzer.directoryNames();
        List<String> skills = questionAnalyzer.skillNames();
        List<ValidationIssue> allIssues = new ArrayList<>();
        NarrationResult deterministic = simulatedNarrator.narrate(evidence, List.of());
        int attempts = 0;
        NarrationResult best = null;
        List<Issue> bestIssues = List.of();

        if (mode.effective() == AiMode.LIVE) {
            List<String> feedback = List.of();
            // Retry only while the projected total (average attempt time × attempts) fits
            // in the narration budget.
            long budgetNanos = properties.narrationBudget().toNanos();
            long loopStart = System.nanoTime();
            while (attempts < properties.maxLlmAttempts()
                    && (attempts == 0 || (System.nanoTime() - loopStart) * (attempts + 1) / attempts < budgetNanos)) {
                attempts++;
                long t0 = System.nanoTime();
                String purpose = "Ask " + mode.model() + " to explain the evidence (JSON-schema constrained output)";
                NarrationResult result;
                try {
                    result = llmNarrator.narrate(evidence, feedback);
                    trace.recordStep("llm_narrate", purpose, Map.of("attempt", attempts, "model", mode.model(),
                            "candidates", evidence.candidates().size()), "Narrative received", true, t0);
                } catch (OllamaClient.LlmException e) {
                    trace.recordStep("llm_narrate", purpose, Map.of("attempt", attempts, "model", mode.model()),
                            "Failed: " + e.getMessage(), false, t0);
                    allIssues.add(new ValidationIssue(attempts, "llm", "LLM_ERROR", e.getMessage(), null));
                    break;
                }
                List<Issue> issues = validate(result, evidence, directory, skills, trace, attempts);
                record(allIssues, issues, attempts);
                if (issues.isEmpty()) {
                    return new NarrationOutcome(result, NarrativeSource.LLM, new Validation(true, attempts, allIssues,
                            List.of(), attempts == 1 ? "Every statement is grounded in stored evidence."
                                    : "Accepted after %d attempts; rejected statements from earlier attempts are listed."
                                            .formatted(attempts)));
                }
                if (best == null || issues.size() < bestIssues.size()) {
                    best = result;
                    bestIssues = issues;
                }
                feedback = issues.stream().map(Issue::message).toList();
            }
        }

        if (best != null) {
            // Keep the model's statements that passed; replace only the fields that failed.
            long t0 = System.nanoTime();
            Repair repair = repair(best, bestIssues, deterministic);
            trace.recordStep("repair_narrative", "Replace only the statements that failed fact-checking",
                    Map.of("repairedFields", repair.fields()), "Replaced: " + String.join(", ", repair.fields()), true,
                    t0);
            List<Issue> issues = validate(repair.result(), evidence, directory, skills, trace, attempts);
            if (issues.isEmpty()) {
                return new NarrationOutcome(repair.result(), NarrativeSource.LLM_WITH_REPAIRS, new Validation(true,
                        attempts, allIssues, repair.fields(),
                        "Statements that failed fact-checking were replaced with deterministic text; the rest is model output."));
            }
            record(allIssues, issues, attempts);
        }

        NarrativeSource source = mode.effective() == AiMode.LIVE ? NarrativeSource.DETERMINISTIC_FALLBACK
                : NarrativeSource.SIMULATED;
        long t0 = System.nanoTime();
        trace.recordStep(source == NarrativeSource.SIMULATED ? "simulated_narrate" : "deterministic_fallback",
                "Compose the explanation from evidence fields with a deterministic template",
                Map.of("candidates", evidence.candidates().size()), "Deterministic narrative composed", true, t0);
        List<Issue> issues = validate(deterministic, evidence, directory, skills, trace, attempts + 1);
        record(allIssues, issues, attempts + 1);
        String note = source == NarrativeSource.SIMULATED
                ? "Simulated narrative, checked by the same validator as model output."
                : "Model output was unavailable after %d attempt(s); a deterministic explanation is shown."
                        .formatted(attempts);
        return new NarrationOutcome(deterministic, source, new Validation(issues.isEmpty(), attempts, allIssues,
                List.of(), note));
    }

    private record Repair(NarrationResult result, List<String> fields) {
    }

    /**
     * Field-level merge: failing summary, answer or candidate narratives come from
     * the deterministic narrator.
     */
    private static Repair repair(NarrationResult model, List<Issue> issues, NarrationResult deterministic) {
        Set<String> failedFields = new HashSet<>();
        Set<Long> failedCandidates = new HashSet<>();
        for (Issue issue : issues) {
            if (NarrativeValidator.FIELD_CANDIDATE.equals(issue.field())) {
                failedCandidates.add(issue.employeeId());
            } else {
                failedFields.add(issue.field());
            }
        }
        List<String> repaired = new ArrayList<>();
        String summary = model.summary();
        if (failedFields.contains(NarrativeValidator.FIELD_SUMMARY)) {
            summary = deterministic.summary();
            repaired.add("summary");
        }
        String answer = model.answer();
        if (failedFields.contains(NarrativeValidator.FIELD_ANSWER)) {
            answer = deterministic.answer();
            repaired.add("answer");
        }
        List<CandidateNarrative> candidates = new ArrayList<>();
        for (CandidateNarrative fallback : deterministic.candidates()) {
            Optional<CandidateNarrative> fromModel = model.candidates().stream()
                    .filter(c -> fallback.employeeId().equals(c.employeeId())).findFirst();
            if (fromModel.isPresent() && !failedCandidates.contains(fallback.employeeId())) {
                candidates.add(fromModel.get());
            } else {
                candidates.add(fallback);
                repaired.add("candidate:" + fallback.employeeId());
            }
        }
        return new Repair(new NarrationResult(summary, answer, candidates), repaired);
    }

    private static void record(List<ValidationIssue> target, List<Issue> issues, int attempt) {
        issues.forEach(i -> target.add(new ValidationIssue(attempt, i.field(), i.code(), i.message(), i.employeeId())));
    }

    private List<Issue> validate(NarrationResult result, EvidencePack evidence, List<String> directory,
            List<String> skills, AgentTrace trace, int attempt) {
        long t0 = System.nanoTime();
        List<Issue> issues = validator.validate(result, evidence, directory, skills);
        trace.recordStep("validate_narrative", "Fact-check every statement against the evidence",
                Map.of("attempt", attempt), issues.isEmpty() ? "All claims grounded in stored evidence"
                        : "%d issue(s): %s".formatted(issues.size(), issues.getFirst().message()),
                issues.isEmpty(), t0);
        return issues;
    }

    // ------------------------------------------------------------------ helpers

    private ReferencedEmployee referencedEmployee(EmployeeMention mention, DemandResponse demand,
            MatchResultResponse match, Map<Long, EmployeeResponse> evidenceById,
            AgentTrace trace) {
        EmployeeResponse employee = evidenceById.containsKey(mention.employeeId())
                ? evidenceById.get(mention.employeeId())
                : trace.call(tools.getEmployeeEvidence, new EmployeeRef(mention.employeeId()));
        Optional<CandidateResponse> ranked = match.rankedCandidates().stream()
                .filter(c -> c.employeeId().equals(mention.employeeId())).findFirst();
        Optional<CandidateResponse> excluded = match.excludedCandidates().stream()
                .filter(c -> c.employeeId().equals(mention.employeeId())).findFirst();
        AvailabilityProfile availability = ranked.or(() -> excluded).map(CandidateResponse::availability)
                .orElseGet(() -> trace.call(tools.checkAvailability, new AvailabilityQuery(mention.employeeId(),
                        demand.startDate(), demand.endDate(), demand.allocationPercent())));
        List<String> notes = new ArrayList<>();
        excluded.ifPresent(c -> notes.addAll(c.exclusionReasons()));
        if (ranked.isEmpty() && excluded.isEmpty()) {
            notes.add("Not evaluated for this demand: holds none of the mandatory skills");
        }
        if (employee.location() == null) {
            notes.add("Work location not recorded");
        }
        return new ReferencedEmployee(employee.id(), employee.employeeCode(), employee.name(), ranked.isPresent(),
                ranked.map(CandidateResponse::rank).orElse(null), employee.skills(), availability, notes);
    }

    /**
     * Excluded candidates whose only blocker is skills: the best targets for a
     * learning path.
     */
    private static List<CandidateResponse> nearMisses(MatchResultResponse match, int limit) {
        return match.excludedCandidates().stream()
                .filter(c -> c.employmentStatus().isAssignable())
                .filter(c -> c.availability().status() != AvailabilityStatus.NONE)
                .filter(c -> !c.meetsMandatorySkills())
                .limit(limit)
                .toList();
    }

    private static List<DataGap> dataGaps(List<CandidateEvidence> candidates, List<ReferencedEmployee> referenced,
            QuestionEntities entities) {
        List<DataGap> gaps = new ArrayList<>();
        for (CandidateEvidence ce : candidates) {
            CandidateResponse c = ce.candidate();
            c.dataGaps().forEach(g -> gaps.add(new DataGap(c.employeeId(), c.name(), "evidence", g)));
        }
        for (ReferencedEmployee r : referenced) {
            for (String skill : entities.skills()) {
                boolean stored = r.storedSkills().stream().map(EmployeeSkillResponse::skillName)
                        .anyMatch(name -> name.equalsIgnoreCase(skill));
                if (!stored) {
                    gaps.add(new DataGap(r.employeeId(), r.name(), "question:skill",
                            "no stored proficiency for %s; it cannot be stated or assumed".formatted(skill)));
                }
            }
            if (r.storedSkills().isEmpty()) {
                gaps.add(new DataGap(r.employeeId(), r.name(), "question:skills",
                        "no skills are stored for this employee"));
            }
        }
        if (entities.fabricationRequested()) {
            gaps.add(new DataGap(null, null, "question:assumption",
                    "the question asked to assume or invent data; only stored records are used"));
        }
        return gaps;
    }

    private static ExclusionSummary exclusionSummary(MatchResultResponse match) {
        Map<String, Long> reasons = new TreeMap<>();
        for (CandidateResponse c : match.excludedCandidates()) {
            for (String reason : c.exclusionReasons()) {
                String category = reason.startsWith("Missing mandatory") ? "missing mandatory skill"
                        : reason.startsWith("Mandatory skill") ? "mandatory skill below level"
                                : reason.startsWith("Not available") ? "no free capacity"
                                        : reason.startsWith("Employment status") ? "employment status not assignable"
                                                : "other";
                reasons.merge(category, 1L, Long::sum);
            }
        }
        return new ExclusionSummary(match.candidatePoolSize(), match.rankedCandidates().size(),
                match.excludedCandidates().size(), match.notConsideredCount(), reasons);
    }

    private ProposalOutcome propose(Long demandId, Long employeeId, Long runId, String actor, AgentTrace trace) {
        try {
            var result = trace.call(tools.proposeAllocation, new ProposalCommand(demandId, employeeId,
                    "Proposed via AI staffing assistant (run #%d)".formatted(runId), actor));
            return new ProposalOutcome(true, result.allocation(), null,
                    "Allocation submitted for approval. An approver must confirm it; rules are re-checked at that moment.");
        } catch (ApiException e) {
            return new ProposalOutcome(false, null, e.getCode().name(), e.getMessage());
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
