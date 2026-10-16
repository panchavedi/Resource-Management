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

/** A skill requirement of a demand: minimum proficiency and whether it is mandatory. */
@Entity
@Table(name = "demand_skills")
public class DemandSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id", nullable = false)
    private ProjectDemand demand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "minimum_proficiency", nullable = false)
    private int minimumProficiency;

    @Column(nullable = false)
    private boolean mandatory;

    protected DemandSkill() {
    }

    DemandSkill(ProjectDemand demand, Skill skill, int minimumProficiency, boolean mandatory) {
        this.demand = demand;
        this.skill = skill;
        this.minimumProficiency = minimumProficiency;
        this.mandatory = mandatory;
    }

    public Long getId() {
        return id;
    }

    public ProjectDemand getDemand() {
        return demand;
    }

    public Skill getSkill() {
        return skill;
    }

    public int getMinimumProficiency() {
        return minimumProficiency;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
