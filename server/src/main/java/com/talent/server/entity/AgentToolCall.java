package com.talent.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One controlled tool invocation made by the staffing agent during a run. */
@Entity
@Table(name = "ai_tool_calls")
public class AgentToolCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private AgentRun run;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "tool_name", nullable = false, length = 60)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments")
    private String arguments;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    protected AgentToolCall() {
    }

    AgentToolCall(AgentRun run, int sequenceNo, String toolName, String arguments, String summary, boolean success,
                  long durationMs) {
        this.run = run;
        this.sequenceNo = sequenceNo;
        this.toolName = toolName;
        this.arguments = arguments;
        this.summary = summary == null ? null : summary.substring(0, Math.min(summary.length(), 1000));
        this.success = success;
        this.durationMs = durationMs;
    }

    public Long getId() {
        return id;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
