package com.kraft.lotto.feature.winningnumber.web;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberCollectService;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectRequest;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Winning-number collection trigger API. This endpoint requires admin token authentication.
 */
@RestController
@RequestMapping("/api/winning-numbers")
public class WinningNumberCollectController {

    private final WinningNumberCollectService collectService;

    public WinningNumberCollectController(WinningNumberCollectService collectService) {
        this.collectService = collectService;
    }

    @PostMapping("/refresh")
    public ApiResponse<CollectResponse> refresh(@Valid @RequestBody(required = false) CollectRequest request) {
        Integer target = request == null || request.targetRound() == null
                ? null
                : Integer.parseInt(request.targetRound());
        return ApiResponse.success(collectService.collect(target));
    }
}
