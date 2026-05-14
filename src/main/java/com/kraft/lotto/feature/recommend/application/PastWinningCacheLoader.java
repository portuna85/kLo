package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.recommend.domain.PastWinningCache;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * PastWinningCache를 Repository로부터 적재/갱신하는 어댑터.
 * 도메인 캐시(POJO)에 Spring/JPA 의존성을 분리시키기 위한 application 계층 컴포넌트.
 */
@Component
public class PastWinningCacheLoader {

    private static final Logger log = LoggerFactory.getLogger(PastWinningCacheLoader.class);

    private final PastWinningCache cache;
    private final WinningNumberRepository repository;

    public PastWinningCacheLoader(PastWinningCache cache, WinningNumberRepository repository) {
        this.cache = cache;
        this.repository = repository;
    }

    @PostConstruct
    public void initialize() {
        reload();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollected(WinningNumbersCollectedEvent event) {
        log.info("PastWinningCache reload triggered: collected={}, skipped={}, failed={}",
                event.collected(), event.skipped(), event.failed());
        reload();
    }

    public void reload() {
        List<LottoCombination> combinations = new ArrayList<>();
        int[] invalidRows = {0};
        repository.findAllCombinationsOrderByRoundAsc().forEach(row -> {
            try {
                if (row.getN1() == null || row.getN2() == null || row.getN3() == null
                        || row.getN4() == null || row.getN5() == null || row.getN6() == null) {
                    invalidRows[0]++;
                    return;
                }
                combinations.add(new LottoCombination(List.of(
                        row.getN1(), row.getN2(), row.getN3(),
                        row.getN4(), row.getN5(), row.getN6()
                )));
            } catch (RuntimeException ex) {
                invalidRows[0]++;
                log.warn("Skipping invalid winning number combination row while loading cache", ex);
            }
        });
        cache.replace(combinations);
        if (invalidRows[0] > 0) {
            log.warn("PastWinningCache loaded with invalid rows skipped: skipped={}, loaded={}",
                    invalidRows[0], cache.size());
        } else {
            log.debug("PastWinningCache loaded size={}", cache.size());
        }
    }
}
