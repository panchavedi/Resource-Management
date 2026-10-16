package com.talent.server.entity;

import com.talent.server.enums.AllocationEventType;
import com.talent.server.enums.AllocationStatus;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Assignment of an employee to a demand for a date range and percentage.
 * State changes go through the domain methods below, which enforce the lifecycle and append
 * an immutable {@link AllocationEvent}; rows are never deleted so history is always retained.
 */
@Entity
@Table(name = "allocations")
public class Allocation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    private ProjectDemand demand;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "allocation_percent", nullable = false)
    private int allocationPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllocationStatus status;

    @Column(name = "requested_by", nullable = false, length = 80)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "request_note", length = 500)
    private String requestNote;

    @Column(name = "decided_by", length = 80)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "cancelled_by", length = 80)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Version
    private long version;

    @OneToMany(mappedBy = "allocation", cascade = CascadeType.ALL)
    @OrderBy("occurredAt ASC, id ASC")
    private List<AllocationEvent> events = new ArrayList<>();

    protected Allocation() {
    }

    public static Allocation request(Employee employee, ProjectDemand demand, LocalDate startDate, LocalDate endDate,
                                     int allocationPercent, String requestedBy, String note, String idempotencyKey,
                                     Instant now) {
        Allocation allocation = new Allocation();
        allocation.employee = employee;
        allocation.demand = demand;
        allocation.startDate = startDate;
        allocation.endDate = endDate;
        allocation.allocationPercent = allocationPercent;
        allocation.status = AllocationStatus.PENDING_APPROVAL;
        allocation.requestedBy = requestedBy;
        allocation.requestedAt = now;
        allocation.requestNote = note;
        allocation.idempotencyKey = idempotencyKey;
        allocation.record(AllocationEventType.REQUESTED, null, requestedBy, note, now);
        return allocation;
    }

    public void approve(String approver, String note, Instant now) {
        requireStatus(AllocationStatus.PENDING_APPROVAL, "approve");
        AllocationStatus from = status;
        status = AllocationStatus.APPROVED;
        decidedBy = approver;
        decidedAt = now;
        decisionNote = note;
        record(AllocationEventType.APPROVED, from, approver, note, now);
    }

    public void reject(String approver, String note, Instant now) {
        requireStatus(AllocationStatus.PENDING_APPROVAL, "reject");
        AllocationStatus from = status;
        status = AllocationStatus.REJECTED;
        decidedBy = approver;
        decidedAt = now;
        decisionNote = note;
        record(AllocationEventType.REJECTED, from, approver, note, now);
    }

    /** Cancels a pending or approved allocation, releasing the capacity it held. */
    public void cancel(String actor, String reason, Instant now) {
        if (!status.holdsCapacity()) {
            throw invalidTransition("cancel");
        }
        AllocationStatus from = status;
        status = AllocationStatus.CANCELLED;
        cancelledBy = actor;
        cancelledAt = now;
        cancellationReason = reason;
        record(AllocationEventType.CANCELLED, from, actor, reason, now);
    }

    private void requireStatus(AllocationStatus expected, String action) {
        if (status != expected) {
            throw invalidTransition(action);
        }
    }

    private ApiException invalidTransition(String action) {
        return new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                "Cannot %s an allocation in status %s".formatted(action, status),
                Map.of("allocationId", String.valueOf(id), "currentStatus", status.name()));
    }

    private void record(AllocationEventType type, AllocationStatus from, String actor, String note, Instant now) {
        events.add(new AllocationEvent(this, type, from, status, actor, note, now));
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public ProjectDemand getDemand() {
        return demand;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getAllocationPercent() {
        return allocationPercent;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getRequestNote() {
        return requestNote;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getVersion() {
        return version;
    }

    public List<AllocationEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
