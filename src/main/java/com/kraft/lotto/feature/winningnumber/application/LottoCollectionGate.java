package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ApplicationEventPublisher;
class LottoCollectionGate {

    private final ApplicationEventPublisher eventPublisher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    LottoCollectionGate(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    CollectResponse run(CollectionTask task) {
        if (!running.compareAndSet(false, true)) {
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
            running.set(false);
        }
    }

    @FunctionalInterface
    interface CollectionTask {
        CollectResponse run();
    }
}
