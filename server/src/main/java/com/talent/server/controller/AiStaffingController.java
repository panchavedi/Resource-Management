package com.talent.server.controller;

import com.talent.server.dto.request.StaffingAdviceRequest;
import com.talent.server.dto.response.AgentRunResponses.AiStatus;
import com.talent.server.dto.response.AgentRunResponses.RunDetail;
import com.talent.server.dto.response.AgentRunResponses.RunSummary;
import com.talent.server.dto.response.StaffingAdviceResponse;
import com.talent.server.security.Roles;
import com.talent.server.service.ai.AgentAuditService;
import com.talent.server.service.ai.StaffingAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI staffing agent")
public class AiStaffingController {

    private final StaffingAgentService agent;
    private final AgentAuditService audit;

    public AiStaffingController(StaffingAgentService agent, AgentAuditService audit) {
        this.agent = agent;
        this.audit = audit;
    }

    @PostMapping("/staffing-advice")
    @PreAuthorize(Roles.MANAGE)
    @Operation(summary = "Run the staffing agent: rank, explain, disclose gaps, suggest learning, optionally propose")
    public StaffingAdviceResponse staffingAdvice(@Valid @RequestBody StaffingAdviceRequest request,
            Authentication authentication) {
        return agent.advise(request, authentication.getName());
    }

    @GetMapping("/status")
    @Operation(summary = "Configured and effective AI mode, model reachability")
    public AiStatus status() {
        return agent.status();
    }

    @GetMapping("/runs")
    @Operation(summary = "Recent agent runs (audit trail)")
    public List<RunSummary> runs(@RequestParam(required = false) Long demandId,
            @RequestParam(defaultValue = "20") int limit) {
        return audit.recent(demandId, limit);
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "One agent run with tool calls and the exact response returned")
    public RunDetail run(@PathVariable Long id) {
        return audit.get(id);
    }
}
