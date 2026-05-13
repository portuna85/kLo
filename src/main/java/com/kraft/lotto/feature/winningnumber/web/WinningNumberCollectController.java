package com.kraft.lotto.feature.winningnumber.web;

import com.kraft.lotto.feature.winningnumber.application.LottoCollectionService;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectRequest;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Winning-number collection trigger API. This endpoint requires admin token authentication.
 */
@RestController
@RequestMapping({"/api/winning-numbers", "/api/v1/winning-numbers"})
public class WinningNumberCollectController {

    private static final String ADMIN_REPLACEMENT_PATH = "/admin/lotto/draws/collect-next";
    private static final String SUNSET_DATE = "Thu, 31 Jul 2026 23:59:59 GMT";

    private final LottoCollectionService collectService;

    public WinningNumberCollectController(LottoCollectionService collectService) {
        this.collectService = collectService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<CollectResponse>> refresh(@Valid @RequestBody(required = false) CollectRequest request) {
        Integer target = request == null || request.targetRound() == null
                ? null
                : Integer.parseInt(request.targetRound());
        HttpHeaders headers = new HttpHeaders();
        headers.add("Deprecation", "true");
        headers.add("Sunset", SUNSET_DATE);
        headers.add("Link", "<" + ADMIN_REPLACEMENT_PATH + ">; rel=\"successor-version\"");
        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.success(collectService.collect(target)));
    }
}
