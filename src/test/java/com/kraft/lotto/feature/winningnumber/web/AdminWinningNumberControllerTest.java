package com.kraft.lotto.feature.winningnumber.web;

import static org.mockito.ArgumentMatchers.isNull;
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

import com.kraft.lotto.feature.winningnumber.application.WinningNumberCollectService;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import com.kraft.lotto.support.GlobalExceptionHandler;
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

@WebMvcTest(controllers = AdminWinningNumberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("AdminWinningNumberController WebMvc")
class AdminWinningNumberControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @MockitoBean
    WinningNumberCollectService collectService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

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
                        .content("{\"targetRound\":\"1103\"}"))
                .andDo(document("admin-winning-numbers-refresh",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("targetRound").type(JsonFieldType.STRING)
                                        .optional()
                                        .description("수집 종료 대상 회차(생략 시 미추첨 회차 전까지 자동 수집)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("요청 성공 여부"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("수집 결과 요약"),
                                fieldWithPath("data.collected").type(JsonFieldType.NUMBER).description("새로 저장된 회차 수"),
                                fieldWithPath("data.skipped").type(JsonFieldType.NUMBER).description("이미 존재하여 건너뛴 회차 수"),
                                fieldWithPath("data.failed").type(JsonFieldType.NUMBER).description("검증/저장 실패 회차 수"),
                                fieldWithPath("data.latestRound").type(JsonFieldType.NUMBER).description("수집 후 최신 회차"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("오류 정보(성공 시 null)")
                        )
                ))
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

    @Test
    @DisplayName("POST /collect 는 targetRound가 1 미만이면 400 LOTTO_INVALID_TARGET_ROUND 를 반환한다")
    void postCollectReturns400OnInvalidTargetRound() throws Exception {
        mockMvc.perform(post("/api/admin/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRound\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));
    }
}
