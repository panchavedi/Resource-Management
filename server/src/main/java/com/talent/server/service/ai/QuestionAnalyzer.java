package com.talent.server.service.ai;

import com.talent.server.entity.Employee;
import com.talent.server.entity.Skill;
import com.talent.server.repository.EmployeeRepository;
import com.talent.server.repository.SkillRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministically resolves which employees and skills a free-text question refers to, and whether
 * it asks the agent to assume or invent data. Resolution is exact (full name, employee code, or a
 * first name that is unique in the directory); nothing is guessed.
 */
@Component
@Transactional(readOnly = true)
public class QuestionAnalyzer {

    private static final Pattern FABRICATION = Pattern.compile(
            "(?i)\\b(assume|assuming|pretend|invent|make up|made up|fabricate|guess|imagine|hypothetical(?:ly)?|"
                    + "just say|say that|even if|estimate|suppose|treat .{1,40} as)\\b");

    private final EmployeeRepository employeeRepository;
    private final SkillRepository skillRepository;

    public QuestionAnalyzer(EmployeeRepository employeeRepository, SkillRepository skillRepository) {
        this.employeeRepository = employeeRepository;
        this.skillRepository = skillRepository;
    }

    public record EmployeeMention(Long employeeId, String employeeCode, String name) {
    }

    public record QuestionEntities(List<EmployeeMention> employees, List<String> skills, boolean fabricationRequested) {

        public static QuestionEntities none() {
            return new QuestionEntities(List.of(), List.of(), false);
        }
    }

    public QuestionEntities analyze(String question) {
        if (question == null || question.isBlank()) {
            return QuestionEntities.none();
        }
        List<Employee> directory = employeeRepository.findAll();
        Map<String, Long> firstNameCounts = directory.stream()
                .collect(Collectors.groupingBy(e -> firstName(e.getName()), Collectors.counting()));
        List<EmployeeMention> employees = directory.stream()
                .filter(e -> mentions(question, e.getName()) || mentions(question, e.getEmployeeCode())
                        || (firstName(e.getName()).length() >= 3 && firstNameCounts.get(firstName(e.getName())) == 1
                        && mentions(question, firstName(e.getName()))))
                .map(e -> new EmployeeMention(e.getId(), e.getEmployeeCode(), e.getName()))
                .limit(5)
                .toList();
        List<String> skills = skillRepository.findAll().stream()
                .map(Skill::getName)
                .filter(name -> mentions(question, name))
                .toList();
        return new QuestionEntities(employees, skills, FABRICATION.matcher(question).find());
    }

    public List<String> directoryNames() {
        return employeeRepository.findAll().stream().map(Employee::getName).toList();
    }

    public List<String> skillNames() {
        return skillRepository.findAll().stream().map(Skill::getName).toList();
    }

    private static String firstName(String name) {
        return name.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    static boolean mentions(String text, String term) {
        return term != null && !term.isBlank()
                && Pattern.compile("(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(term.trim()) + "(?![\\p{L}\\p{N}])")
                .matcher(text).find();
    }
}
