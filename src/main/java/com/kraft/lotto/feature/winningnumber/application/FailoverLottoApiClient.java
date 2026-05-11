package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary API call failure triggers temporary fallback API usage.
 */
public class FailoverLottoApiClient implements LottoApiClient {

    private static final Logger log = LoggerFactory.getLogger(FailoverLottoApiClient.class);
    private static final long FALLBACK_COOLDOWN_MS = 5 * 60 * 1000L;

    private final LottoApiClient primary;
    private final LottoApiClient fallback;
    private volatile long fallbackUntil;

    public FailoverLottoApiClient(LottoApiClient primary, LottoApiClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Optional<WinningNumber> fetch(int round) {
        if (System.currentTimeMillis() < fallbackUntil) {
            return fallback.fetch(round);
        }
        try {
            return primary.fetch(round);
        } catch (LottoApiClientException ex) {
            fallbackUntil = System.currentTimeMillis() + FALLBACK_COOLDOWN_MS;
            log.warn("primary lotto api failed, fallback enabled for {}ms: round={}", FALLBACK_COOLDOWN_MS, round, ex);
            return fallback.fetch(round);
        }
    }
}
