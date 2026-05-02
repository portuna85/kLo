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
 * 관리자 전용 당첨번호 수집 트리거 API. SecurityConfig의 {@code /api/admin/**} 정책에 의해 ROLE_ADMIN만 접근할 수 있다.
 */
@RestController
@RequestMapping("/api/admin/winning-numbers")
public class AdminWinningNumberController {

    private final WinningNumberCollectService collectService;

    public AdminWinningNumberController(WinningNumberCollectService collectService) {
        this.collectService = collectService;
    }

    @PostMapping("/refresh")
    public ApiResponse<CollectResponse> refresh(@Valid @RequestBody(required = false) CollectRequest request) {
        Integer target = request == null ? null : request.targetRound();
        return ApiResponse.success(collectService.collect(target));
    }
}
