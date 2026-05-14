package com.kraft.lotto.feature.winningnumber.web;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import com.kraft.lotto.feature.winningnumber.web.dto.NumberFrequencyDto;
import com.kraft.lotto.feature.winningnumber.web.dto.CombinationPrizeHistoryDto;
import com.kraft.lotto.feature.winningnumber.web.dto.FrequencySummaryDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberPageDto;
import com.kraft.lotto.support.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당첨번호 공개 조회 API. 인증 없이 접근 가능하다.
 */
@RestController
@Validated
@RequestMapping({"/api/winning-numbers", "/api/v1/winning-numbers"})
public class WinningNumberController {

    private final WinningNumberQueryService queryService;

    public WinningNumberController(WinningNumberQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/latest")
    public ApiResponse<WinningNumberDto> latest() {
        return ApiResponse.success(queryService.getLatest());
    }

    @GetMapping("/{round}")
    public ApiResponse<WinningNumberDto> byRound(@PathVariable("round") @Min(1) @Max(WinningNumberQueryService.MAX_ROUND) int round) {
        return ApiResponse.success(queryService.getByRound(round));
    }

    @GetMapping
    public ApiResponse<WinningNumberPageDto> list(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(queryService.list(page, size));
    }

    @GetMapping("/stats/frequency")
    public ApiResponse<List<NumberFrequencyDto>> frequency() {
        return ApiResponse.success(queryService.frequency());
    }

    @GetMapping("/stats/frequency-summary")
    public ApiResponse<FrequencySummaryDto> frequencySummary() {
        return ApiResponse.success(queryService.frequencySummary());
    }

    @GetMapping("/stats/combination-prize-history")
    public ApiResponse<CombinationPrizeHistoryDto> combinationPrizeHistory(
            @RequestParam(name = "numbers") List<Integer> numbers
    ) {
        return ApiResponse.success(queryService.combinationPrizeHistory(numbers));
    }
}
