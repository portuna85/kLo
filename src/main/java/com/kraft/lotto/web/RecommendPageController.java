package com.kraft.lotto.web;

import com.kraft.lotto.feature.recommend.application.RecommendService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/recommend")
public class RecommendPageController {

    private final RecommendService recommendService;

    public RecommendPageController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @GetMapping
    public String recommend(@RequestParam(defaultValue = "5") int count, Model model) {
        int safeCount = Math.max(1, Math.min(10, count));
        model.addAttribute("count", safeCount);
        model.addAttribute("combinations", recommendService.recommend(safeCount).combinations());
        model.addAttribute("rules", recommendService.rules());
        return "recommend/index";
    }
}
