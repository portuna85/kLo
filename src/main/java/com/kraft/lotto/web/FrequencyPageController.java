package com.kraft.lotto.web;

import com.kraft.lotto.feature.statistics.application.WinningStatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/frequency")
public class FrequencyPageController {

    private final WinningStatisticsService statisticsService;

    public FrequencyPageController(WinningStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public String frequency(Model model) {
        model.addAttribute("frequency", statisticsService.frequency());
        return "frequency/index";
    }
}
