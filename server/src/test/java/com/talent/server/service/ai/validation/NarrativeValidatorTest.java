package com.talent.server.service.ai.validation;

import com.talent.server.config.MatchingProperties;
import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.dto.response.EmployeeSkillResponse;
import com.talent.server.dto.response.StaffingAdviceResponse.ExclusionSummary;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.DemandStatus;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.enums.EvidenceSource;
import com.talent.server.enums.EvidenceStatus;
import com.talent.server.enums.MandatorySkillPolicy;
import com.talent.server.service.ai.narration.EvidencePack;
import com.talent.server.service.ai.narration.EvidencePack.CandidateEvidence;
import com.talent.server.service.ai.narration.NarrationResult;
import com.talent.server.service.ai.narration.NarrationResult.CandidateNarrative;
import com.talent.server.service.ai.narration.SimulatedStaffingNarrator;
import com.talent.server.service.ai.validation.NarrativeValidator.Issue;
import com.talent.server.service.matching.AvailabilityCalculator;
import com.talent.server.service.matching.Booking;
import com.talent.server.service.matching.CandidateScorer;
import com.talent.server.service.matching.EvidenceClassifier;
import com.talent.server.service.matching.MatchingModels.CandidateProfile;
import com.talent.server.service.matching.MatchingModels.DemandProfile;
import com.talent.server.service.matching.MatchingModels.Requirement;
import com.talent.server.service.matching.MatchingModels.SkillRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The fact checker that stands between the LLM and the user. */
class NarrativeValidatorTest {

        private static final LocalDate TODAY = LocalDate.of(2027, 1, 1);
        private static final Requirement JAVA = new Requirement(1L, "Java", "Backend", 4, true);
        private static final List<String> SKILLS = List.of("Java", "Kafka", "Rust");
        private static final List<String> DIRECTORY = List.of("Asha Rao", "Ben Cole", "Chen Wu");

        private final NarrativeValidator validator = new NarrativeValidator();
        private final EvidencePack evidence = evidence(null);

        private static EvidencePack evidence(String question) {
                MatchingProperties props = new MatchingProperties(MandatorySkillPolicy.EXCLUDE, 18,
                                new MatchingProperties.Weights(0.5, 0.3, 0.15, 0.05));
                CandidateScorer scorer = new CandidateScorer(props, new EvidenceClassifier(props,
                                Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC)));
                DemandProfile demand = new DemandProfile(1L, "Atlas", "Engineer", TODAY.plusDays(1), TODAY.plusDays(20),
                                "Pune",
                                false, 100, 1, DemandPriority.HIGH, DemandStatus.OPEN, List.of(JAVA));
                SkillRecord java = new SkillRecord(1L, "Java", "Backend", 5, "cert", EvidenceSource.CERTIFICATION,
                                TODAY.minusDays(10));
                var availability = AvailabilityCalculator.calculate(TODAY.plusDays(1), TODAY.plusDays(20), 100, 100,
                                List.of(
                                                new Booking(5L, 2L, "Other", TODAY.plusDays(11), TODAY.plusDays(20),
                                                                100, AllocationStatus.APPROVED)));
                CandidateResponse asha = scorer.evaluate(demand,
                                new CandidateProfile(10L, "E10", "Asha Rao", "Engineer",
                                                "Engineering", "Pune", true, EmploymentStatus.ACTIVE, 100,
                                                Map.of(1L, java)),
                                availability).withRank(1);
                DemandResponse demandResponse = new DemandResponse(1L, "Atlas", "Engineer", null, demand.startDate(),
                                demand.endDate(), 20, "Pune", false, 100, 1, DemandPriority.HIGH, DemandStatus.OPEN, 0,
                                0,
                                List.of(new DemandResponse.RequiredSkill(1L, "Java", "Backend", 4, true)), "manager",
                                null, null);
                List<EmployeeSkillResponse> stored = List.of(new EmployeeSkillResponse(1L, 1L, "Java", "Backend", 5,
                                "cert",
                                EvidenceSource.CERTIFICATION, TODAY.minusDays(10), "board", EvidenceStatus.VALIDATED));
                return new EvidencePack(demandResponse, question, false, List.of(new CandidateEvidence(asha, stored)),
                                List.of(),
                                List.of(), List.of(), new ExclusionSummary(1, 1, 0, 0, Map.of()), List.of());
        }

        private static NarrationResult narrative(String explanation, String... strengths) {
                return new NarrationResult("Atlas needs one Engineer.", "",
                                List.of(new CandidateNarrative(10L, explanation, List.of(strengths), List.of())));
        }

        private List<Issue> check(NarrationResult result) {
                return validator.validate(result, evidence, DIRECTORY, SKILLS);
        }

        @Test
        @DisplayName("A grounded narrative passes")
        void groundedNarrativePasses() {
                assertThat(check(narrative(
                                "Asha Rao has Java at level 5, above the required 4. She is partially available: 10 of 20 days.")))
                                .isEmpty();
        }

        @Test
        @DisplayName("The deterministic narrator always produces text that passes the validator")
        void deterministicNarrativePasses() {
                assertThat(check(new SimulatedStaffingNarrator().narrate(evidence, List.of()))).isEmpty();
        }

        @Test
        @DisplayName("TC-06-10 (unit): an invented proficiency level is rejected")
        void inventedLevelRejected() {
                assertThat(check(narrative("Asha Rao has Java at level 3.")))
                                .extracting(Issue::code).containsExactly("UNSUPPORTED_LEVEL");
        }

        @Test
        @DisplayName("TC-06-10 (unit): crediting a skill that is not stored is rejected")
        void unrecordedSkillRejected() {
                assertThat(check(narrative("Asha Rao is an expert in Kafka.")))
                                .extracting(Issue::code).containsExactly("UNRECORDED_SKILL");
        }

        @Test
        @DisplayName("Stating that a stored skill is missing is rejected")
        void contradictingRecordRejected() {
                assertThat(check(narrative("Asha Rao has no stored record for Java.")))
                                .extracting(Issue::code).contains("CONTRADICTS_RECORD");
        }

        @Test
        @DisplayName("TC-06-10 (unit): claiming full availability when only partial is computed is rejected")
        void availabilityOverclaimRejected() {
                assertThat(check(narrative("Asha Rao is fully available for the project.")))
                                .extracting(Issue::code).containsExactly("AVAILABILITY_OVERCLAIM");
        }

        @Test
        @DisplayName("Invented percentages, scores and day counts are rejected")
        void inventedNumbersRejected() {
                assertThat(check(narrative("Asha Rao scores 99.0 and is 75% free for 12 days.")))
                                .extracting(Issue::code)
                                .containsExactlyInAnyOrder("UNSUPPORTED_SCORE", "UNSUPPORTED_PERCENT",
                                                "UNSUPPORTED_DAYS");
        }

        @Test
        @DisplayName("Protected characteristics and employment decisions are rejected")
        void policyViolationsRejected() {
                assertThat(check(narrative("Asha Rao is young and we should promote her.")))
                                .extracting(Issue::code)
                                .containsExactlyInAnyOrder("PROTECTED_ATTRIBUTE", "EMPLOYMENT_DECISION");
        }

        @Test
        @DisplayName("People outside the evidence and unknown candidates are rejected")
        void structureAndPeople() {
                NarrationResult result = new NarrationResult("Ben Cole would also fit.", "",
                                List.of(new CandidateNarrative(99L, "Someone else.", List.of(), List.of())));

                assertThat(check(result)).extracting(Issue::code)
                                .contains("UNKNOWN_PERSON", "STRUCTURE");
                assertThat(check(result)).anyMatch(
                                i -> i.message().startsWith("Missing narrative for ranked candidate Asha Rao"));
        }

        @Test
        @DisplayName("A question must be answered, and issues name the field they were found in")
        void answerRequiredWhenAsked() {
                List<Issue> issues = validator.validate(narrative("Asha Rao has Java at level 5."),
                                evidence("Who is best?"), DIRECTORY, SKILLS);

                assertThat(issues).singleElement().satisfies(i -> {
                        assertThat(i.field()).isEqualTo(NarrativeValidator.FIELD_ANSWER);
                        assertThat(i.code()).isEqualTo("STRUCTURE");
                });
        }
}
