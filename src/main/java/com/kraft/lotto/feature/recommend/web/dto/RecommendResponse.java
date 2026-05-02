package com.kraft.lotto.feature.recommend.web.dto;

import java.util.List;

public record RecommendResponse(List<CombinationDto> combinations) {
    public RecommendResponse {
        combinations = List.copyOf(combinations);
    }
}
