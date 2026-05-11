package com.kraft.lotto.feature.winningnumber.web;

import com.kraft.lotto.feature.winningnumber.application.LottoCollectionService;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/admin/lotto/draws")
public class AdminLottoDrawController {

    private final LottoCollectionService collectionService;

    public AdminLottoDrawController(LottoCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping("/collect-next")
    public ApiResponse<CollectResponse> collectNext() {
        return ApiResponse.success(collectionService.collectNextDraw());
    }

    @PostMapping("/collect-missing")
    public ApiResponse<CollectResponse> collectMissing() {
        return ApiResponse.success(collectionService.collectMissingDraws());
    }

    @PostMapping("/{drwNo}/refresh")
    public ApiResponse<CollectResponse> refresh(@PathVariable @Min(1) @Max(3000) int drwNo) {
        return ApiResponse.success(collectionService.refreshDraw(drwNo));
    }

    @PostMapping("/backfill")
    public ApiResponse<CollectResponse> backfill(@RequestParam @Min(1) @Max(3000) int from,
                                                 @RequestParam @Min(1) @Max(3000) int to) {
        return ApiResponse.success(collectionService.backfill(from, to));
    }
}
