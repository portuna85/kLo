package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LottoCollectionService {

    private static final Logger log = LoggerFactory.getLogger(LottoCollectionService.class);

    private final LottoApiClient lottoApiClient;
    private final WinningNumberRepository winningNumberRepository;
    private final WinningNumberPersister persister;
    private final LottoFetchLogRepository fetchLogRepository;
    private final Clock clock;
    private final long backfillDelayMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LottoCollectionService(LottoApiClient lottoApiClient,
                                  WinningNumberRepository winningNumberRepository,
                                  WinningNumberPersister persister,
                                  LottoFetchLogRepository fetchLogRepository,
                                  @Value("${kraft.lotto.api.backfill-delay-ms:${kraft.api.retry-backoff-ms:700}}") long backfillDelayMs) {
        this(lottoApiClient, winningNumberRepository, persister, fetchLogRepository,
                Clock.systemDefaultZone(), backfillDelayMs);
    }

    LottoCollectionService(LottoApiClient lottoApiClient,
                           WinningNumberRepository winningNumberRepository,
                           WinningNumberPersister persister,
                           LottoFetchLogRepository fetchLogRepository,
                           Clock clock,
                           long backfillDelayMs) {
        this.lottoApiClient = lottoApiClient;
        this.winningNumberRepository = winningNumberRepository;
        this.persister = persister;
        this.fetchLogRepository = fetchLogRepository;
        this.clock = clock;
        this.backfillDelayMs = Math.max(0, backfillDelayMs);
    }

    public CollectResponse collectDraw(int drwNo) {
        validateRound(drwNo);
        return guarded(() -> collectOne(drwNo, false));
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
                return response(0, 0, List.of(), false, null, false);
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
        int collected = 0;
        int skipped = 0;
        List<Integer> failedRounds = new ArrayList<>();
        boolean firstCall = true;
        for (Integer round : rounds) {
            if (!firstCall && delayBetweenCalls) {
                sleepBackfillDelay();
            }
            firstCall = false;
            CollectResponse one = collectOne(round, refresh);
            collected += one.collected();
            skipped += one.skipped();
            failedRounds.addAll(one.failedRounds());
        }
        return response(collected, skipped, failedRounds, false, null, false);
    }

    private CollectResponse collectOne(int drwNo, boolean refresh) {
        if (!refresh && winningNumberRepository.existsByRound(drwNo)) {
            saveLog(drwNo, LottoFetchStatus.SKIPPED, "이미 저장된 회차입니다.", null, null);
            return response(0, 1, List.of(), false, null, false);
        }
        try {
            Optional<WinningNumber> fetched = lottoApiClient.fetch(drwNo);
            if (fetched.isEmpty()) {
                saveLog(drwNo, LottoFetchStatus.FAILED, "API가 정상 회차 데이터를 반환하지 않았습니다.", null, null);
                return response(0, 0, List.of(drwNo), false, null, true);
            }
            boolean inserted = persister.upsert(fetched.get());
            saveLog(drwNo, LottoFetchStatus.SUCCESS, inserted ? "수집 저장 완료" : "수집 갱신 완료", null, fetched.get().rawJson());
            return response(inserted ? 1 : 0, inserted ? 0 : 1, List.of(), false, null, false);
        } catch (LottoApiClientException ex) {
            log.warn("로또 회차 수집 실패: drwNo={}", drwNo, ex);
            saveLog(drwNo, LottoFetchStatus.FAILED, ex.getMessage(), ex.getResponseCode(), ex.getRawResponse());
            return response(0, 0, List.of(drwNo), false, null, false);
        } catch (RuntimeException ex) {
            log.warn("로또 회차 수집 실패: drwNo={}", drwNo, ex);
            saveLog(drwNo, LottoFetchStatus.FAILED, ex.getMessage(), null, null);
            return response(0, 0, List.of(drwNo), false, null, false);
        }
    }

    private CollectResponse guarded(CollectionTask task) {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "로또 회차 수집이 이미 실행 중입니다.");
        }
        try {
            return task.run();
        } finally {
            running.set(false);
        }
    }

    private CollectResponse response(int collected,
                                     int skipped,
                                     List<Integer> failedRounds,
                                     boolean truncated,
                                     Integer nextRound,
                                     boolean notDrawn) {
        int latestRound = winningNumberRepository.findMaxRound().orElse(0);
        return new CollectResponse(collected, skipped, failedRounds.size(), latestRound,
                failedRounds, truncated, nextRound, notDrawn);
    }

    private void validateRange(int from, int to) {
        validateRound(from);
        validateRound(to);
        if (from > to) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND, "백필 시작 회차는 종료 회차보다 클 수 없습니다.");
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
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILURE, "백필 호출 간격 대기 중 인터럽트가 발생했습니다.", ex);
        }
    }

    private void saveLog(int drwNo, LottoFetchStatus status, String message, Integer responseCode, String rawResponse) {
        fetchLogRepository.save(new LottoFetchLogEntity(
                drwNo,
                status,
                message,
                responseCode,
                rawResponse,
                LocalDateTime.now(clock)
        ));
    }

    @FunctionalInterface
    private interface CollectionTask {
        CollectResponse run();
    }
}
