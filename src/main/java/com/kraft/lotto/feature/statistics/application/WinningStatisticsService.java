package com.kraft.lotto.feature.statistics.application;

import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.CombinationPrizeHistoryDto;
import com.kraft.lotto.feature.winningnumber.web.dto.CombinationPrizeHitDto;
import com.kraft.lotto.feature.winningnumber.web.dto.FrequencySummaryDto;
import com.kraft.lotto.feature.winningnumber.web.dto.NumberFrequencyDto;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WinningStatisticsService {

    private final WinningNumberRepository repository;

    public WinningStatisticsService(WinningNumberRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = "winningNumberFrequency")
    public List<NumberFrequencyDto> frequency() {
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

    public FrequencySummaryDto frequencySummary() {
        List<NumberFrequencyDto> frequencies = frequency();
        List<Integer> lowSixNumbers = frequencies.stream()
                .sorted(Comparator.comparingLong(NumberFrequencyDto::count).thenComparingInt(NumberFrequencyDto::number))
                .limit(6)
                .map(NumberFrequencyDto::number)
                .sorted()
                .toList();
        CombinationPrizeHistoryDto lowSixHistory = combinationPrizeHistory(lowSixNumbers);
        return new FrequencySummaryDto(frequencies, lowSixHistory);
    }

    @EventListener
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
