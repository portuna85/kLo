package com.kraft.lotto.infra.health;

import com.kraft.lotto.feature.winningnumber.application.LottoApiClient;
import com.kraft.lotto.feature.winningnumber.application.LottoApiClientException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("lottoApi")
public class LottoApiHealthIndicator implements HealthIndicator {

    private final LottoApiClient lottoApiClient;

    public LottoApiHealthIndicator(LottoApiClient lottoApiClient) {
        this.lottoApiClient = lottoApiClient;
    }

    @Override
    public Health health() {
        try {
            lottoApiClient.fetch(1);
            return Health.up().build();
        } catch (LottoApiClientException ex) {
            return Health.down().withDetail("error", ex.getMessage()).build();
        }
    }
}
