package com.talent.server.enums;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a training recommendation. */
public enum TrainingStatus {
    RECOMMENDED,
    IN_PROGRESS,
    COMPLETED,
    DISMISSED;

    public static final Set<TrainingStatus> OPEN = EnumSet.of(RECOMMENDED, IN_PROGRESS);
}
