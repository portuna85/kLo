package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchStatus;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class LottoCollectionService {

    private static final Logger log = LoggerFactory.getLogger(LottoCollectionService.class);

    private final LottoApiClient lottoApiClient;
    private final WinningNumberRepository winningNumberRepository;
    private final WinningNumberPersister persister;
    private final LottoFetchLogRepository fetchLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final long backfillDelayMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public LottoCollectionService(LottoApiClient lottoApiClient,
                                  WinningNumberRepository winningNumberRepository,
                                  WinningNumberPersister persister,
                                  LottoFetchLogRepository fetchLogRepository,
                                  ApplicationEventPublisher eventPublisher,
                                  @Value("${kraft.lotto.api.backfill-delay-ms:${kraft.api.retry-backoff-ms:700}}") long backfillDelayMs) {
        this(lottoApiClient, winningNumberRepository, persister, fetchLogRepository, eventPublisher,
                Clock.systemDefaultZone(), backfillDelayMs);
    }

    LottoCollectionService(LottoApiClient lottoApiClient,
                           WinningNumberRepository winningNumberRepository,
                           WinningNumberPersister persister,
                           LottoFetchLogRepository fetchLogRepository,
                           ApplicationEventPublisher eventPublisher,
                           Clock clock,
                           long backfillDelayMs) {
        this.lottoApiClient = lottoApiClient;
        this.winningNumberRepository = winningNumberRepository;
        this.persister = persister;
        this.fetchLogRepository = fetchLogRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.backfillDelayMs = Math.max(0, backfillDelayMs);
    }

    public CollectResponse collectDraw(int drwNo) {
        validateRound(drwNo);
        return guarded(() -> collectOne(drwNo, false));
    }

    public CollectResponse collect(Integer targetRound) {
        if (targetRound == null) {
            return collectNextDraw();
        }
        validateRound(targetRound);
        return guarded(() -> {
            int latestRound = winningNumberRepository.findMaxRound().orElse(0);
            if (targetRound <= latestRound) {
                return new CollectResponse(0, 0, 1, 0, latestRound, List.of(), false, null, false, false);
            }
            List<Integer> rounds = new ArrayList<>();
            for (int round = latestRound + 1; round <= targetRound; round++) {
                rounds.add(round);
            }
            return collectRange(rounds, false, true);
        });
    }

    public CollectResponse collectNextDraw() {
        return guarded(() -> {
            int nextRound = winningNumberRepository.findMaxRound().orElse(0) + 1;
            return collectOne(nextRound, false);
        });
    }

    public CollectResponse collectMissingDraws() {
        return guarded(() -> {
            int maxRound = winningNumberRepository.findMaxRound().orElse(0);
            if (maxRound <= 0) {
                return response(0, 0, 0, List.of(), false, null, false);
            }
            Set<Integer> existingRounds = winningNumberRepository.findRoundsBetween(1, maxRound);
            List<Integer> missingRounds = new ArrayList<>();
            for (int round = 1; round <= maxRound; round++) {
                if (!existingRounds.contains(round)) {
                    missingRounds.add(round);
                }
            }
            return collectRange(missingRounds, false, true);
        });
    }

    public CollectResponse backfill(int from, int to) {
        validateRange(from, to);
        return guarded(() -> {
            List<Integer> rounds = new ArrayList<>();
            for (int round = from; round <= to; round++) {
                rounds.add(round);
            }
            return collectRange(rounds, false, true);
        });
    }

    public CollectResponse refreshDraw(int drwNo) {
        validateRound(drwNo);
        return guarded(() -> collectOne(drwNo, true));
    }

    private CollectResponse collectRange(List<Integer> rounds, boolean refresh, boolean delayBetweenCalls) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        List<Integer> failedRounds = new ArrayList<>();
        boolean firstCall = true;
        for (Integer round : rounds) {
            if (!firstCall && delayBetweenCalls) {
                sleepBackfillDelay();
            }
            firstCall = false;
            CollectResponse one = collectOne(round, refresh);
            inserted += one.collected();
            updated += one.updated();
            skipped += one.skipped();
            failedRounds.addAll(one.failedRounds());
        }
        return response(inserted, updated, skipped, failedRounds, false, null, false);
    }

    private CollectResponse collectOne(int drwNo, boolean refresh) {
        if (!refresh && winningNumberRepository.existsByRound(drwNo)) {
            saveLog(drwNo, LottoFetchStatus.SKIPPED, "already collected round", null, null);
            return response(0, 0, 1, List.of(), false, null, false);
        }
        try {
            Optional<WinningNumber> fetched = lottoApiClient.fetch(drwNo);
            if (fetched.isEmpty()) {
                saveLog(drwNo, LottoFetchStatus.FAILED, "round not drawn yet", null, null);
                return response(0, 0, 0, List.of(drwNo), false, null, true);
            }
            UpsertOutcome outcome = persister.upsert(fetched.get());
            String message = switch (outcome) {
                case INSERTED -> "inserted";
                case UPDATED -> "updated";
                case UNCHANGED -> "unchanged";
            };
            saveLog(drwNo, LottoFetchStatus.SUCCESS, message, null, fetched.get().rawJson());
            return switch (outcome) {
                case INSERTED -> response(1, 0, 0, List.of(), false, null, false);
                case UPDATED -> response(0, 1, 0, List.of(), false, null, false);
                case UNCHANGED -> response(0, 0, 1, List.of(), false, null, false);
            };
        } catch (LottoApiClientException ex) {
            log.warn("lotto draw collect failed: drwNo={}", drwNo, ex);
            saveLog(drwNo, LottoFetchStatus.FAILED, ex.getMessage(), ex.getResponseCode(), ex.getRawResponse());
            return response(0, 0, 0, List.of(drwNo), false, null, false);
        } catch (RuntimeException ex) {
            log.warn("lotto draw collect failed: drwNo={}", drwNo, ex);
            saveLog(drwNo, LottoFetchStatus.FAILED, ex.getMessage(), null, null);
            return response(0, 0, 0, List.of(drwNo), false, null, false);
        }
    }

    private CollectResponse guarded(CollectionTask task) {
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

    private CollectResponse response(int collected,
                                     int updated,
                                     int skipped,
                                     List<Integer> failedRounds,
                                     boolean truncated,
                                     Integer nextRound,
                                     boolean notDrawn) {
        int latestRound = winningNumberRepository.findMaxRound().orElse(0);
        return new CollectResponse(collected, updated, skipped, failedRounds.size(), latestRound,
                failedRounds, truncated, nextRound, notDrawn, (collected + updated) > 0);
    }

    private void validateRange(int from, int to) {
        validateRound(from);
        validateRound(to);
        if (from > to) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND,
                    "backfill start round must be less than or equal to end round");
        }
    }

    private void validateRound(int round) {
        if (round <= 0) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
        }
    }

    private void sleepBackfillDelay() {
        if (backfillDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(backfillDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILURE, "backfill delay interrupted", ex);
        }
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

    @FunctionalInterface
    private interface CollectionTask {
        CollectResponse run();
    }
}
