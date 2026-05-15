package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.context.ApplicationEventPublisher;
class LottoCollectionGate {

    private static final String LOCK_NAME = "lotto_collect_gate";
    private final ApplicationEventPublisher eventPublisher;
    private final LockProvider lockProvider;
    private final Duration lockAtMostFor;
    private final Duration lockAtLeastFor;

    LottoCollectionGate(ApplicationEventPublisher eventPublisher,
                       LockProvider lockProvider,
                       Duration lockAtMostFor,
                       Duration lockAtLeastFor) {
        this.eventPublisher = eventPublisher;
        this.lockProvider = lockProvider;
        this.lockAtMostFor = lockAtMostFor;
        this.lockAtLeastFor = lockAtLeastFor;
    }

    CollectResponse run(CollectionTask task) {
        LockConfiguration lockConfiguration = new LockConfiguration(
                Instant.now(),
                LOCK_NAME,
                lockAtMostFor,
                lockAtLeastFor
        );
        SimpleLock lock = lockProvider.lock(lockConfiguration).orElse(null);
        if (lock == null) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "lotto collect job is already running");
        }
        try {
            CollectResponse response = task.run();
            if (response.dataChanged()) {
                eventPublisher.publishEvent(
                        WinningNumbersCollectedEvent.of(
                                response.collected(),
                                response.updated(),
                                response.skipped(),
                                response.failed())
                );
            }
            return response;
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    interface CollectionTask {
        CollectResponse run();
    }
}
