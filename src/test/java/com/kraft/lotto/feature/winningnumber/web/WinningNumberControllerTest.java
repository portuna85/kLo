package com.kraft.lotto.feature.winningnumber.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.winningnumber.application.WinningNumberQueryService;
import com.kraft.lotto.feature.winningnumber.web.dto.NumberFrequencyDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberPageDto;
import com.kraft.lotto.TestCacheConfig;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import com.kraft.lotto.support.GlobalExceptionHandler;
import java.time.LocalDate;
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
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(controllers = WinningNumberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, TestCacheConfig.class})
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("당첨 번호 조회 컨트롤러 테스트")
class WinningNumberControllerTest {
    private static final int ROUND_EXISTING = 1102;
    private static final int ROUND_NOT_FOUND = 1200;


    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @MockitoBean
    WinningNumberQueryService queryService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    private static WinningNumberDto sample(int round) {
        return new WinningNumberDto(round, LocalDate.of(2024, 1, 6),
                List.of(6, 13, 23, 24, 28, 33), 38, 2_596_477_500L, 11, 79_760_843_000L);
    }

    @Test
    @DisplayName("최신 당첨 번호 조회 시 200 OK를 반환한다")
    void getLatestReturnsOk() throws Exception {
        Mockito.when(queryService.getLatest()).thenReturn(sample(ROUND_EXISTING));

        mockMvc.perform(get("/api/winning-numbers/latest"))
                .andDo(document("winning-numbers-latest",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("?ㅻ챸"),
                                fieldWithPath("data.round").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.drawDate").type(JsonFieldType.STRING).description("?ㅻ챸"),
                                fieldWithPath("data.numbers").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("data.bonusNumber").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.firstPrize").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.firstWinners").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.totalSales").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("?ㅻ챸")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.round").value(ROUND_EXISTING))
                .andExpect(jsonPath("$.data.bonusNumber").value(38))
                .andExpect(jsonPath("$.data.numbers.length()").value(6));
    }

    @Test
    @DisplayName("회차별 당첨 번호 조회 시 200 OK를 반환한다")
    void getByRoundReturnsOk() throws Exception {
        Mockito.when(queryService.getByRound(ROUND_EXISTING)).thenReturn(sample(ROUND_EXISTING));

        mockMvc.perform(get("/api/winning-numbers/{round}", ROUND_EXISTING))
                .andDo(document("winning-numbers-by-round",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("?ㅻ챸"),
                                fieldWithPath("data.round").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.drawDate").type(JsonFieldType.STRING).description("?ㅻ챸"),
                                fieldWithPath("data.numbers").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("data.bonusNumber").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.firstPrize").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.firstWinners").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.totalSales").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("?ㅻ챸")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.round").value(ROUND_EXISTING));
    }

    @Test
    @DisplayName("존재하지 않는 회차 조회 시 404 Not Found를 반환한다")
    void getByRoundReturns404WhenNotFound() throws Exception {
        Mockito.when(queryService.getByRound(ROUND_NOT_FOUND))
                .thenThrow(new BusinessException(ErrorCode.WINNING_NUMBER_NOT_FOUND));

        mockMvc.perform(get("/api/winning-numbers/{round}", ROUND_NOT_FOUND))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("WINNING_NUMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("당첨 번호 목록 조회 시 페이징된 결과를 반환한다")
    void getListReturnsPage() throws Exception {
        Mockito.when(queryService.list(0, 20))
                .thenReturn(new WinningNumberPageDto(List.of(sample(ROUND_EXISTING), sample(1101)), 0, 20, 2L, 1));

        mockMvc.perform(get("/api/winning-numbers?page=0&size=20"))
                .andDo(document("winning-numbers-list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("?ㅻ챸"),
                                fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("data.content[].round").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.content[].drawDate").type(JsonFieldType.STRING).description("?ㅻ챸"),
                                fieldWithPath("data.content[].numbers").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("data.content[].bonusNumber").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.content[].firstPrize").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.content[].firstWinners").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.content[].totalSales").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.page").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.size").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.totalElements").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.totalPages").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("?ㅻ챸")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("번호별 출현 빈도 통계 조회 시 200 OK를 반환한다")
    void getStatsFrequencyReturnsOk() throws Exception {
        List<NumberFrequencyDto> data = List.of(
                new NumberFrequencyDto(1, 123),
                new NumberFrequencyDto(2, 118)
        );
        Mockito.when(queryService.frequency()).thenReturn(data);

        mockMvc.perform(get("/api/winning-numbers/stats/frequency"))
                .andDo(document("winning-numbers-frequency",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("data").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("data[].number").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data[].count").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("?ㅻ챸")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].number").value(1))
                .andExpect(jsonPath("$.data[0].count").value(123))
                .andExpect(jsonPath("$.data[1].number").value(2))
                .andExpect(jsonPath("$.data[1].count").value(118));
    }

    @Test
    @DisplayName("잘못된 페이지 요청 시 400 Bad Request를 반환한다")
    void getListReturns400WhenPageRequestInvalid() throws Exception {
        mockMvc.perform(get("/api/winning-numbers?page=-1&size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_PAGE_REQUEST"));
    }

    @Test
    @DisplayName("회차가 1보다 작으면 400 Bad Request를 반환한다")
    void getByRoundReturns400WhenRoundIsLessThanOne() throws Exception {
        mockMvc.perform(get("/api/winning-numbers/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));
    }

    @Test
    @DisplayName("회차가 숫자가 아니면 400 Bad Request를 반환한다")
    void getByRoundReturns400WhenRoundIsNotNumeric() throws Exception {
        mockMvc.perform(get("/api/winning-numbers/10a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));
    }

    @Test
    @DisplayName("회차가 최대값을 초과하면 400 Bad Request를 반환한다")
    void getByRoundReturns400WhenRoundExceedsMax() throws Exception {
        mockMvc.perform(get("/api/winning-numbers/3001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));
    }
}
