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
class AdminWinningNumberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WinningNumberCollectService collectService;

    @Test
    void POST_collect_본문없으면_targetRound_null로_위임() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenReturn(new CollectResponse(3, 0, 0, 1103));

        mockMvc.perform(post("/api/admin/winning-numbers/collect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collected").value(3))
                .andExpect(jsonPath("$.data.latestRound").value(1103));
    }

    @Test
    void POST_collect_targetRound_지정() throws Exception {
        Mockito.when(collectService.collect(1103))
                .thenReturn(new CollectResponse(2, 1, 0, 1103));

        mockMvc.perform(post("/api/admin/winning-numbers/collect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRound\":1103}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collected").value(2))
                .andExpect(jsonPath("$.data.skipped").value(1));
    }

    @Test
    void POST_collect_외부_API_실패는_502_BAD_GATEWAY() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_FAILURE));

        mockMvc.perform(post("/api/admin/winning-numbers/collect"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_API_FAILURE"));
    }
}
