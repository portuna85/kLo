package com.kraft.lotto.feature.winningnumber.web.dto;

public record BackfillJobStatusResponse(
        String jobId,
        String status,
        Integer from,
        Integer to,
        CollectResponse result,
        String error
) {
}
