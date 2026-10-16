package com.talent.server.api;

import com.talent.server.dto.response.AllocationResponse;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.Skill;
import com.talent.server.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static com.talent.server.support.TestData.TODAY;
import static com.talent.server.support.TestData.level;
import static com.talent.server.support.TestData.requires;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AllocationWorkflowIT extends IntegrationTest {

        private static final LocalDate START = TODAY.plusDays(10);
        private static final LocalDate END = TODAY.plusDays(39);

        private Skill java;
        private Employee asha;
        private DemandResponse demand;

        @BeforeEach
        void setUp() {
                java = data.skill("Java", "Backend");
                asha = data.employee("E1", "Asha Rao", "Bengaluru", level(java, 5));
                demand = data.demand("Atlas", "Engineer", "Bengaluru", START, END, 60, 1, requires(java, 4));
        }

        private ResultActions propose(Long employeeId, Long demandId, Integer percent) throws Exception {
                Map<String, Object> body = new HashMap<>(Map.of("employeeId", employeeId, "demandId", demandId));
                if (percent != null) {
                        body.put("allocationPercent", percent);
                }
                return mvc.perform(post("/api/allocations").with(asManager()).contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(body)));
        }

        private Long id(ResultActions result) throws Exception {
                return json.readValue(result.andReturn().getResponse().getContentAsString(), AllocationResponse.class)
                                .id();
        }

        private ResultActions availability(Long employeeId, int percent) throws Exception {
                return mvc.perform(get("/api/employees/{id}/availability", employeeId).with(asManager())
                                .param("startDate", START.toString()).param("endDate", END.toString())
                                .param("allocationPercent", String.valueOf(percent)));
        }

        @Test
        @DisplayName("TC-06-07: confirm after approval -> persisted, history recorded, availability and demand recalculated")
        void confirmAfterApproval() throws Exception {
                Long allocationId = id(propose(asha.getId(), demand.id(), null)
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                                .andExpect(jsonPath("$.allocationPercent").value(60)));

                mvc.perform(put("/api/allocations/{id}/approve", allocationId).with(asApprover())
                                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Good fit\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("APPROVED"))
                                .andExpect(jsonPath("$.decidedBy").value("approver"))
                                .andExpect(jsonPath("$.history[*].eventType", contains("REQUESTED", "APPROVED")));

                assertThat(jdbc.queryForObject("select status from allocations where id = ?", String.class,
                                allocationId))
                                .isEqualTo("APPROVED");
                mvc.perform(get("/api/demands/{id}", demand.id()).with(asManager()))
                                .andExpect(jsonPath("$.status").value("FILLED"))
                                .andExpect(jsonPath("$.approvedCount").value(1));
                availability(asha.getId(), 50)
                                .andExpect(jsonPath("$.peakLoadPercent").value(60))
                                .andExpect(jsonPath("$.minFreePercent").value(40))
                                .andExpect(jsonPath("$.status").value("PARTIAL"))
                                .andExpect(jsonPath("$.windows[0].confirmedLoadPercent").value(60));
        }

        @Test
        @DisplayName("TC-06-08: allocating above the capacity limit is rejected with the conflicting load")
        void aboveCapacityRejected() throws Exception {
                DemandResponse second = data.demand("Nimbus", "Engineer", "Bengaluru", START, END, 50, 1,
                                requires(java, 4));
                data.approved(asha.getId(), demand.id(), START, END, 60);

                propose(asha.getId(), second.id(), 50)
                                .andExpect(status().isUnprocessableContent())
                                .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"))
                                .andExpect(jsonPath("$.details.peakLoadPercent").value(60))
                                .andExpect(jsonPath("$.details.requestedPercent").value(50))
                                .andExpect(jsonPath("$.details.capacityPercent").value(100));

                propose(asha.getId(), second.id(), 40).andExpect(status().isCreated());
                assertThat(jdbc.queryForObject("select count(*) from allocations", Integer.class)).isEqualTo(2);
        }

        @Test
        @DisplayName("Approval re-validates eligibility at commit time (employee went on leave after the proposal)")
        void approvalRevalidates() throws Exception {
                Long allocationId = id(propose(asha.getId(), demand.id(), null).andExpect(status().isCreated()));
                mvc.perform(patch("/api/employees/{id}/status", asha.getId()).with(asManager())
                                .contentType(MediaType.APPLICATION_JSON).content("{\"employmentStatus\":\"ON_LEAVE\"}"))
                                .andExpect(status().isOk());

                mvc.perform(put("/api/allocations/{id}/approve", allocationId).with(asApprover()))
                                .andExpect(status().isUnprocessableContent())
                                .andExpect(jsonPath("$.code").value("CANDIDATE_NOT_ELIGIBLE"));
                assertThat(jdbc.queryForObject("select status from allocations where id = ?", String.class,
                                allocationId))
                                .isEqualTo("PENDING_APPROVAL");
        }

        @Test
        @DisplayName("Approval requires the APPROVER role and a different person than the requester")
        void approvalAuthorization() throws Exception {
                Long allocationId = id(propose(asha.getId(), demand.id(), null));

                mvc.perform(put("/api/allocations/{id}/approve", allocationId).with(asManager()))
                                .andExpect(status().isForbidden());
                mvc.perform(put("/api/allocations/{id}/approve", allocationId).with(as("manager", "ADMIN")))
                                .andExpect(status().isUnprocessableContent())
                                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("TC-06-12: cancel releases capacity and retains the full history")
        void cancelReleasesCapacityAndKeepsHistory() throws Exception {
                AllocationResponse approved = data.approved(asha.getId(), demand.id(), START, END, 60);
                availability(asha.getId(), 60).andExpect(jsonPath("$.status").value("PARTIAL"));

                mvc.perform(put("/api/allocations/{id}/cancel", approved.id()).with(asManager())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Client paused the project\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CANCELLED"))
                                .andExpect(jsonPath("$.cancellationReason").value("Client paused the project"))
                                .andExpect(jsonPath("$.history[*].eventType",
                                                contains("REQUESTED", "APPROVED", "CANCELLED")));

                availability(asha.getId(), 100).andExpect(jsonPath("$.status").value("FULL"))
                                .andExpect(jsonPath("$.peakLoadPercent").value(0));
                mvc.perform(get("/api/demands/{id}", demand.id()).with(asManager()))
                                .andExpect(jsonPath("$.status").value("OPEN"));
                assertThat(jdbc.queryForObject("select count(*) from allocation_events where allocation_id = ?",
                                Integer.class,
                                approved.id())).isEqualTo(3);
                // Capacity is reusable: the same employee can be proposed again.
                propose(asha.getId(), demand.id(), null).andExpect(status().isCreated());
                // A cancelled allocation cannot be cancelled again.
                mvc.perform(put("/api/allocations/{id}/cancel", approved.id()).with(asManager())
                                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        }

        @Test
        @DisplayName("Idempotency-Key replays return the original allocation instead of a duplicate")
        void idempotentProposal() throws Exception {
                String body = toJson(Map.of("employeeId", asha.getId(), "demandId", demand.id()));
                Long first = id(mvc
                                .perform(post("/api/allocations").with(asManager())
                                                .header("Idempotency-Key", "retry-key-0001")
                                                .contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isCreated()));
                Long replay = id(mvc
                                .perform(post("/api/allocations").with(asManager())
                                                .header("Idempotency-Key", "retry-key-0001")
                                                .contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isOk()));

                assertThat(replay).isEqualTo(first);
                assertThat(jdbc.queryForObject("select count(*) from allocations", Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("Dates outside the demand period, duplicates and filled headcount are rejected")
        void proposalGuards() throws Exception {
                mvc.perform(post("/api/allocations").with(asManager()).contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(Map.of("employeeId", asha.getId(), "demandId", demand.id(),
                                                "startDate", START.minusDays(5).toString(), "endDate",
                                                END.toString()))))
                                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));

                propose(asha.getId(), demand.id(), 30).andExpect(status().isCreated());
                propose(asha.getId(), demand.id(), 30)
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("ALLOCATION_ALREADY_EXISTS"));

                Employee ben = data.employee("E2", "Ben Cole", "Bengaluru", level(java, 5));
                propose(ben.getId(), demand.id(), null).andExpect(jsonPath("$.code").value("HEADCOUNT_FILLED"));
        }
}
