package com.kraft.lotto.feature.winningnumber.web.dto;

import java.util.List;

public record FrequencySummaryDto(
        List<NumberFrequencyDto> frequencies,
        CombinationPrizeHistoryDto lowSixCombinationHistory
) {
}

