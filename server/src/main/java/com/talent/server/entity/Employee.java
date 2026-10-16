package com.talent.server.entity;

import com.talent.server.enums.EmploymentStatus;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * An employee who can be staffed on project demands. Only facts needed for staffing are stored;
 * no protected or sensitive characteristics are modelled.
 */
@Entity
@Table(name = "employees")
public class Employee extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 20)
    private String employeeCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(nullable = false, length = 80)
    private String department;

    @Column(length = 80)
    private String location;

    @Column(name = "remote_eligible", nullable = false)
    private boolean remoteEligible;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 20)
    private EmploymentStatus employmentStatus;

    @Column(name = "capacity_percent", nullable = false)
    private int capacityPercent = 100;

    @Version
    private long version;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeSkill> skills = new ArrayList<>();

    protected Employee() {
    }

    public Employee(String employeeCode, String name, String email, String jobTitle, String department,
                    String location, boolean remoteEligible, EmploymentStatus employmentStatus, int capacityPercent) {
        this.employeeCode = employeeCode;
        this.name = name;
        this.email = email;
        this.jobTitle = jobTitle;
        this.department = department;
        this.location = location;
        this.remoteEligible = remoteEligible;
        this.employmentStatus = employmentStatus;
        this.capacityPercent = capacityPercent;
    }

    public Optional<EmployeeSkill> findSkill(Long skillId) {
        return skills.stream().filter(s -> s.getSkill().getId().equals(skillId)).findFirst();
    }

    public EmployeeSkill addSkill(EmployeeSkill skill) {
        skills.add(skill);
        return skill;
    }

    public boolean removeSkill(Long skillId) {
        return skills.removeIf(s -> s.getSkill().getId().equals(skillId));
    }

    public boolean isAssignable() {
        return employmentStatus.isAssignable();
    }

    public void changeStatus(EmploymentStatus status) {
        this.employmentStatus = status;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getDepartment() {
        return department;
    }

    public String getLocation() {
        return location;
    }

    public boolean isRemoteEligible() {
        return remoteEligible;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public int getCapacityPercent() {
        return capacityPercent;
    }

    public long getVersion() {
        return version;
    }

    public List<EmployeeSkill> getSkills() {
        return Collections.unmodifiableList(skills);
    }
}
