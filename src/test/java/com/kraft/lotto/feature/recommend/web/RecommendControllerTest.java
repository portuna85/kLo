package com.kraft.lotto.feature.recommend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.recommend.application.RecommendService;
import com.kraft.lotto.feature.recommend.web.dto.CombinationDto;
import com.kraft.lotto.feature.recommend.web.dto.RecommendResponse;
import com.kraft.lotto.feature.recommend.web.dto.RuleDto;
import com.kraft.lotto.TestCacheConfig;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import com.kraft.lotto.support.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * RecommendController WebMvc slice ???獒??
 * ?怨뚮옖???눀???ш낄援????????濚밸Ŧ遊???筌뚯슦肉????爾??용굞肉???곷첓 ????깃탾??ApiResponse ?????癲ル슣?띰ℓ癒ⓦ뀋??筌먲퐢??
 * ?怨뚮옖???눀??嶺뚮Ĳ????濡ろ떟?癲ル슣鍮섌뜮? ?怨뚮옓?????Security ???? ???獒?嶺뚮ㅎ???????얜Ŧ類??筌먲퐢??
 */
@WebMvcTest(controllers = RecommendController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, TestCacheConfig.class})
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("추천 컨트롤러 테스트")
class RecommendControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @MockitoBean
    RecommendService recommendService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    @DisplayName("추천 요청이 성공하면 200 OK를 반환한다")
    void postRecommendReturnsOk() throws Exception {
        Mockito.when(recommendService.recommend(3))
                .thenReturn(new RecommendResponse(List.of(
                        new CombinationDto(List.of(1, 7, 13, 22, 34, 45)),
                        new CombinationDto(List.of(2, 8, 14, 21, 33, 44)),
                        new CombinationDto(List.of(3, 9, 16, 27, 35, 41))
                )));

        mockMvc.perform(post("/api/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":3}"))
                .andDo(document("recommend-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("count").type(JsonFieldType.NUMBER)
                                        .optional()
                                        .description("?ㅻ챸")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("?ㅻ챸"),
                                fieldWithPath("data.combinations").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("data.combinations[].numbers").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("?ㅻ챸")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.combinations.length()").value(3))
                .andExpect(jsonPath("$.data.combinations[0].numbers[0]").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("본문이 없으면 기본 추천 개수를 사용한다")
    void postRecommendUsesDefaultCountWhenBodyAbsent() throws Exception {
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
    @DisplayName("추천 개수가 범위를 벗어나면 400 Bad Request를 반환한다")
    void postRecommendReturns400WhenCountOutOfRange() throws Exception {
        mockMvc.perform(post("/api/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_COUNT"));
    }

    @Test
    @DisplayName("비즈니스 예외 발생 시 적절한 상태 코드로 매핑한다")
    void postRecommendMapsBusinessExceptionToStatus() throws Exception {
        Mockito.when(recommendService.recommend(5))
                .thenThrow(new BusinessException(ErrorCode.LOTTO_GENERATION_TIMEOUT));

        mockMvc.perform(post("/api/recommend"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_GENERATION_TIMEOUT"));
    }

    @Test
    @DisplayName("규칙 목록 조회 시 규칙 리스트를 반환한다")
    void getRulesReturnsRuleList() throws Exception {
        Mockito.when(recommendService.rules()).thenReturn(List.of(
                new RuleDto("PastWinningRule", "??貫???1?????????釉뚰????????ш끽維???????곕럡???釉뚰?????? ??筌믨퀡???筌뤾퍓???"),
                new RuleDto("BirthdayBiasRule", "6???類????癒?씀? 癲ル슢?꾤땟?嶺?31 ??熬곣뫀?????獄????類?????嶺뚮ㅏ援???釉뚰?????? ??筌믨퀡???筌뤾퍓???")
        ));

        mockMvc.perform(get("/api/recommend/rules"))
                .andDo(document("recommend-rules",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("data").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("data[].name").type(JsonFieldType.STRING).description("?ㅻ챸"),
                                fieldWithPath("data[].reason").type(JsonFieldType.STRING).description("?ㅻ챸"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("?ㅻ챸")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("PastWinningRule"))
                .andExpect(jsonPath("$.data[0].reason").exists());
    }
}

