package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.event.WinningNumbersCollectedEvent;
import com.kraft.lotto.feature.winningnumber.web.dto.NumberFrequencyDto;
import com.kraft.lotto.feature.winningnumber.web.dto.CombinationPrizeHitDto;
import com.kraft.lotto.feature.winningnumber.web.dto.CombinationPrizeHistoryDto;
import com.kraft.lotto.feature.winningnumber.web.dto.FrequencySummaryDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberPageDto;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 당첨번호 조회 application 서비스.
 * 도메인 객체로 한 번 검증한 뒤 응답 DTO로 변환한다.
 */
@Service
@Transactional(readOnly = true)
public class WinningNumberQueryService {

    public static final int MAX_ROUND = 3000;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final WinningNumberRepository repository;

    @Lazy
    @Autowired
    private WinningNumberQueryService self;

    public WinningNumberQueryService(WinningNumberRepository repository) {
        this.repository = repository;
    }

    public WinningNumberDto getLatest() {
        return repository.findTopByOrderByRoundDesc()
                .map(WinningNumberMapper::toDomain)
                .map(WinningNumberDto::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.WINNING_NUMBER_NOT_FOUND));
    }

    public WinningNumberDto getByRound(int round) {
        if (round <= 0 || round > MAX_ROUND) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
        }
        return repository.findById(round)
                .map(WinningNumberMapper::toDomain)
                .map(WinningNumberDto::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.WINNING_NUMBER_NOT_FOUND));
    }

    public WinningNumberPageDto list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        var entities = repository.findAllByOrderByRoundDesc(PageRequest.of(safePage, safeSize));
        var mapped = entities
                .map(WinningNumberMapper::toDomain)
                .map(WinningNumberDto::from);
        return WinningNumberPageDto.from(mapped);
    }

    /**
     * 1~45 본번호의 출현 빈도를 회차 전체에 걸쳐 집계해 1번부터 45번까지 오름차순으로 반환한다.
     * 보너스 번호는 집계에서 제외한다. 데이터가 없는 번호도 count=0으로 항상 포함된다.
     */
    @Cacheable(cacheNames = "winningNumberFrequency")
    public List<NumberFrequencyDto> frequency() {
        long[] counts = new long[46]; // index 1..45
        for (Object[] row : repository.findAllNumbersForFrequency()) {
            countMainNumbers(counts, row);
        }
        return IntStream.rangeClosed(1, 45)
                .mapToObj(n -> new NumberFrequencyDto(n, counts[n]))
                .toList();
    }

    @Cacheable(
            cacheNames = "combinationPrizeHistory",
            key = "T(com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService).combinationHistoryCacheKey(#numbers)"
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
        List<NumberFrequencyDto> frequencies = self.frequency();
        List<Integer> lowSixNumbers = frequencies.stream()
                .sorted(Comparator.comparingLong(NumberFrequencyDto::count).thenComparingInt(NumberFrequencyDto::number))
                .limit(6)
                .map(NumberFrequencyDto::number)
                .sorted()
                .toList();
        CombinationPrizeHistoryDto lowSixHistory = self.combinationPrizeHistory(lowSixNumbers);
        return new FrequencySummaryDto(frequencies, lowSixHistory);
    }

    @EventListener
    @CacheEvict(cacheNames = {"winningNumberFrequency", "combinationPrizeHistory"}, allEntries = true)
    public void evictFrequencyCacheOnCollected(WinningNumbersCollectedEvent event) {
        // cache eviction handled by annotation
    }

    private static void countMainNumbers(long[] counts, Object[] row) {
        for (Object number : row) {
            counts[(Integer) number]++;
        }
    }

    private static void validateCombination(List<Integer> numbers) {
        if (numbers == null || numbers.size() != 6) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND, "번호는 6개여야 합니다.");
        }
        boolean[] seen = new boolean[46];
        for (Integer number : numbers) {
            if (number == null || number < 1 || number > 45 || seen[number]) {
                throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND, "번호는 1~45 중복 없는 6개여야 합니다.");
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
