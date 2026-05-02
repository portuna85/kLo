package com.kraft.lotto.feature.recommend.web.dto;

import java.util.List;

public record CombinationDto(List<Integer> numbers) {
    public CombinationDto {
        numbers = List.copyOf(numbers);
    }
}
