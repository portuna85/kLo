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
 * RecommendController WebMvc slice ???裕??
 * ?곌랜?삯뇡??熬곥굤????????繹먮봿???琉우뿰 ???쳜?猿낆뿉??댁몠 ???됱굚??ApiResponse ?????嶺뚯쉶理먨ㅇ??類ｋ펲.
 * ?곌랜?삯뇡??筌먦끉???롪틵?嶺뚯빘鍮? ?곌랙?х뙴??Security ???? ???裕?筌뤾쑬?????臾먮뺄??類ｋ펲.
 */
@WebMvcTest(controllers = RecommendController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ExtendWith(RestDocumentationExtension.class)
    @DisplayName("테스트")
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
    @DisplayName("테스트")
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
                                        .description("설명")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("설명"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("설명"),
                                fieldWithPath("data.combinations").type(JsonFieldType.ARRAY).description("설명"),
                                fieldWithPath("data.combinations[].numbers").type(JsonFieldType.ARRAY).description("설명"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("설명")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.combinations.length()").value(3))
                .andExpect(jsonPath("$.data.combinations[0].numbers[0]").value(1))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("테스트")
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
    @DisplayName("테스트")
    void postRecommendReturns400WhenCountOutOfRange() throws Exception {
        mockMvc.perform(post("/api/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_COUNT"));
    }

    @Test
    @DisplayName("테스트")
    void postRecommendMapsBusinessExceptionToStatus() throws Exception {
        Mockito.when(recommendService.recommend(5))
                .thenThrow(new BusinessException(ErrorCode.LOTTO_GENERATION_TIMEOUT));

        mockMvc.perform(post("/api/recommend"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_GENERATION_TIMEOUT"));
    }

    @Test
    @DisplayName("테스트")
    void getRulesReturnsRuleList() throws Exception {
        Mockito.when(recommendService.rules()).thenReturn(List.of(
                new RuleDto("PastWinningRule", "??λ닁??1???獄????브퀗?ч뜮????熬곣뫗??????됰뎄???브퀗?ч뜮??? ??戮곕뇶??紐껊퉵??"),
                new RuleDto("BirthdayBiasRule", "6???뺢퀡???먯쾸? 嶺뚮ㅄ維筌?31 ??袁⑤┃????諭逾??뺢퀡????筌뤿굝???브퀗?ч뜮??? ??戮곕뇶??紐껊퉵??")
        ));

        mockMvc.perform(get("/api/recommend/rules"))
                .andDo(document("recommend-rules",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("설명"),
                                fieldWithPath("data").type(JsonFieldType.ARRAY).description("설명"),
                                fieldWithPath("data[].name").type(JsonFieldType.STRING).description("설명"),
                                fieldWithPath("data[].reason").type(JsonFieldType.STRING).description("설명"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("설명")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("PastWinningRule"))
                .andExpect(jsonPath("$.data[0].reason").exists());
    }
}

