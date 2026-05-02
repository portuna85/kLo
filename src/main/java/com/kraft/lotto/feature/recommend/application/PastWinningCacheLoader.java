package com.kraft.lotto.feature.recommend.application;

import com.kraft.lotto.feature.recommend.domain.PastWinningCache;
import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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

    @EventListener
    public void onCollected(WinningNumbersCollectedEvent event) {
        log.info("PastWinningCache reload triggered: collected={}, skipped={}, failed={}",
                event.collected(), event.skipped(), event.failed());
        reload();
    }

    public void reload() {
        List<LottoCombination> combinations = new ArrayList<>();
        repository.findAllOrderByRoundAsc().forEach(entity ->
                combinations.add(WinningNumberMapper.toDomain(entity).combination())
        );
        cache.replace(combinations);
        log.debug("PastWinningCache loaded size={}", cache.size());
    }
}
