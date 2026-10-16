package com.talent.server.support;

import com.talent.server.dto.request.CreateAllocationRequest;
import com.talent.server.dto.request.CreateDemandRequest;
import com.talent.server.dto.request.CreateDemandRequest.SkillRequirementRequest;
import com.talent.server.dto.response.AllocationResponse;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.EmployeeSkill;
import com.talent.server.entity.LearningResource;
import com.talent.server.entity.Skill;
import com.talent.server.enums.DemandPriority;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.enums.EvidenceSource;
import com.talent.server.enums.LearningFormat;
import com.talent.server.repository.EmployeeRepository;
import com.talent.server.repository.LearningResourceRepository;
import com.talent.server.repository.SkillRepository;
import com.talent.server.service.AllocationService;
import com.talent.server.service.DemandService;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builders for synthetic test data, going through the real services
 * where rules matter.
 */
@TestComponent
public class TestData {

    public static final LocalDate TODAY = LocalDate.now();

    private final SkillRepository skills;
    private final EmployeeRepository employees;
    private final LearningResourceRepository learningResources;
    private final DemandService demandService;
    private final AllocationService allocationService;
    private final TransactionTemplate tx;

    public TestData(SkillRepository skills, EmployeeRepository employees, LearningResourceRepository learningResources,
            DemandService demandService, AllocationService allocationService, TransactionTemplate tx) {
        this.skills = skills;
        this.employees = employees;
        this.learningResources = learningResources;
        this.demandService = demandService;
        this.allocationService = allocationService;
        this.tx = tx;
    }

    public Skill skill(String name, String category) {
        return skills.save(new Skill(name, category, null));
    }

    public void learningResource(Skill skill, String title, int targetLevel, int hours) {
        learningResources.save(new LearningResource(skill, title, "Test Academy", LearningFormat.COURSE, targetLevel,
                hours, null));
    }

    /**
     * A stored proficiency; {@code validatedDaysAgo == null} means never validated.
     */
    public record Level(Skill skill, int level, Integer validatedDaysAgo) {
    }

    public static Level level(Skill skill, int level) {
        return new Level(skill, level, 30);
    }

    public static Level unvalidated(Skill skill, int level) {
        return new Level(skill, level, null);
    }

    public Employee employee(String code, String name, String location, Level... levels) {
        return employee(code, name, location, EmploymentStatus.ACTIVE, 100, levels);
    }

    public Employee employee(String code, String name, String location, EmploymentStatus status, int capacity,
            Level... levels) {
        return tx.execute(s -> {
            Employee e = new Employee(code, name, code.toLowerCase() + "@example.com", "Engineer", "Engineering",
                    location, true, status, capacity);
            for (Level l : levels) {
                e.addSkill(new EmployeeSkill(e, l.skill(), l.level(), "Synthetic evidence",
                        l.validatedDaysAgo() == null ? EvidenceSource.SELF_DECLARED : EvidenceSource.ASSESSMENT,
                        l.validatedDaysAgo() == null ? null : TODAY.minusDays(l.validatedDaysAgo()), "tester"));
            }
            return employees.save(e);
        });
    }

    public static SkillRequirementRequest requires(Skill skill, int minimum) {
        return new SkillRequirementRequest(skill.getId(), minimum, true);
    }

    public static SkillRequirementRequest optional(Skill skill, int minimum) {
        return new SkillRequirementRequest(skill.getId(), minimum, false);
    }

    public DemandResponse demand(String project, LocalDate start, LocalDate end, int percent,
            SkillRequirementRequest... requirements) {
        return demand(project, "Backend Engineer", "Bengaluru", start, end, percent, 1, requirements);
    }

    public DemandResponse demand(String project, String role, String location, LocalDate start, LocalDate end,
            int percent, int headcount, SkillRequirementRequest... requirements) {
        return demandService.create(new CreateDemandRequest(project, role, null, start, end, location, true, percent,
                headcount, DemandPriority.HIGH, Arrays.asList(requirements)), "manager");
    }

    /** Proposes as "manager" (PENDING_APPROVAL). */
    public AllocationResponse propose(Long employeeId, Long demandId, LocalDate start, LocalDate end, int percent) {
        return allocationService.propose(new CreateAllocationRequest(employeeId, demandId, start, end, percent, null),
                null, "manager").allocation();
    }

    /** Proposes as "manager" and approves as "approver". */
    public AllocationResponse approved(Long employeeId, Long demandId, LocalDate start, LocalDate end, int percent) {
        AllocationResponse pending = propose(employeeId, demandId, start, end, percent);
        return allocationService.approve(pending.id(), "approved in test", "approver");
    }

    public List<Skill> allSkills() {
        return skills.findAll();
    }
}
