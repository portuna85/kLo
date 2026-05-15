package com.kraft.lotto.web;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/rounds")
public class RoundPageController {

    private final WinningNumberQueryService queryService;

    public RoundPageController(WinningNumberQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        model.addAttribute("rounds", queryService.list(page, size));
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        return "rounds/list";
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) Integer round, Model model) {
        model.addAttribute("round", round);
        if (round != null) {
            model.addAttribute("result", queryService.getByRound(round));
        }
        return "rounds/search";
    }
}
