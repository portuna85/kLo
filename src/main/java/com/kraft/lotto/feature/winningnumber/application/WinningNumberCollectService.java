package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 외부 API에서 당첨번호를 끌어와 DB에 저장하는 application 서비스.
 * <p>
 * 동작 규칙:
 * <ol>
 *   <li>시작 회차는 {@code max(round) + 1} (DB 비어 있으면 1).</li>
 *   <li>종료 회차는 {@code targetRound}가 지정되면 그 회차까지, 아니면 외부 API가 미추첨(empty)을 줄 때까지.</li>
 *   <li>이미 저장된 회차는 skip 카운트 증가 후 다음으로 진행.</li>
 *   <li>도메인 검증/저장 단계 실패는 failed 카운트 증가 후 다음 회차로 진행 (전체 트랜잭션 롤백 X).</li>
 *   <li>외부 API 자체가 실패하면 부분 진행 결과를 잃지 않기 위해 즉시 {@link BusinessException}({@code EXTERNAL_API_FAILURE})으로 종료.</li>
 *   <li>최종적으로 {@link WinningNumbersCollectedEvent}를 발행한다 (collected==0이어도 발행하여 캐시 reload는 매번 트리거되지 않음 → collected&gt;0일 때만 발행).</li>
 * </ol>
 *
 * 본 서비스는 전체 수집 루프에 트랜잭션을 걸지 않는다.
 * 저장은 WinningNumberPersister가 회차별로 담당한다.
 */
@Service
public class WinningNumberCollectService {

    private static final Logger log = LoggerFactory.getLogger(WinningNumberCollectService.class);

    static final int ABSOLUTE_MAX_ROUNDS_PER_CALL = 5_000;

    private final LottoApiClient lottoApiClient;
    private final WinningNumberRepository repository;
    private final WinningNumberPersister persister;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxRoundsPerCall;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public WinningNumberCollectService(LottoApiClient lottoApiClient,
                                       WinningNumberRepository repository,
                                       WinningNumberPersister persister,
                                       ApplicationEventPublisher eventPublisher) {
        this(lottoApiClient, repository, persister, eventPublisher, ABSOLUTE_MAX_ROUNDS_PER_CALL);
    }

    private WinningNumberCollectService(LottoApiClient lottoApiClient,
                                        WinningNumberRepository repository,
                                        WinningNumberPersister persister,
                                        ApplicationEventPublisher eventPublisher,
                                        int maxRoundsPerCall) {
        this.lottoApiClient = lottoApiClient;
        this.repository = repository;
        this.persister = persister;
        this.eventPublisher = eventPublisher;
        this.maxRoundsPerCall = maxRoundsPerCall;
    }

    WinningNumberCollectService(LottoApiClient lottoApiClient,
                                WinningNumberRepository repository,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock) {
        this(lottoApiClient, repository, new WinningNumberPersister(repository, clock), eventPublisher);
    }

    WinningNumberCollectService(LottoApiClient lottoApiClient,
                                WinningNumberRepository repository,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock,
                                int maxRoundsPerCall) {
        this(lottoApiClient, repository, new WinningNumberPersister(repository, clock), eventPublisher, maxRoundsPerCall);
    }

    public CollectResponse collect(Integer targetRound) {
        if (targetRound != null && targetRound <= 0) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
        }
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "당첨번호 수집이 이미 실행 중입니다.");
        }
        try {
            return doCollect(targetRound);
        } finally {
            running.set(false);
        }
    }

    private CollectResponse doCollect(Integer targetRound) {
        int latestBeforeCollect = repository.findMaxRound().orElse(0);
        if (targetRound != null && targetRound <= latestBeforeCollect) {
            log.info("targetRound already collected: targetRound={}, latestRound={}", targetRound, latestBeforeCollect);
            return new CollectResponse(0, 1, 0, latestBeforeCollect, List.of(), false, null, false);
        }

        int startRound = latestBeforeCollect + 1;
        int collected = 0;
        int skipped = 0;
        List<Integer> failedRounds = new ArrayList<>();

        int processed = 0;
        int round = startRound;
        boolean truncated = false;
        Integer nextRound = null;
        while (processed < maxRoundsPerCall) {
            if (targetRound != null && round > targetRound) {
                break;
            }

            Optional<WinningNumber> fetched;
            try {
                fetched = lottoApiClient.fetch(round);
            } catch (LottoApiClientException ex) {
                log.warn("외부 API 호출 실패: round={}", round, ex);
                if (collected > 0) {
                    eventPublisher.publishEvent(WinningNumbersCollectedEvent.of(collected, skipped, failedRounds.size()));
                }
                throw new BusinessException(ErrorCode.EXTERNAL_API_FAILURE, ex.getMessage(), ex);
            }
            if (fetched.isEmpty()) {
                if (targetRound != null) {
                    // 명시적 targetRound가 미추첨이면 notDrawn=true로 CollectResponse 반환
                    log.info("미추첨 회차 도달(stop): round={}, targetRound={}", round, targetRound);
                    return finishCollect(collected, skipped, failedRounds, false, null, true);
                }
                break;
            }

            try {
                if (repository.existsByRound(round)) {
                    skipped++;
                } else {
                    if (persister.saveIfAbsent(round, fetched.get())) {
                        collected++;
                    } else {
                        skipped++;
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("당첨번호 저장 실패: round={}", round, ex);
                failedRounds.add(round);
            }
            round++;
            processed++;
        }
        // ABSOLUTE_MAX_ROUNDS_PER_CALL 제한에 도달한 경우
        if (processed >= maxRoundsPerCall) {
            truncated = true;
            nextRound = round;
        }
        return finishCollect(collected, skipped, failedRounds, truncated, nextRound, false);
    }

    private CollectResponse finishCollect(int collected,
                                          int skipped,
                                          List<Integer> failedRounds,
                                          boolean truncated,
                                          Integer nextRound,
                                          boolean notDrawn) {
        int latestRound = repository.findMaxRound().orElse(0);
        int failed = failedRounds.size();
        if (collected > 0) {
            eventPublisher.publishEvent(WinningNumbersCollectedEvent.of(collected, skipped, failed));
        }
        log.info("collect summary: collected={}, skipped={}, failed={}, latestRound={}, failedRounds={}, truncated={}, nextRound={}",
                collected, skipped, failed, latestRound, failedRounds, truncated, nextRound);
        return new CollectResponse(collected, skipped, failed, latestRound, List.copyOf(failedRounds), truncated, nextRound, notDrawn);
    }
}
