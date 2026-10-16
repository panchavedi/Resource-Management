package com.talent.server.service.matching;

import com.talent.server.config.MatchingProperties;
import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.DemandStatus;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.enums.EvidenceSource;
import com.talent.server.enums.EvidenceStatus;
import com.talent.server.enums.LocationFit;
import com.talent.server.enums.MandatorySkillPolicy;
import com.talent.server.service.matching.MatchingModels.CandidateProfile;
import com.talent.server.service.matching.MatchingModels.DemandProfile;
import com.talent.server.service.matching.MatchingModels.Requirement;
import com.talent.server.service.matching.MatchingModels.SkillRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CandidateScorerTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 1, 1);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final MatchingProperties.Weights WEIGHTS = new MatchingProperties.Weights(0.5, 0.3, 0.15, 0.05);

    private static final Requirement JAVA = new Requirement(1L, "Java", "Backend", 4, true);
    private static final Requirement SQL = new Requirement(2L, "SQL", "Data", 3, true);
    private static final Requirement KAFKA = new Requirement(3L, "Kafka", "Data", 3, false);

    private static CandidateScorer scorer(MandatorySkillPolicy policy) {
        MatchingProperties props = new MatchingProperties(policy, 18, WEIGHTS);
        return new CandidateScorer(props, new EvidenceClassifier(props, CLOCK));
    }

    private static DemandProfile demand(String location, boolean remote, Requirement... requirements) {
        return new DemandProfile(1L, "Atlas", "Engineer", TODAY.plusDays(1), TODAY.plusDays(30), location, remote, 100,
                1,
                DemandPriority.HIGH, DemandStatus.OPEN, List.of(requirements));
    }

    private static SkillRecord skill(Requirement req, int level, LocalDate validated) {
        return new SkillRecord(req.skillId(), req.skillName(), req.category(), level, "evidence",
                EvidenceSource.ASSESSMENT, validated);
    }

    private static CandidateProfile candidate(long id, String code, String location, EmploymentStatus status,
            SkillRecord... skills) {
        Map<Long, SkillRecord> byId = Arrays.stream(skills)
                .collect(Collectors.toMap(SkillRecord::skillId, Function.identity()));
        return new CandidateProfile(id, code, "Person " + code, "Engineer", "Engineering", location, true, status, 100,
                byId);
    }

    private static AvailabilityProfile free() {
        return AvailabilityCalculator.calculate(TODAY.plusDays(1), TODAY.plusDays(30), 100, 100, List.of());
    }

    private static AvailabilityProfile busy() {
        return AvailabilityCalculator.calculate(TODAY.plusDays(1), TODAY.plusDays(30), 100, 100, List.of(
                new Booking(9L, 9L, "Other", TODAY, TODAY.plusDays(60), 100, AllocationStatus.APPROVED)));
    }

    @Test
    @DisplayName("TC-06-03 (unit): score is the documented weighted sum and every factor is explained")
    void scoreBreakdownFollowsFormula() {
        CandidateResponse c = scorer(MandatorySkillPolicy.EXCLUDE).evaluate(demand("Pune", false, JAVA, SQL),
                candidate(1, "E1", "Pune", EmploymentStatus.ACTIVE,
                        skill(JAVA, 5, TODAY.minusMonths(1)), skill(SQL, 3, TODAY.minusMonths(1))),
                free());

        // Java: 0.70 + 0.30 * (5-4)/(5-4) = 1.00 ; SQL: 0.70 + 0.30 * 0/2 = 0.70 ; both
        // mandatory (weight 2)
        assertThat(c.factorScore(CandidateScorer.FACTOR_SKILL)).isEqualTo(85.0);
        assertThat(c.factorScore(CandidateScorer.FACTOR_AVAILABILITY)).isEqualTo(100.0);
        assertThat(c.factorScore(CandidateScorer.FACTOR_LOCATION)).isEqualTo(100.0);
        assertThat(c.factorScore(CandidateScorer.FACTOR_EVIDENCE)).isEqualTo(100.0);
        assertThat(c.totalScore()).isCloseTo(85 * 0.5 + 100 * 0.3 + 100 * 0.15 + 100 * 0.05, within(0.05));
        assertThat(c.scoreBreakdown()).allSatisfy(s -> assertThat(s.explanation()).isNotBlank());
        assertThat(c.eligible()).isTrue();
    }

    @Test
    @DisplayName("TC-06-04 (unit): missing mandatory skill excludes under EXCLUDE policy, with the reason")
    void missingMandatoryExcluded() {
        CandidateScorer scorer = scorer(MandatorySkillPolicy.EXCLUDE);
        CandidateResponse c = scorer.evaluate(demand(null, false, JAVA, SQL),
                candidate(1, "E1", "Pune", EmploymentStatus.ACTIVE, skill(JAVA, 5, TODAY)), free());

        assertThat(c.eligible()).isFalse();
        assertThat(c.meetsMandatorySkills()).isFalse();
        assertThat(scorer.isRanked(c)).isFalse();
        assertThat(c.exclusionReasons())
                .containsExactly("Missing mandatory skill SQL (requires level 3, no stored record)");
        assertThat(c.dataGaps()).contains("No stored proficiency record for SQL");
    }

    @Test
    @DisplayName("TC-06-04 (unit): under FLAG policy the candidate stays visible but is flagged and ineligible")
    void missingMandatoryFlagged() {
        CandidateScorer scorer = scorer(MandatorySkillPolicy.FLAG);
        CandidateResponse c = scorer.evaluate(demand(null, false, JAVA, SQL),
                candidate(1, "E1", "Pune", EmploymentStatus.ACTIVE, skill(JAVA, 5, TODAY), skill(SQL, 2, TODAY)),
                free());

        assertThat(c.eligible()).isFalse();
        assertThat(scorer.isRanked(c)).isTrue();
        assertThat(c.flags()).anyMatch(f -> f.startsWith("Flagged"));
        assertThat(c.gaps()).singleElement().satisfies(g -> {
            assertThat(g.skillName()).isEqualTo("SQL");
            assertThat(g.levelsShort()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Optional gaps lower the score but do not exclude")
    void optionalGapDoesNotExclude() {
        CandidateResponse c = scorer(MandatorySkillPolicy.EXCLUDE).evaluate(demand(null, false, JAVA, KAFKA),
                candidate(1, "E1", null, EmploymentStatus.ACTIVE, skill(JAVA, 4, TODAY)), free());

        assertThat(c.eligible()).isTrue();
        assertThat(c.gaps()).extracting(CandidateResponse.SkillGap::skillName).containsExactly("Kafka");
        // Java met at min (0.70, weight 2) + Kafka missing (0, weight 1) -> 1.4 / 3
        assertThat(c.factorScore(CandidateScorer.FACTOR_SKILL)).isEqualTo(46.7);
    }

    @Test
    @DisplayName("Unavailable and non-assignable candidates are excluded with explicit reasons")
    void hardRules() {
        CandidateScorer scorer = scorer(MandatorySkillPolicy.FLAG);
        CandidateResponse busy = scorer.evaluate(demand(null, false, JAVA),
                candidate(1, "E1", "Pune", EmploymentStatus.ACTIVE, skill(JAVA, 5, TODAY)), busy());
        CandidateResponse onLeave = scorer.evaluate(demand(null, false, JAVA),
                candidate(2, "E2", "Pune", EmploymentStatus.ON_LEAVE, skill(JAVA, 5, TODAY)), free());

        assertThat(busy.eligible()).isFalse();
        assertThat(scorer.isRanked(busy)).isFalse();
        assertThat(busy.exclusionReasons()).anyMatch(r -> r.startsWith("Not available"));
        assertThat(onLeave.exclusionReasons()).containsExactly("Employment status ON_LEAVE is not assignable");
    }

    @Test
    @DisplayName("Evidence freshness: never validated, stale and fresh are classified and disclosed")
    void evidenceStatus() {
        CandidateResponse c = scorer(MandatorySkillPolicy.EXCLUDE).evaluate(demand(null, false, JAVA, SQL, KAFKA),
                candidate(1, "E1", "Pune", EmploymentStatus.ACTIVE,
                        skill(JAVA, 4, TODAY.minusMonths(2)), skill(SQL, 3, TODAY.minusMonths(30)),
                        skill(KAFKA, 3, null)),
                free());

        assertThat(c.skillMatches()).extracting(CandidateResponse.SkillMatch::evidenceStatus)
                .containsExactly(EvidenceStatus.VALIDATED, EvidenceStatus.STALE, EvidenceStatus.UNVALIDATED);
        assertThat(c.factorScore(CandidateScorer.FACTOR_EVIDENCE)).isEqualTo(50.0);
        assertThat(c.dataGaps()).hasSize(2);
    }

    @Test
    @DisplayName("Location fit scores: same, remote, different with remote allowed, onsite mismatch, unknown")
    void locationFit() {
        CandidateScorer scorer = scorer(MandatorySkillPolicy.EXCLUDE);
        CandidateProfile pune = candidate(1, "E1", "Pune", EmploymentStatus.ACTIVE);
        CandidateProfile unknown = candidate(2, "E2", null, EmploymentStatus.ACTIVE);

        assertThat(scorer.assessLocation(demand("pune", false), pune).fit()).isEqualTo(LocationFit.SAME_LOCATION);
        assertThat(scorer.assessLocation(demand("Bengaluru", true), pune).fit()).isEqualTo(LocationFit.REMOTE);
        assertThat(scorer.assessLocation(demand("Bengaluru", false), pune).score()).isZero();
        assertThat(scorer.assessLocation(demand(null, false), pune).fit()).isEqualTo(LocationFit.ANY_LOCATION);
        assertThat(scorer.assessLocation(demand("Pune", false), unknown).fit()).isEqualTo(LocationFit.UNKNOWN);
    }

    @Test
    @DisplayName("TC-06-03 (unit): ranking is deterministic - eligible first, then score, then tie-breakers")
    void rankingOrder() {
        CandidateScorer scorer = scorer(MandatorySkillPolicy.FLAG);
        DemandProfile d = demand("Pune", false, JAVA);
        List<CandidateResponse> list = new ArrayList<>(List.of(
                scorer.evaluate(d, candidate(1, "E3", "Pune", EmploymentStatus.ACTIVE, skill(JAVA, 4, TODAY)), free()),
                scorer.evaluate(d, candidate(2, "E2", "Pune", EmploymentStatus.ACTIVE, skill(JAVA, 5, TODAY)), free()),
                scorer.evaluate(d, candidate(3, "E1", "Pune", EmploymentStatus.ACTIVE, skill(JAVA, 4, TODAY)), free()),
                scorer.evaluate(d, candidate(4, "E0", "Pune", EmploymentStatus.ACTIVE, skill(JAVA, 3, TODAY)),
                        free())));
        list.sort(CandidateScorer.RANKING);

        assertThat(list).extracting(CandidateResponse::employeeCode).containsExactly("E2", "E1", "E3", "E0");
    }
}
