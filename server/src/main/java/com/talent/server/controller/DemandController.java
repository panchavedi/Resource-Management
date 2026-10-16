package com.talent.server.controller;

import com.talent.server.dto.request.CreateDemandRequest;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.dto.response.MatchResultResponse;
import com.talent.server.dto.response.PageResponse;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.DemandStatus;
import com.talent.server.security.Roles;
import com.talent.server.service.CandidateMatchingService;
import com.talent.server.service.DemandService;
import com.talent.server.service.DemandService.DemandFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/demands")
@Tag(name = "Demands")
public class DemandController {

    private final DemandService demandService;
    private final CandidateMatchingService matchingService;

    public DemandController(DemandService demandService, CandidateMatchingService matchingService) {
        this.demandService = demandService;
        this.matchingService = matchingService;
    }

    public record CancelDemandRequest(@Size(max = 500) String reason) {
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGE)
    @Operation(summary = "Create a demand (role, valid dates and allocation percentage are mandatory)")
    public ResponseEntity<DemandResponse> create(@Valid @RequestBody CreateDemandRequest request,
            Authentication authentication) {
        DemandResponse created = demandService.create(request, authentication.getName());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "List demands filtered by status, priority, required skill, text and date window")
    public PageResponse<DemandResponse> search(@RequestParam(required = false) List<DemandStatus> status,
            @RequestParam(required = false) DemandPriority priority,
            @RequestParam(required = false) Long skillId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return demandService.search(new DemandFilter(status, priority, skillId, q, from, to), pageable);
    }

    @GetMapping("/{id}")
    public DemandResponse get(@PathVariable Long id) {
        return demandService.get(id);
    }

    @PostMapping("/{id}/recommendations")
    @Operation(summary = "Rank candidates with eligibility rules and a transparent score breakdown")
    public MatchResultResponse recommendations(@PathVariable Long id) {
        return matchingService.rankCandidates(id);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize(Roles.MANAGE)
    @Operation(summary = "Cancel a demand and release every allocation that holds capacity for it")
    public DemandResponse cancel(@PathVariable Long id,
            @Valid @RequestBody(required = false) CancelDemandRequest request,
            Authentication authentication) {
        return demandService.cancel(id, authentication.getName(), request == null ? null : request.reason());
    }
}
