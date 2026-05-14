package com.kraft.lotto.feature.winningnumber.application;

public enum UpsertOutcome {
    INSERTED,
    UPDATED,
    UNCHANGED;

    public boolean dataChanged() {
        return this == INSERTED || this == UPDATED;
    }
}
