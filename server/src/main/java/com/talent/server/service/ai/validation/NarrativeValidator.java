package com.talent.server.service.ai.validation;

import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.EmployeeSkillResponse;
import com.talent.server.dto.response.StaffingAdviceResponse.ReferencedEmployee;
import com.talent.server.enums.AvailabilityStatus;
import com.talent.server.service.LearningPathService.LearningPath;
import com.talent.server.service.ai.narration.EvidencePack;
import com.talent.server.service.ai.narration.EvidencePack.CandidateEvidence;
import com.talent.server.service.ai.narration.NarrationResult;
import com.talent.server.service.ai.narration.NarrationResult.CandidateNarrative;
import com.talent.server.service.matching.AvailabilityProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fact-checks narrative text against the evidence pack before anything reaches
 * the user.
 * Every issue names the field it was found in ({@link #FIELD_SUMMARY},
 * {@link #FIELD_ANSWER} or
 * {@link #FIELD_CANDIDATE} plus employee id), so the agent can replace exactly
 * the failing parts.
 *
 * <ul>
 * <li><b>STRUCTURE</b> – required text present; candidates match the ranking,
 * none missing or extra.</li>
 * <li><b>UNSUPPORTED_LEVEL / PERCENT / SCORE / DAYS</b> – numeric claims must
 * match stored values.</li>
 * <li><b>AVAILABILITY_OVERCLAIM</b> – "fully available" only when the computed
 * status is FULL.</li>
 * <li><b>UNRECORDED_SKILL</b> – people may only be credited with skills stored
 * for them.</li>
 * <li><b>CONTRADICTS_RECORD</b> – a skill that is stored must not be described
 * as missing.</li>
 * <li><b>UNKNOWN_PERSON</b> – no names outside the evidence.</li>
 * <li><b>PROTECTED_ATTRIBUTE / EMPLOYMENT_DECISION</b> – no sensitive
 * inferences, no HR decisions.</li>
 * </ul>
 * The checks are deliberately conservative: a false positive costs a retry or a
 * deterministic
 * replacement, while a false negative would show the user an invented fact.
 */
@Component
public class NarrativeValidator {

    public static final String FIELD_SUMMARY = "summary";
    public static final String FIELD_ANSWER = "answer";
    public static final String FIELD_CANDIDATE = "candidate";

    public record Issue(String field, String code, String message, Long employeeId) {
    }

    private static final Pattern LEVEL = Pattern.compile(
            "(?i)\\b(?:level|lvl|proficiency(?:\\s+(?:of|at|is))?)\\s*(\\d+)|\\b(\\d+)\\s*/\\s*5\\b");
    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final Pattern SCORE = Pattern
            .compile("(?i)\\bscore[sd]?(?:\\s+(?:of|is|at))?\\s*(?:of\\s*)?(\\d{1,3}(?:\\.\\d+)?)");
    private static final Pattern DAYS = Pattern.compile("(?i)\\b(\\d{1,4})\\s+(?:calendar\\s+)?days?\\b");
    private static final Pattern FULL_AVAILABILITY = Pattern.compile(
            "(?i)\\b(fully available|available for the (?:entire|full|whole)|100% available|completely available)\\b");
    private static final Pattern NEGATION = Pattern.compile(
            "(?i)\\b(no|not|lacks?|lacking|missing|without|gap|absent|unrecorded|on record|record|cannot|can't|unable|assume)\\b");
    private static final String ABSENCE = "(?i)(?:no\\s+(?:stored\\s+)?(?:proficiency\\s+)?(?:records?|data|experience)|not\\s+(?:stored|recorded|on record)"
            + "|missing|lacks?|lacking|without)\\b[^.;\\n]{0,40}?";
    private static final Pattern PROTECTED = Pattern.compile(
            "(?i)\\b(age[ds]?|years old|young|elderly|gender|male|female|woman|women|man|men|race|racial|ethnic(?:ity)?|"
                    + "religio(?:n|us)|nationality|citizenship|disab(?:led|ility)|health|medical|pregnan(?:t|cy)|"
                    + "maternity|paternity|marital|married|single parent|sexual orientation|caste)\\b");
    private static final Pattern DECISION = Pattern.compile(
            "(?i)\\b(hire|hiring|fire|firing|terminate|termination|dismiss(?:al)?|promote|promotion|demote|demotion|"
                    + "salary|compensation|pay rise|bonus|performance rating|performance review|lay ?off)\\b");

    public List<Issue> validate(NarrationResult result, EvidencePack evidence, Collection<String> directoryNames,
            Collection<String> catalogueSkills) {
        List<Issue> issues = new ArrayList<>();
        if (result == null) {
            issues.add(new Issue(FIELD_SUMMARY, "STRUCTURE", "No narrative was produced", null));
            return issues;
        }
        Facts global = globalFacts(evidence);

        // summary
        if (isBlank(result.summary())) {
            issues.add(new Issue(FIELD_SUMMARY, "STRUCTURE", "summary is empty", null));
        } else {
            checkFreeText(result.summary(), FIELD_SUMMARY, evidence, global, directoryNames, catalogueSkills, issues);
        }

        // answer
        if (evidence.hasQuestion()) {
            if (isBlank(result.answer())) {
                issues.add(new Issue(FIELD_ANSWER, "STRUCTURE", "answer is empty although a question was asked", null));
            } else {
                checkFreeText(result.answer(), FIELD_ANSWER, evidence, global, directoryNames, catalogueSkills, issues);
            }
        }

        // candidates
        Map<Long, CandidateEvidence> byId = new HashMap<>();
        evidence.candidates().forEach(c -> byId.put(c.candidate().employeeId(), c));
        Set<Long> narrated = new HashSet<>();
        for (CandidateNarrative narrative : result.candidates()) {
            CandidateEvidence ce = byId.get(narrative.employeeId());
            if (ce == null) {
                issues.add(new Issue(FIELD_CANDIDATE, "STRUCTURE",
                        "Narrative for employeeId %s who is not a ranked candidate"
                                .formatted(narrative.employeeId()),
                        narrative.employeeId()));
                continue;
            }
            if (!narrated.add(narrative.employeeId())) {
                issues.add(new Issue(FIELD_CANDIDATE, "STRUCTURE", "Duplicate narrative for " + ce.candidate().name(),
                        narrative.employeeId()));
            }
            String text = textOf(narrative);
            if (isBlank(narrative.explanation())) {
                issues.add(new Issue(FIELD_CANDIDATE, "STRUCTURE", "Empty explanation for " + ce.candidate().name(),
                        narrative.employeeId()));
            }
            checkCandidateText(scrub(text, evidence), ce, catalogueSkills, issues);
            checkPolicy(scrub(text, evidence), FIELD_CANDIDATE, ce.candidate().employeeId(), issues);
            checkPeople(text, FIELD_CANDIDATE, ce.candidate().employeeId(), evidence, directoryNames, issues);
        }
        byId.keySet().stream().filter(id -> !narrated.contains(id)).forEach(id -> issues.add(new Issue(FIELD_CANDIDATE,
                "STRUCTURE", "Missing narrative for ranked candidate %s".formatted(byId.get(id).candidate().name()),
                id)));
        return issues;
    }

    // ------------------------------------------------------------------ summary /
    // answer

    private void checkFreeText(String text, String field, EvidencePack evidence, Facts global,
            Collection<String> directoryNames, Collection<String> catalogueSkills,
            List<Issue> issues) {
        checkNumbers(text, global, field, null, issues);
        String scrubbed = scrub(text, evidence);
        checkPolicy(scrubbed, field, null, issues);
        checkPeople(text, field, null, evidence, directoryNames, issues);
        // Sentences naming a person must not credit them with skills that are not
        // stored for them.
        Map<String, Set<String>> storedByName = new HashMap<>();
        evidence.candidates().forEach(c -> storedByName.put(c.candidate().name(), skillNames(c.storedSkills())));
        evidence.referencedEmployees().forEach(r -> storedByName.put(r.name(), skillNames(r.storedSkills())));
        for (String sentence : sentences(scrubbed)) {
            storedByName.forEach((name, stored) -> {
                if (!mentions(sentence, name) || NEGATION.matcher(sentence).find()) {
                    return;
                }
                for (String skill : catalogueSkills) {
                    if (mentions(sentence, skill) && !stored.contains(skill.toLowerCase(Locale.ROOT))) {
                        issues.add(new Issue(field, "UNRECORDED_SKILL",
                                "%s is credited with %s, which is not stored for them".formatted(name, skill), null));
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // per-candidate

    private void checkCandidateText(String text, CandidateEvidence ce, Collection<String> catalogueSkills,
            List<Issue> issues) {
        CandidateResponse c = ce.candidate();
        checkNumbers(text, candidateFacts(ce), FIELD_CANDIDATE, c.employeeId(), issues);

        if (c.availability().status() != AvailabilityStatus.FULL && FULL_AVAILABILITY.matcher(text).find()) {
            issues.add(new Issue(FIELD_CANDIDATE, "AVAILABILITY_OVERCLAIM",
                    "%s is described as fully available but computed availability is %s"
                            .formatted(c.name(), c.availability().status()),
                    c.employeeId()));
        }

        Set<String> stored = skillNames(ce.storedSkills());
        for (EmployeeSkillResponse skill : ce.storedSkills()) {
            if (Pattern
                    .compile(ABSENCE + "(?<![\\p{L}\\p{N}])" + Pattern.quote(skill.skillName()) + "(?![\\p{L}\\p{N}])")
                    .matcher(text).find()) {
                issues.add(new Issue(FIELD_CANDIDATE, "CONTRADICTS_RECORD",
                        "%s is described as lacking %s, but level %d is stored".formatted(c.name(), skill.skillName(),
                                skill.proficiencyLevel()),
                        c.employeeId()));
            }
        }
        for (String sentence : sentences(text)) {
            for (String skill : catalogueSkills) {
                if (mentions(sentence, skill) && !stored.contains(skill.toLowerCase(Locale.ROOT))
                        && !NEGATION.matcher(sentence).find()) {
                    issues.add(new Issue(FIELD_CANDIDATE, "UNRECORDED_SKILL",
                            "%s is credited with %s, which is not stored for them".formatted(c.name(), skill),
                            c.employeeId()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ numbers

    private record Facts(Set<Integer> levels, Set<Double> percents, Set<Double> scores, Set<Long> days) {
        Facts {
            levels = new HashSet<>(levels);
            percents = new HashSet<>(percents);
            scores = new HashSet<>(scores);
            days = new HashSet<>(days);
        }

        Facts merge(Facts other) {
            Facts merged = new Facts(levels, percents, scores, days);
            merged.levels.addAll(other.levels);
            merged.percents.addAll(other.percents);
            merged.scores.addAll(other.scores);
            merged.days.addAll(other.days);
            return merged;
        }
    }

    private void checkNumbers(String text, Facts facts, String field, Long employeeId, List<Issue> issues) {
        Matcher level = LEVEL.matcher(text);
        while (level.find()) {
            int value = Integer.parseInt(level.group(1) != null ? level.group(1) : level.group(2));
            if (!facts.levels().contains(value)) {
                issues.add(new Issue(field, "UNSUPPORTED_LEVEL", "Level %d in \"%s\" is not in the stored evidence"
                        .formatted(value, snippet(text, level.start())), employeeId));
            }
        }
        Matcher percent = PERCENT.matcher(text);
        while (percent.find()) {
            double value = Double.parseDouble(percent.group(1));
            if (!containsApprox(facts.percents(), value) && !containsApprox(facts.scores(), value)) {
                issues.add(new Issue(field, "UNSUPPORTED_PERCENT", "%s%% in \"%s\" does not match any stored percentage"
                        .formatted(percent.group(1), snippet(text, percent.start())), employeeId));
            }
        }
        Matcher score = SCORE.matcher(text);
        while (score.find()) {
            double value = Double.parseDouble(score.group(1));
            if (!containsApprox(facts.scores(), value)) {
                issues.add(new Issue(field, "UNSUPPORTED_SCORE", "Score %s in \"%s\" does not match the computed scores"
                        .formatted(score.group(1), snippet(text, score.start())), employeeId));
            }
        }
        Matcher days = DAYS.matcher(text);
        while (days.find()) {
            long value = Long.parseLong(days.group(1));
            if (!facts.days().contains(value)) {
                issues.add(new Issue(field, "UNSUPPORTED_DAYS", "\"%d days\" does not match the computed availability"
                        .formatted(value), employeeId));
            }
        }
    }

    private Facts candidateFacts(CandidateEvidence ce) {
        CandidateResponse c = ce.candidate();
        Set<Integer> levels = new HashSet<>();
        c.skillMatches().forEach(s -> {
            levels.add(s.requiredLevel());
            if (s.actualLevel() != null) {
                levels.add(s.actualLevel());
            }
        });
        ce.storedSkills().forEach(s -> levels.add(s.proficiencyLevel()));
        Set<Double> percents = new HashSet<>();
        Set<Long> days = new HashSet<>();
        addAvailability(c.availability(), percents, days);
        Set<Double> scores = new HashSet<>();
        scores.add(c.totalScore());
        c.scoreBreakdown().forEach(s -> {
            scores.add(s.score());
            scores.add(s.weightedScore());
            percents.add(s.weight() * 100);
        });
        percents.add(c.locationFit().score());
        return new Facts(levels, percents, scores, days);
    }

    private Facts globalFacts(EvidencePack evidence) {
        var d = evidence.demand();
        Set<Integer> levels = new HashSet<>();
        d.requiredSkills().forEach(s -> levels.add(s.minimumProficiency()));
        Facts facts = new Facts(levels, Set.of((double) d.allocationPercent()), Set.of(), Set.of(d.durationDays()));
        for (CandidateEvidence ce : evidence.candidates()) {
            facts = facts.merge(candidateFacts(ce));
        }
        for (ReferencedEmployee r : evidence.referencedEmployees()) {
            Set<Integer> refLevels = new HashSet<>();
            r.storedSkills().stream().map(EmployeeSkillResponse::proficiencyLevel).forEach(refLevels::add);
            Set<Double> refPercents = new HashSet<>();
            Set<Long> refDays = new HashSet<>();
            if (r.availability() != null) {
                addAvailability(r.availability(), refPercents, refDays);
            }
            facts = facts.merge(new Facts(refLevels, refPercents, Set.of(), refDays));
        }
        Set<Integer> pathLevels = new HashSet<>();
        for (LearningPath p : evidence.learningPaths()) {
            pathLevels.add(p.targetLevel());
            if (p.currentLevel() != null) {
                pathLevels.add(p.currentLevel());
            }
            p.steps().forEach(s -> pathLevels.add(s.targetLevel()));
        }
        return facts.merge(new Facts(pathLevels, Set.of(), Set.of(), Set.of()));
    }

    private static void addAvailability(AvailabilityProfile a, Set<Double> percents, Set<Long> days) {
        percents.addAll(
                List.of((double) a.capacityPercent(), (double) a.requestedPercent(), (double) a.peakLoadPercent(),
                        (double) a.minFreePercent(), a.coveragePercent()));
        days.add(a.totalDays());
        days.add(a.availableDays());
        days.add(a.totalDays() - a.availableDays());
        a.windows().forEach(w -> {
            days.add(w.days());
            percents.add((double) w.loadPercent());
            percents.add((double) w.freePercent());
            percents.add((double) w.confirmedLoadPercent());
        });
        a.bookings().forEach(b -> percents.add((double) b.allocationPercent()));
    }

    // ------------------------------------------------------------------ policy &
    // people

    private void checkPolicy(String text, String field, Long employeeId, List<Issue> issues) {
        Matcher protectedTerm = PROTECTED.matcher(text);
        if (protectedTerm.find()) {
            issues.add(
                    new Issue(field, "PROTECTED_ATTRIBUTE", "Mentions a protected or sensitive characteristic (\"%s\")"
                            .formatted(protectedTerm.group(1)), employeeId));
        }
        Matcher decision = DECISION.matcher(text);
        if (decision.find()) {
            issues.add(new Issue(field, "EMPLOYMENT_DECISION", "Makes or suggests an employment decision (\"%s\")"
                    .formatted(decision.group(1)), employeeId));
        }
    }

    private void checkPeople(String text, String field, Long employeeId, EvidencePack evidence,
            Collection<String> directoryNames, List<Issue> issues) {
        Set<String> allowed = new HashSet<>();
        evidence.candidates().forEach(c -> allowed.add(c.candidate().name().toLowerCase(Locale.ROOT)));
        evidence.referencedEmployees().forEach(r -> allowed.add(r.name().toLowerCase(Locale.ROOT)));
        evidence.learningPaths().forEach(p -> allowed.add(p.employeeName().toLowerCase(Locale.ROOT)));
        for (String name : directoryNames) {
            if (!allowed.contains(name.toLowerCase(Locale.ROOT)) && mentions(text, name)) {
                issues.add(new Issue(field, "UNKNOWN_PERSON",
                        "Mentions %s, who is not part of the evidence".formatted(name),
                        employeeId));
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Removes the demand's own project name and role so they do not trigger skill
     * or policy checks.
     */
    private static String scrub(String text, EvidencePack evidence) {
        String result = text;
        for (String term : List.of(evidence.demand().projectName(), evidence.demand().role())) {
            if (term != null && !term.isBlank()) {
                result = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE).matcher(result).replaceAll(" ");
            }
        }
        return result;
    }

    static boolean mentions(String text, String term) {
        return Pattern.compile("(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])").matcher(text)
                .find();
    }

    private static List<String> sentences(String text) {
        return List.of(text.split("(?<=[.!?;])\\s+|\\n"));
    }

    private static Set<String> skillNames(List<EmployeeSkillResponse> skills) {
        return skills.stream().map(s -> s.skillName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static String textOf(CandidateNarrative n) {
        return Stream.concat(Stream.of(n.explanation() == null ? "" : n.explanation()),
                Stream.concat(n.strengths().stream(), n.considerations().stream())).collect(Collectors.joining("\n"));
    }

    private static boolean containsApprox(Set<Double> values, double value) {
        return values.stream().anyMatch(v -> Math.abs(v - value) < 0.051);
    }

    private static String snippet(String text, int at) {
        int start = Math.max(0, at - 30);
        int end = Math.min(text.length(), at + 30);
        return text.substring(start, end).replace('\n', ' ').trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
