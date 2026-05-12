package com.kraft.lotto.feature.winningnumber.web.dto;

import java.util.List;

public record CombinationPrizeHistoryDto(
        List<Integer> numbers,
        int firstPrizeCount,
        int secondPrizeCount,
        List<CombinationPrizeHitDto> firstPrizeHits,
        List<CombinationPrizeHitDto> secondPrizeHits
) {
}

