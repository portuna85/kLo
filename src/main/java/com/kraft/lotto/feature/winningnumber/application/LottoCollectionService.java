package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import org.springframework.stereotype.Service;

@Service
public class LottoCollectionService {

    private final LottoCollectionCommandService commands;

    public LottoCollectionService(LottoCollectionCommandService commands) {
        this.commands = commands;
    }

    public CollectResponse collectDraw(int drwNo) { return commands.collectDraw(drwNo); }
    public CollectResponse collect(Integer targetRound) { return commands.collect(targetRound); }
    public CollectResponse collectNextDraw() { return commands.collectNextDraw(); }
    public CollectResponse collectMissingDraws() { return commands.collectMissingDraws(); }
    public CollectResponse backfill(int from, int to) { return commands.backfill(from, to); }
    public CollectResponse refreshDraw(int drwNo) { return commands.refreshDraw(drwNo); }
}
