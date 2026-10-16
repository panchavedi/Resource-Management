package com.talent.server.entity;

import com.talent.server.enums.LearningFormat;
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

/** Catalogue entry the agent may cite when building a learning path. It never invents courses. */
@Entity
@Table(name = "learning_resources")
public class LearningResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 80)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LearningFormat format;

    @Column(name = "target_level", nullable = false)
    private int targetLevel;

    @Column(name = "duration_hours", nullable = false)
    private int durationHours;

    @Column(length = 300)
    private String url;

    protected LearningResource() {
    }

    public LearningResource(Skill skill, String title, String provider, LearningFormat format, int targetLevel,
                            int durationHours, String url) {
        this.skill = skill;
        this.title = title;
        this.provider = provider;
        this.format = format;
        this.targetLevel = targetLevel;
        this.durationHours = durationHours;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public Skill getSkill() {
        return skill;
    }

    public String getTitle() {
        return title;
    }

    public String getProvider() {
        return provider;
    }

    public LearningFormat getFormat() {
        return format;
    }

    public int getTargetLevel() {
        return targetLevel;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public String getUrl() {
        return url;
    }
}
