package com.kraft.lotto.feature.winningnumber.application;

public enum UpsertOutcome {
    INSERTED,
    UPDATED,
    UNCHANGED,
    FAILED;

    public boolean dataChanged() {
        return this == INSERTED || this == UPDATED;
    }
}
