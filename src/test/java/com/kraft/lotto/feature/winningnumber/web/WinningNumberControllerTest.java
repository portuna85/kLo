package com.kraft.lotto.feature.winningnumber.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberPageDto;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import com.kraft.lotto.support.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WinningNumberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class WinningNumberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WinningNumberQueryService queryService;

    private static WinningNumberDto sample(int round) {
        return new WinningNumberDto(round, LocalDate.of(2024, 1, 6),
                List.of(6, 13, 23, 24, 28, 33), 38, 2_596_477_500L, 11, 79_760_843_000L);
    }

    @Test
    void GET_latest_정상응답() throws Exception {
        Mockito.when(queryService.getLatest()).thenReturn(sample(1102));

        mockMvc.perform(get("/api/winning-numbers/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.round").value(1102))
                .andExpect(jsonPath("$.data.bonusNumber").value(38))
                .andExpect(jsonPath("$.data.numbers.length()").value(6));
    }

    @Test
    void GET_byRound_없으면_404_NOT_FOUND() throws Exception {
        Mockito.when(queryService.getByRound(9999))
                .thenThrow(new BusinessException(ErrorCode.WINNING_NUMBER_NOT_FOUND));

        mockMvc.perform(get("/api/winning-numbers/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("WINNING_NUMBER_NOT_FOUND"));
    }

    @Test
    void GET_list_페이지_응답() throws Exception {
        Mockito.when(queryService.list(0, 20))
                .thenReturn(new WinningNumberPageDto(List.of(sample(1102), sample(1101)), 0, 20, 2L, 1));

        mockMvc.perform(get("/api/winning-numbers?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }
}
