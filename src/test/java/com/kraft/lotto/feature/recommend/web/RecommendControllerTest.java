package com.kraft.lotto.feature.recommend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.recommend.application.RecommendService;
import com.kraft.lotto.feature.recommend.web.dto.CombinationDto;
import com.kraft.lotto.feature.recommend.web.dto.RecommendResponse;
import com.kraft.lotto.feature.recommend.web.dto.RuleDto;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import com.kraft.lotto.support.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RecommendController WebMvc slice 테스트.
 * 보안 필터는 비활성화하여 컨트롤러 동작과 ApiResponse 포맷에 집중한다.
 * 보안 정책 검증은 별도의 Security 통합 테스트에서 수행한다.
 */
@WebMvcTest(controllers = RecommendController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RecommendControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RecommendService recommendService;

    @Test
    void POST_recommend_정상응답() throws Exception {
        Mockito.when(recommendService.recommend(3))
                .thenReturn(new RecommendResponse(List.of(
                        new CombinationDto(List.of(1, 7, 13, 22, 34, 45)),
                        new CombinationDto(List.of(2, 8, 14, 21, 33, 44)),
                        new CombinationDto(List.of(3, 9, 16, 27, 35, 41))
                )));

        mockMvc.perform(post("/api/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.combinations.length()").value(3))
                .andExpect(jsonPath("$.data.combinations[0].numbers[0]").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void POST_recommend_본문없으면_기본값5_사용() throws Exception {
        Mockito.when(recommendService.recommend(5))
                .thenReturn(new RecommendResponse(List.of(
                        new CombinationDto(List.of(1, 2, 3, 4, 5, 6)),
                        new CombinationDto(List.of(7, 8, 9, 10, 11, 12)),
                        new CombinationDto(List.of(13, 14, 15, 16, 17, 18)),
                        new CombinationDto(List.of(19, 20, 21, 22, 23, 24)),
                        new CombinationDto(List.of(25, 26, 27, 28, 29, 30))
                )));

        mockMvc.perform(post("/api/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.combinations.length()").value(5));
    }

    @Test
    void POST_recommend_count_범위초과시_400() throws Exception {
        mockMvc.perform(post("/api/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_COUNT"));
    }

    @Test
    void POST_recommend_서비스_BusinessException은_적절한_상태코드와_본문() throws Exception {
        Mockito.when(recommendService.recommend(5))
                .thenThrow(new BusinessException(ErrorCode.LOTTO_GENERATION_TIMEOUT));

        mockMvc.perform(post("/api/recommend"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_GENERATION_TIMEOUT"));
    }

    @Test
    void GET_rules_규칙목록_반환() throws Exception {
        Mockito.when(recommendService.rules()).thenReturn(List.of(
                new RuleDto("PastWinningRule", "과거 1등 당첨 조합과 완전히 동일한 조합은 제외합니다."),
                new RuleDto("BirthdayBiasRule", "6개 번호가 모두 31 이하인 생일 번호 편향 조합은 제외합니다.")
        ));

        mockMvc.perform(get("/api/recommend/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("PastWinningRule"))
                .andExpect(jsonPath("$.data[0].reason").exists());
    }
}
