package com.talent.server.controller;

import com.talent.server.dto.request.AllocationDecisionRequest;
import com.talent.server.dto.request.CancelAllocationRequest;
import com.talent.server.dto.request.CreateAllocationRequest;
import com.talent.server.dto.response.AllocationResponse;
import com.talent.server.dto.response.PageResponse;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.security.Roles;
import com.talent.server.service.AllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/allocations")
@Tag(name = "Allocations")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGE)
    @Operation(summary = "Propose an allocation (PENDING_APPROVAL). Replaying an Idempotency-Key returns the original (200).")
    public ResponseEntity<AllocationResponse> propose(
            @Valid @RequestBody CreateAllocationRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) @Pattern(regexp = "^[A-Za-z0-9._:-]{8,80}$", message = "must be 8-80 URL-safe characters") String idempotencyKey,
            Authentication authentication) {
        var result = allocationService.propose(request, idempotencyKey, authentication.getName());
        if (result.replayed()) {
            return ResponseEntity.ok(result.allocation());
        }
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(result.allocation().id()).toUri()).body(result.allocation());
    }

    @GetMapping
    public PageResponse<AllocationResponse> search(@RequestParam(required = false) List<AllocationStatus> status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long demandId,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return allocationService.search(status, employeeId, demandId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Allocation with its full state history")
    public AllocationResponse get(@PathVariable Long id) {
        return allocationService.get(id);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize(Roles.APPROVE)
    @Operation(summary = "Approve; every rule is re-validated at commit time")
    public AllocationResponse approve(@PathVariable Long id,
            @Valid @RequestBody(required = false) AllocationDecisionRequest request,
            Authentication authentication) {
        return allocationService.approve(id, request == null ? null : request.note(), authentication.getName());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize(Roles.APPROVE)
    public AllocationResponse reject(@PathVariable Long id,
            @Valid @RequestBody(required = false) AllocationDecisionRequest request,
            Authentication authentication) {
        return allocationService.reject(id, request == null ? null : request.note(), authentication.getName());
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize(Roles.CANCEL)
    @Operation(summary = "Cancel; releases capacity and keeps the history")
    public AllocationResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelAllocationRequest request,
            Authentication authentication) {
        return allocationService.cancel(id, request.reason(), authentication.getName());
    }
}
