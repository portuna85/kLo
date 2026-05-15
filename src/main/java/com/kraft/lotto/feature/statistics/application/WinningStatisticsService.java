package com.kraft.lotto.feature.statistics.application;

import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.statistics.infrastructure.WinningNumberFrequencySummaryEntity;
import com.kraft.lotto.feature.statistics.infrastructure.WinningNumberFrequencySummaryRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CombinationPrizeHistoryDto;
import com.kraft.lotto.feature.winningnumber.web.dto.CombinationPrizeHitDto;
import com.kraft.lotto.feature.winningnumber.web.dto.FrequencySummaryDto;
import com.kraft.lotto.feature.winningnumber.web.dto.NumberFrequencyDto;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class WinningStatisticsService {

    private final WinningNumberRepository repository;
    private final WinningNumberFrequencySummaryRepository summaryRepository;
    private final MeterRegistry meterRegistry;
    @Lazy
    @Autowired(required = false)
    private WinningStatisticsService self;

    public WinningStatisticsService(WinningNumberRepository repository) {
        this(repository, null, null);
    }

    @Autowired
    public WinningStatisticsService(WinningNumberRepository repository,
                                    WinningNumberFrequencySummaryRepository summaryRepository) {
        this(repository, summaryRepository, null);
    }

    WinningStatisticsService(WinningNumberRepository repository,
                             WinningNumberFrequencySummaryRepository summaryRepository,
                             MeterRegistry meterRegistry) {
        this.repository = repository;
        this.summaryRepository = summaryRepository;
        this.meterRegistry = meterRegistry;
    }

    @Cacheable(cacheNames = "winningNumberFrequency")
    @Transactional(readOnly = true)
    public List<NumberFrequencyDto> frequency() {
        long startedAt = System.nanoTime();
        String source = "recompute";
        if (summaryRepository != null) {
            int latestRound = repository.findMaxRound().orElse(0);
            List<WinningNumberFrequencySummaryEntity> summaryRows = summaryRepository.findAllByOrderByBallAsc();
            if (isUsableSummary(summaryRows, latestRound)) {
                List<NumberFrequencyDto> result = summaryRows.stream()
                        .map(row -> new NumberFrequencyDto(row.getBall(), row.getHitCount()))
                        .toList();
                source = "summary";
                recordFrequencyLatency(startedAt, source);
                return result;
            }
            List<NumberFrequencyDto> recomputed = recomputeFrequency();
            saveSummary(recomputed, latestRound);
            recordFrequencyLatency(startedAt, source);
            return recomputed;
        }
        List<NumberFrequencyDto> recomputed = recomputeFrequency();
        recordFrequencyLatency(startedAt, source);
        return recomputed;
    }

    private List<NumberFrequencyDto> recomputeFrequency() {
        long[] counts = new long[46];
        for (Object[] row : repository.findAllNumbersForFrequency()) {
            for (Object number : row) {
                counts[(Integer) number]++;
            }
        }
        return IntStream.rangeClosed(1, 45)
                .mapToObj(n -> new NumberFrequencyDto(n, counts[n]))
                .toList();
    }

    private boolean isUsableSummary(List<WinningNumberFrequencySummaryEntity> summaryRows, int latestRound) {
        if (summaryRows.size() != 45) {
            return false;
        }
        Set<Integer> balls = new HashSet<>(45);
        for (WinningNumberFrequencySummaryEntity row : summaryRows) {
            Integer ball = row.getBall();
            if (ball == null || ball < 1 || ball > 45) {
                return false;
            }
            if (!balls.add(ball)) {
                return false;
            }
            if (row.getHitCount() < 0) {
                return false;
            }
            if (row.getLastCalculatedRound() != latestRound) {
                return false;
            }
        }
        return balls.size() == 45;
    }

    private void saveSummary(List<NumberFrequencyDto> frequencies, int latestRound) {
        if (summaryRepository == null) {
            return;
        }
        List<WinningNumberFrequencySummaryEntity> rows = frequencies.stream()
                .map(dto -> new WinningNumberFrequencySummaryEntity(dto.number(), dto.count(), latestRound))
                .toList();
        summaryRepository.saveAll(rows);
        if (meterRegistry != null) {
            meterRegistry.counter("kraft.statistics.frequency.summary.refresh").increment();
        }
    }

    private void recordFrequencyLatency(long startedAtNano, String source) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.timer("kraft.statistics.frequency.latency", "source", source)
                .record(java.time.Duration.ofNanos(System.nanoTime() - startedAtNano));
    }

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "combinationPrizeHistory",
            key = "T(com.kraft.lotto.feature.statistics.application.WinningStatisticsService).combinationHistoryCacheKey(#numbers)"
    )
    public CombinationPrizeHistoryDto combinationPrizeHistory(List<Integer> numbers) {
        validateCombination(numbers);
        List<Integer> normalized = numbers.stream().sorted().toList();
        List<WinningNumberRepository.PrizeHitWithRankRow> hits = repository.findPrizeHitsByNumbers(
                normalized.get(0), normalized.get(1), normalized.get(2),
                normalized.get(3), normalized.get(4), normalized.get(5)
        );
        List<CombinationPrizeHitDto> firstPrizeHits = new ArrayList<>();
        List<CombinationPrizeHitDto> secondPrizeHits = new ArrayList<>();
        for (WinningNumberRepository.PrizeHitWithRankRow hit : hits) {
            CombinationPrizeHitDto dto = new CombinationPrizeHitDto(hit.getRound(), hit.getDrawDate());
            if (hit.getPrizeRank() != null && hit.getPrizeRank() == 1) {
                firstPrizeHits.add(dto);
            } else {
                secondPrizeHits.add(dto);
            }
        }

        return new CombinationPrizeHistoryDto(
                normalized,
                firstPrizeHits.size(),
                secondPrizeHits.size(),
                firstPrizeHits,
                secondPrizeHits
        );
    }

    @Transactional(readOnly = true)
    public FrequencySummaryDto frequencySummary() {
        WinningStatisticsService proxy = self == null ? this : self;
        List<NumberFrequencyDto> frequencies = proxy.frequency();
        List<Integer> lowSixNumbers = frequencies.stream()
                .sorted(Comparator.comparingLong(NumberFrequencyDto::count).thenComparingInt(NumberFrequencyDto::number))
                .limit(6)
                .map(NumberFrequencyDto::number)
                .sorted()
                .toList();
        CombinationPrizeHistoryDto lowSixHistory = combinationPrizeHistory(lowSixNumbers);
        return new FrequencySummaryDto(frequencies, lowSixHistory);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(cacheNames = {"winningNumberFrequency", "combinationPrizeHistory"}, allEntries = true)
    public void evictCachesOnCollected(WinningNumbersCollectedEvent event) {
        // cache eviction handled by annotation
    }

    private static void validateCombination(List<Integer> numbers) {
        if (numbers == null || numbers.size() != 6) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND, "numbers must contain exactly 6 values");
        }
        boolean[] seen = new boolean[46];
        for (Integer number : numbers) {
            if (number == null || number < 1 || number > 45 || seen[number]) {
                throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND, "numbers must be unique values from 1 to 45");
            }
            seen[number] = true;
        }
    }

    public static String combinationHistoryCacheKey(List<Integer> numbers) {
        if (numbers == null) {
            return "null";
        }
        return numbers.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
