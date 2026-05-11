package com.kraft.lotto.feature.winningnumber.web;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.winningnumber.application.LottoCollectionService;
import com.kraft.lotto.support.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminLottoDrawController.class)
@Import(GlobalExceptionHandler.class)
    @DisplayName("테스트")
class AdminLottoDrawControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LottoCollectionService collectionService;

    @Test
    @DisplayName("테스트")
    void refreshRejectsInvalidRound() throws Exception {
        mockMvc.perform(post("/admin/lotto/draws/0/refresh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));

        verify(collectionService, never()).refreshDraw(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("테스트")
    void backfillRejectsInvalidRangeParams() throws Exception {
        mockMvc.perform(post("/admin/lotto/draws/backfill")
                        .param("from", "1")
                        .param("to", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));

        verify(collectionService, never()).backfill(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }
}
