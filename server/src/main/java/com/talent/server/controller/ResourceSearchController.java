package com.talent.server.controller;

import com.talent.server.dto.response.AvailableResourceResponse;
import com.talent.server.service.ResourceSearchService;
import com.talent.server.service.ResourceSearchService.SearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/resources")
@Tag(name = "Resource search")
public class ResourceSearchController {

    private final ResourceSearchService resourceSearchService;

    public ResourceSearchController(ResourceSearchService resourceSearchService) {
        this.resourceSearchService = resourceSearchService;
    }

    @GetMapping("/available")
    @Operation(summary = "Assignable employees ranked by availability coverage for a window, filtered by skills")
    public List<AvailableResourceResponse> available(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<Long> skillIds,
            @RequestParam(required = false) @Min(1) @Max(5) Integer minProficiency,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int allocationPercent,
            @RequestParam(defaultValue = "false") boolean includeUnavailable) {
        return resourceSearchService.search(new SearchCriteria(startDate, endDate, skillIds, minProficiency, location,
                department, allocationPercent, includeUnavailable));
    }
}
