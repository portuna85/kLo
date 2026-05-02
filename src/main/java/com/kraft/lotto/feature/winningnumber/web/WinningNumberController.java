package com.kraft.lotto.feature.winningnumber.web;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberPageDto;
import com.kraft.lotto.support.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 당첨번호 공개 조회 API. 인증 없이 접근 가능하다.
 */
@RestController
@RequestMapping("/api/winning-numbers")
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
    public ApiResponse<WinningNumberDto> byRound(@PathVariable("round") int round) {
        return ApiResponse.success(queryService.getByRound(round));
    }

    @GetMapping
    public ApiResponse<WinningNumberPageDto> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.success(queryService.list(page, size));
    }
}
