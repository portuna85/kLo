package com.kraft.lotto.feature.recommend.web;

import com.kraft.lotto.feature.recommend.application.RecommendService;
import com.kraft.lotto.feature.recommend.web.dto.RecommendRequest;
import com.kraft.lotto.feature.recommend.web.dto.RecommendResponse;
import com.kraft.lotto.feature.recommend.web.dto.RuleDto;
import com.kraft.lotto.support.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로또 추천 REST API.
 *
 * 본 API는 인기/중복 패턴 회피형 추천만을 제공하며, 당첨 확률 향상을 보장하지 않는다.
 */
@RestController
@RequestMapping({"/api/recommend", "/api/v1/recommend"})
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @PostMapping
    public ApiResponse<RecommendResponse> recommend(@Valid @RequestBody(required = false) RecommendRequest request) {
        int count = (request == null) ? RecommendRequest.DEFAULT_COUNT : request.countOrDefault();
        return ApiResponse.success(recommendService.recommend(count));
    }

    @GetMapping("/rules")
    public ApiResponse<List<RuleDto>> rules() {
        return ApiResponse.success(recommendService.rules());
    }
}
