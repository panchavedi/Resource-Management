package com.talent.server.entity;

import com.talent.server.enums.AgentRunStatus;
import com.talent.server.enums.AiMode;
import com.talent.server.enums.NarrativeSource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Audit record of one staffing-agent run: goal, mode, every tool call, validation outcome and the
 * exact response returned. Lets reviewers verify that each statement was grounded in stored data.
 */
@Entity
@Table(name = "ai_agent_runs")
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "demand_id")
    private Long demandId;

    @Column(name = "requested_by", nullable = false, length = 80)
    private String requestedBy;

    @Column(nullable = false, length = 300)
    private String goal;

    @Column(length = 1000)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiMode mode;

    @Column(length = 80)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "narrative_source", length = 30)
    private NarrativeSource narrativeSource;

    @Column(name = "validation_passed", nullable = false)
    private boolean validationPassed;

    @Column(name = "llm_attempts", nullable = false)
    private int llmAttempts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_issues")
    private String validationIssues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response")
    private String response;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<AgentToolCall> toolCalls = new ArrayList<>();

    protected AgentRun() {
    }

    public AgentRun(Long demandId, String requestedBy, String goal, String question, AiMode mode, String model) {
        this.demandId = demandId;
        this.requestedBy = requestedBy;
        this.goal = goal;
        this.question = question;
        this.mode = mode;
        this.model = model;
        this.status = AgentRunStatus.RUNNING;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void addToolCall(int sequenceNo, String toolName, String argumentsJson, String summary, boolean success,
                            long durationMs) {
        toolCalls.add(new AgentToolCall(this, sequenceNo, toolName, argumentsJson, summary, success, durationMs));
    }

    public void complete(AgentRunStatus status, NarrativeSource narrativeSource, boolean validationPassed,
                         int llmAttempts, String validationIssuesJson, String responseJson, long durationMs) {
        this.status = status;
        this.narrativeSource = narrativeSource;
        this.validationPassed = validationPassed;
        this.llmAttempts = llmAttempts;
        this.validationIssues = validationIssuesJson;
        this.response = responseJson;
        this.durationMs = durationMs;
    }

    public void fail(String errorMessage, long durationMs) {
        this.status = AgentRunStatus.FAILED;
        this.errorMessage = errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 1000));
        this.durationMs = durationMs;
    }

    public void changeMode(AiMode mode, String model) {
        this.mode = mode;
        this.model = model;
    }

    public Long getId() {
        return id;
    }

    public Long getDemandId() {
        return demandId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getGoal() {
        return goal;
    }

    public String getQuestion() {
        return question;
    }

    public AiMode getMode() {
        return mode;
    }

    public String getModel() {
        return model;
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public NarrativeSource getNarrativeSource() {
        return narrativeSource;
    }

    public boolean isValidationPassed() {
        return validationPassed;
    }

    public int getLlmAttempts() {
        return llmAttempts;
    }

    public String getValidationIssues() {
        return validationIssues;
    }

    public String getResponse() {
        return response;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<AgentToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }
}
