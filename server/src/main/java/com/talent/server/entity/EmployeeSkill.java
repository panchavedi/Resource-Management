package com.talent.server.entity;

import com.talent.server.enums.EvidenceSource;
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
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Stored proficiency (1-5) of an employee in a skill, with the evidence behind it.
 * {@code lastValidatedAt == null} means the level was never validated and must be disclosed as such.
 */
@Entity
@Table(name = "employee_skills")
public class EmployeeSkill extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "proficiency_level", nullable = false)
    private int proficiencyLevel;

    @Column(length = 500)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_source", nullable = false, length = 30)
    private EvidenceSource evidenceSource;

    @Column(name = "last_validated_at")
    private LocalDate lastValidatedAt;

    @Column(name = "validated_by", length = 80)
    private String validatedBy;

    protected EmployeeSkill() {
    }

    public EmployeeSkill(Employee employee, Skill skill, int proficiencyLevel, String evidence,
                         EvidenceSource evidenceSource, LocalDate lastValidatedAt, String validatedBy) {
        this.employee = employee;
        this.skill = skill;
        update(proficiencyLevel, evidence, evidenceSource, lastValidatedAt, validatedBy);
    }

    public final void update(int proficiencyLevel, String evidence, EvidenceSource evidenceSource,
                             LocalDate lastValidatedAt, String validatedBy) {
        this.proficiencyLevel = proficiencyLevel;
        this.evidence = evidence;
        this.evidenceSource = evidenceSource;
        this.lastValidatedAt = lastValidatedAt;
        this.validatedBy = lastValidatedAt == null ? null : validatedBy;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Skill getSkill() {
        return skill;
    }

    public int getProficiencyLevel() {
        return proficiencyLevel;
    }

    public String getEvidence() {
        return evidence;
    }

    public EvidenceSource getEvidenceSource() {
        return evidenceSource;
    }

    public LocalDate getLastValidatedAt() {
        return lastValidatedAt;
    }

    public String getValidatedBy() {
        return validatedBy;
    }
}
