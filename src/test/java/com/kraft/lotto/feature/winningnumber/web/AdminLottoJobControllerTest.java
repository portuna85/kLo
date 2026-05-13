package com.kraft.lotto.feature.winningnumber.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.winningnumber.application.BackfillJobService;
import com.kraft.lotto.feature.winningnumber.web.dto.BackfillJobStatusResponse;
import com.kraft.lotto.TestCacheConfig;
import com.kraft.lotto.support.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminLottoJobController.class)
@Import({GlobalExceptionHandler.class, TestCacheConfig.class})
@DisplayName("관리자 로또 작업 컨트롤러 테스트")
class AdminLottoJobControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BackfillJobService backfillJobService;

    @Test
    @DisplayName("백필 작업을 시작하고 작업 ID를 반환한다")
    void startBackfillReturnsJobId() throws Exception {
        when(backfillJobService.start(1, 10))
                .thenReturn(new BackfillJobStatusResponse("job-1", "QUEUED", 1, 10, null, null));

        mockMvc.perform(post("/admin/lotto/jobs/backfill").param("from", "1").param("to", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-1"));
    }

    @Test
    @DisplayName("존재하지 않는 작업 조회 시 404를 반환한다")
    void getJobReturns404WhenMissing() throws Exception {
        when(backfillJobService.get("missing")).thenReturn(null);

        mockMvc.perform(get("/admin/lotto/jobs/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }
}
