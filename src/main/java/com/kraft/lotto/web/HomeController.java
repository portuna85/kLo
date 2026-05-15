package com.kraft.lotto.web;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final WinningNumberQueryService queryService;

    public HomeController(WinningNumberQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/")
    public String home(Model model) {
        try {
            model.addAttribute("latest", queryService.getLatest());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.WINNING_NUMBER_NOT_FOUND) {
                throw ex;
            }
            model.addAttribute("latest", null);
        }
        return "home";
    }
}
