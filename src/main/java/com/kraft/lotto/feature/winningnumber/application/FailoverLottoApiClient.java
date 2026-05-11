package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary API 호출 실패 시 fallback API로 재시도하는 클라이언트.
 */
public class FailoverLottoApiClient implements LottoApiClient {

    private static final Logger log = LoggerFactory.getLogger(FailoverLottoApiClient.class);

    private final LottoApiClient primary;
    private final LottoApiClient fallback;
    private volatile boolean fallbackActivated;

    public FailoverLottoApiClient(LottoApiClient primary, LottoApiClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Optional<WinningNumber> fetch(int round) {
        if (fallbackActivated) {
            return fallback.fetch(round);
        }
        try {
            return primary.fetch(round);
        } catch (LottoApiClientException ex) {
            fallbackActivated = true;
            log.warn("primary lotto api failed, fallback mode activated: round={}", round, ex);
            return fallback.fetch(round);
        }
    }
}
