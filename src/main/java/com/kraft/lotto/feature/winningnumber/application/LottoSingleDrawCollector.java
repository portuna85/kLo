package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchStatus;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
class LottoSingleDrawCollector {

    private static final Logger log = LoggerFactory.getLogger(LottoSingleDrawCollector.class);

    private final LottoApiClient lottoApiClient;
    private final WinningNumberRepository winningNumberRepository;
    private final WinningNumberPersister persister;
    private final LottoFetchLogRepository fetchLogRepository;
    private final Clock clock;

    LottoSingleDrawCollector(LottoApiClient lottoApiClient,
                             WinningNumberRepository winningNumberRepository,
                             WinningNumberPersister persister,
                             LottoFetchLogRepository fetchLogRepository,
                             Clock clock) {
        this.lottoApiClient = lottoApiClient;
        this.winningNumberRepository = winningNumberRepository;
        this.persister = persister;
        this.fetchLogRepository = fetchLogRepository;
        this.clock = clock;
    }

    CollectResponse collectOne(int drwNo, boolean refresh) {
        if (!refresh && winningNumberRepository.existsByRound(drwNo)) {
            saveLog(drwNo, LottoFetchStatus.SKIPPED, "already collected round", null, null);
            return response(CollectResponse.ofSkipped(1, winningNumberRepository.findMaxRound().orElse(0)));
        }
        try {
            Optional<WinningNumber> fetched = lottoApiClient.fetch(drwNo);
            if (fetched.isEmpty()) {
                saveLog(drwNo, LottoFetchStatus.FAILED, "round not drawn yet", null, null);
                return response(CollectResponse.ofFailed(List.of(drwNo), winningNumberRepository.findMaxRound().orElse(0), true));
            }
            UpsertOutcome outcome = persister.upsert(fetched.get());
            String message = switch (outcome) {
                case INSERTED -> "inserted";
                case UPDATED -> "updated";
                case UNCHANGED -> "unchanged";
            };
            saveLog(drwNo, LottoFetchStatus.SUCCESS, message, null, fetched.get().rawJson());
            return switch (outcome) {
                case INSERTED -> response(CollectResponse.ofInserted(1, winningNumberRepository.findMaxRound().orElse(0)));
                case UPDATED -> response(CollectResponse.ofUpdated(1, winningNumberRepository.findMaxRound().orElse(0)));
                case UNCHANGED -> response(CollectResponse.ofSkipped(1, winningNumberRepository.findMaxRound().orElse(0)));
            };
        } catch (LottoApiClientException ex) {
            log.warn("lotto draw collect failed: drwNo={}", drwNo, ex);
            saveLog(drwNo, LottoFetchStatus.FAILED, ex.getMessage(), ex.getResponseCode(), ex.getRawResponse());
            return response(CollectResponse.ofFailed(List.of(drwNo), winningNumberRepository.findMaxRound().orElse(0), false));
        } catch (RuntimeException ex) {
            log.warn("lotto draw collect failed: drwNo={}", drwNo, ex);
            saveLog(drwNo, LottoFetchStatus.FAILED, ex.getMessage(), null, null);
            return response(CollectResponse.ofFailed(List.of(drwNo), winningNumberRepository.findMaxRound().orElse(0), false));
        }
    }

    private CollectResponse response(CollectResponse response) {
        return response;
    }

    private void saveLog(int drwNo, LottoFetchStatus status, String message, Integer responseCode, String rawResponse) {
        String rawResponseToSave = status == LottoFetchStatus.SUCCESS ? null : rawResponse;
        fetchLogRepository.save(new LottoFetchLogEntity(
                drwNo,
                status,
                message,
                responseCode,
                rawResponseToSave,
                LocalDateTime.now(clock)
        ));
    }
}
