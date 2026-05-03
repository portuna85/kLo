package com.kraft.lotto.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 단일 페이지 진입점(SSR).
 *
 * <p>{@code /}, {@code /index} 두 경로 모두 동일한 Thymeleaf 뷰({@code index.html})를 렌더링한다.
 * 화면 데이터는 클라이언트 사이드에서 {@code /api/...} 를 호출하여 채운다.
 */
@Controller
public class IndexController {

    @GetMapping({"/", "/index"})
    public String index(Model model) {
        model.addAttribute("appName", "KraftLotto");
        model.addAttribute("activeNav", "home");
        return "index";
    }
}

