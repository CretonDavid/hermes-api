package com.untitled.hermesapi.model.enums;

import java.time.LocalDate;

public enum RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    /**
     * Calcule la date de la n-ième occurrence à partir de la date de départ.
     * Le calcul part toujours de la date initiale pour éviter la dérive des fins de mois
     * (un 31 janvier donne un 31 mars et non un 28 mars).
     */
    public LocalDate occurrence(LocalDate start, int interval, int index) {
        long steps = (long) interval * index;
        return switch (this) {
            case DAILY -> start.plusDays(steps);
            case WEEKLY -> start.plusWeeks(steps);
            case MONTHLY -> start.plusMonths(steps);
            case YEARLY -> start.plusYears(steps);
        };
    }
}
