package com.kraft.lotto.feature.winningnumber.web;

import com.kraft.lotto.support.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminSmokeController {

    @GetMapping("/smoke-auth-check")
    public ApiResponse<Map<String, String>> smokeAuthCheck() {
        return ApiResponse.success(Map.of("status", "ok"));
    }
}
