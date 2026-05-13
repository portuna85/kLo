package com.kraft.lotto.feature.winningnumber.web;

import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kraft.lotto.feature.winningnumber.application.LottoCollectionService;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import com.kraft.lotto.support.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("tests for WinningNumberCollectControllerTest")
class WinningNumberCollectControllerTest {

    @Mock
    LottoCollectionService collectService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WinningNumberCollectController(collectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    @DisplayName("post collect delegates null target round when body absent")
    void postCollectDelegatesNullTargetRoundWhenBodyAbsent() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenReturn(new CollectResponse(3, 0, 0, 1103, List.of(), true, 2000, false));

        mockMvc.perform(post("/api/winning-numbers/refresh"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Sunset", "Thu, 31 Jul 2026 23:59:59 GMT"))
                .andExpect(header().string("Link", "</admin/lotto/draws/collect-next>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collected").value(3))
                .andExpect(jsonPath("$.data.latestRound").value(1103))
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andExpect(jsonPath("$.data.nextRound").value(2000));
    }

    @Test
    @DisplayName("post collect delegates specified target round")
    void postCollectDelegatesSpecifiedTargetRound() throws Exception {
        Mockito.when(collectService.collect(1103))
                .thenReturn(new CollectResponse(2, 1, 0, 1103, List.of(), false, null, false));

        mockMvc.perform(post("/api/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRound\":\"1103\"}"))
                .andDo(document("winning-numbers-refresh",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("targetRound").type(JsonFieldType.STRING).description("수집 종료 대상 회차(선택)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("요청 성공 여부"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("수집 결과"),
                                fieldWithPath("data.collected").type(JsonFieldType.NUMBER).description("새로 저장된 회차 수"),
                                fieldWithPath("data.skipped").type(JsonFieldType.NUMBER).description("건너뛴 회차 수"),
                                fieldWithPath("data.failed").type(JsonFieldType.NUMBER).description("수집 실패 회차 수"),
                                fieldWithPath("data.latestRound").type(JsonFieldType.NUMBER).description("수집 완료 후 최신 회차"),
                                fieldWithPath("data.failedRounds").type(JsonFieldType.ARRAY).description("실패한 회차 목록"),
                                fieldWithPath("data.truncated").type(JsonFieldType.BOOLEAN).description("호출당 최대 수집 한도 도달 여부"),
                                fieldWithPath("data.nextRound").type(JsonFieldType.NULL).optional().description("다음 호출 시작 회차"),
                                fieldWithPath("data.notDrawn").type(JsonFieldType.BOOLEAN).description("추첨 미완료 회차로 중단 여부"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("오류 정보")
                        )))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(jsonPath("$.data.collected").value(2))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.nextRound").doesNotExist());
    }

    @Test
    @DisplayName("post collect returns 502 on external api failure")
    void postCollectReturns502OnExternalApiFailure() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_FAILURE));

        mockMvc.perform(post("/api/winning-numbers/refresh"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_API_FAILURE"));
    }

    @Test
    @DisplayName("post collect returns 400 on invalid target round")
    void postCollectReturns400OnInvalidTargetRound() throws Exception {
        mockMvc.perform(post("/api/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRound\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));
    }
}
