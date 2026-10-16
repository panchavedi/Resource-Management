package com.talent.server.enums;

/**
 * What happens to a candidate who fails a mandatory skill requirement.
 * EXCLUDE removes them from the ranking (listed under exclusions with the reason);
 * FLAG keeps them in the ranking, marked ineligible and ordered after every eligible candidate.
 */
public enum MandatorySkillPolicy {
    EXCLUDE,
    FLAG
}
