package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary API call failure triggers temporary fallback API usage.
 */
public class FailoverLottoApiClient implements LottoApiClient {

    private static final Logger log = LoggerFactory.getLogger(FailoverLottoApiClient.class);
    private final LottoApiClient primary;
    private final LottoApiClient fallback;
    private final CircuitBreaker circuitBreaker;

    public FailoverLottoApiClient(LottoApiClient primary, LottoApiClient fallback) {
        this(primary, fallback, defaultCircuitBreaker());
    }

    FailoverLottoApiClient(LottoApiClient primary, LottoApiClient fallback, CircuitBreaker circuitBreaker) {
        this.primary = primary;
        this.fallback = fallback;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Optional<WinningNumber> fetch(int round) {
        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("primary lotto api blocked by open circuit, using fallback: round={}", round);
            return fallback.fetch(round);
        }
        try {
            Optional<WinningNumber> response = primary.fetch(round);
            circuitBreaker.onSuccess(0, TimeUnit.NANOSECONDS);
            return response;
        } catch (CallNotPermittedException ex) {
            log.warn("primary lotto api call not permitted, using fallback: round={}", round);
            return fallback.fetch(round);
        } catch (LottoApiClientException ex) {
            circuitBreaker.onError(0, TimeUnit.NANOSECONDS, ex);
            log.warn("primary lotto api failed, using fallback: round={}", round, ex);
            return fallback.fetch(round);
        }
    }

    private static CircuitBreaker defaultCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(2)
                .slidingWindowSize(4)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMinutes(5))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(ex -> ex instanceof LottoApiClientException)
                .build();
        return CircuitBreaker.of("lottoApiPrimary", config);
    }
}
