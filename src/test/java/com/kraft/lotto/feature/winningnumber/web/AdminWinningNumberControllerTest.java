package com.kraft.lotto.feature.winningnumber.web;

import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberCollectService;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import com.kraft.lotto.support.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminWinningNumberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminWinningNumberController WebMvc")
class AdminWinningNumberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WinningNumberCollectService collectService;

    @Test
    @DisplayName("POST /collect 본문이 없으면 targetRound 를 null 로 위임한다")
    void postCollectDelegatesNullTargetRoundWhenBodyAbsent() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenReturn(new CollectResponse(3, 0, 0, 1103));

        mockMvc.perform(post("/api/admin/winning-numbers/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collected").value(3))
                .andExpect(jsonPath("$.data.latestRound").value(1103));
    }

    @Test
    @DisplayName("POST /collect 는 지정된 targetRound 로 위임한다")
    void postCollectDelegatesSpecifiedTargetRound() throws Exception {
        Mockito.when(collectService.collect(1103))
                .thenReturn(new CollectResponse(2, 1, 0, 1103));

        mockMvc.perform(post("/api/admin/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRound\":1103}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collected").value(2))
                .andExpect(jsonPath("$.data.skipped").value(1));
    }

    @Test
    @DisplayName("POST /collect 외부 API 실패 시 502 BAD_GATEWAY 를 반환한다")
    void postCollectReturns502OnExternalApiFailure() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_FAILURE));

        mockMvc.perform(post("/api/admin/winning-numbers/refresh"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_API_FAILURE"));
    }
}

