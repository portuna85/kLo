package com.kraft.lotto.infra.health;

import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("lottoApi")
public class LottoApiHealthIndicator implements HealthIndicator {

    private final WinningNumberRepository winningNumberRepository;

    public LottoApiHealthIndicator(WinningNumberRepository winningNumberRepository) {
        this.winningNumberRepository = winningNumberRepository;
    }

    @Override
    public Health health() {
        try {
            Optional<Integer> maxRound = winningNumberRepository.findMaxRound();
            Optional<WinningNumberEntity> latest = winningNumberRepository.findTopByOrderByRoundDesc();
            LocalDateTime lastCollectedAt = latest.map(WinningNumberEntity::getFetchedAt).orElse(null);

            Health.Builder builder = Health.up()
                    .withDetail("latestStoredRound", maxRound.orElse(0));
            if (lastCollectedAt != null) {
                builder.withDetail("lastCollectedAt", lastCollectedAt);
            }
            return builder.build();
        } catch (RuntimeException ex) {
            return Health.down().withDetail("error", "repository_query_failed").build();
        }
    }
}
