package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import org.springframework.stereotype.Service;

@Service
public class LottoCollectionService {

    private final LottoCollectionCommandService commands;

    public LottoCollectionService(LottoCollectionCommandService commands) {
        this.commands = commands;
    }

    public CollectResponse collectNextIfNeeded() {
        return commands.collectNextIfNeeded();
    }

    public CollectResponse collectMissingOnce() {
        return commands.collectMissingOnce();
    }
}