package com.talent.server.entity;

import com.talent.server.enums.RecommendationSource;
import com.talent.server.enums.TrainingStatus;
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

/** A suggested learning action that closes a skill gap for an employee. */
@Entity
@Table(name = "training_recommendations")
public class TrainingRecommendation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demand_id")
    private ProjectDemand demand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_resource_id")
    private LearningResource learningResource;

    @Column(name = "current_level")
    private Integer currentLevel;

    @Column(name = "target_level", nullable = false)
    private int targetLevel;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingStatus status = TrainingStatus.RECOMMENDED;

    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;

    protected TrainingRecommendation() {
    }

    public TrainingRecommendation(Employee employee, Skill skill, ProjectDemand demand, LearningResource learningResource,
                                  Integer currentLevel, int targetLevel, String reason, RecommendationSource source,
                                  String createdBy) {
        this.employee = employee;
        this.skill = skill;
        this.demand = demand;
        this.learningResource = learningResource;
        this.currentLevel = currentLevel;
        this.targetLevel = targetLevel;
        this.reason = reason;
        this.source = source;
        this.createdBy = createdBy;
    }

    public void changeStatus(TrainingStatus newStatus) {
        this.status = newStatus;
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

    public ProjectDemand getDemand() {
        return demand;
    }

    public LearningResource getLearningResource() {
        return learningResource;
    }

    public Integer getCurrentLevel() {
        return currentLevel;
    }

    public int getTargetLevel() {
        return targetLevel;
    }

    public String getReason() {
        return reason;
    }

    public RecommendationSource getSource() {
        return source;
    }

    public TrainingStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
