package com.kraft.lotto.feature.winningnumber.web;

import com.kraft.lotto.feature.winningnumber.application.BackfillJobService;
import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import com.kraft.lotto.feature.winningnumber.web.dto.BackfillJobStatusResponse;
import com.kraft.lotto.support.ApiResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/admin/lotto/jobs")
public class AdminLottoJobController {

    private final BackfillJobService backfillJobService;

    public AdminLottoJobController(BackfillJobService backfillJobService) {
        this.backfillJobService = backfillJobService;
    }

    @PostMapping("/backfill")
    public ApiResponse<BackfillJobStatusResponse> startBackfill(
            @RequestParam @Min(1) @Max(WinningNumberQueryService.MAX_ROUND) int from,
            @RequestParam @Min(1) @Max(WinningNumberQueryService.MAX_ROUND) int to) {
        return ApiResponse.success(backfillJobService.start(from, to));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<BackfillJobStatusResponse> getJob(@PathVariable String jobId) {
        BackfillJobStatusResponse response = backfillJobService.get(jobId);
        if (response == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "backfill job not found: " + jobId);
        }
        return ApiResponse.success(response);
    }
}
