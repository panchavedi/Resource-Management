package com.talent.server.entity;

import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.DemandStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A request for staff on a project: role, period, allocation percentage and skill requirements. */
@Entity
@Table(name = "project_demands")
public class ProjectDemand extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_name", nullable = false, length = 120)
    private String projectName;

    @Column(nullable = false, length = 120)
    private String role;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 80)
    private String location;

    @Column(name = "remote_allowed", nullable = false)
    private boolean remoteAllowed;

    @Column(name = "allocation_percent", nullable = false)
    private int allocationPercent;

    @Column(nullable = false)
    private int headcount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DemandPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemandStatus status = DemandStatus.OPEN;

    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "demand", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DemandSkill> requiredSkills = new ArrayList<>();

    protected ProjectDemand() {
    }

    public ProjectDemand(String projectName, String role, String description, LocalDate startDate, LocalDate endDate,
                         String location, boolean remoteAllowed, int allocationPercent, int headcount,
                         DemandPriority priority, String createdBy) {
        this.projectName = projectName;
        this.role = role;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.location = location;
        this.remoteAllowed = remoteAllowed;
        this.allocationPercent = allocationPercent;
        this.headcount = headcount;
        this.priority = priority;
        this.createdBy = createdBy;
    }

    public DemandSkill addRequiredSkill(Skill skill, int minimumProficiency, boolean mandatory) {
        DemandSkill requirement = new DemandSkill(this, skill, minimumProficiency, mandatory);
        requiredSkills.add(requirement);
        return requirement;
    }

    public boolean covers(LocalDate start, LocalDate end) {
        return !start.isBefore(startDate) && !end.isAfter(endDate);
    }

    public long durationDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** Recomputes OPEN / PARTIALLY_FILLED / FILLED from the number of approved allocations. */
    public void refreshStaffingStatus(long approvedAllocations) {
        if (status == DemandStatus.CANCELLED) {
            return;
        }
        if (approvedAllocations <= 0) {
            status = DemandStatus.OPEN;
        } else if (approvedAllocations < headcount) {
            status = DemandStatus.PARTIALLY_FILLED;
        } else {
            status = DemandStatus.FILLED;
        }
    }

    public void cancel() {
        status = DemandStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getRole() {
        return role;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getLocation() {
        return location;
    }

    public boolean isRemoteAllowed() {
        return remoteAllowed;
    }

    public int getAllocationPercent() {
        return allocationPercent;
    }

    public int getHeadcount() {
        return headcount;
    }

    public DemandPriority getPriority() {
        return priority;
    }

    public DemandStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public long getVersion() {
        return version;
    }

    public List<DemandSkill> getRequiredSkills() {
        return Collections.unmodifiableList(requiredSkills);
    }
}
