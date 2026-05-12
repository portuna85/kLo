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

@WebMvcTest(controllers = WinningNumberCollectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ExtendWith(RestDocumentationExtension.class)
    @DisplayName("tests for WinningNumberCollectControllerTest")
class WinningNumberCollectControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @MockitoBean
    LottoCollectionService collectService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
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
                                fieldWithPath("targetRound").type(JsonFieldType.STRING)
                                        .optional()
                                        .description("?ㅻ챸")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("?ㅻ챸"),
                                fieldWithPath("data.collected").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.skipped").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.failed").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.latestRound").type(JsonFieldType.NUMBER).description("?ㅻ챸"),
                                fieldWithPath("data.failedRounds").type(JsonFieldType.ARRAY).description("?ㅻ챸"),
                                fieldWithPath("data.truncated").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("data.nextRound").type(JsonFieldType.NUMBER).optional().description("?ㅻ챸"),
                                fieldWithPath("data.notDrawn").type(JsonFieldType.BOOLEAN).description("?ㅻ챸"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("?ㅻ챸")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collected").value(2))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.nextRound").doesNotExist());
    }

    @Test
    @DisplayName("post collect returns502 on external api failure")
    void postCollectReturns502OnExternalApiFailure() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_FAILURE));

        mockMvc.perform(post("/api/winning-numbers/refresh"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_API_FAILURE"));
    }

    @Test
    @DisplayName("post collect returns400 on invalid target round")
    void postCollectReturns400OnInvalidTargetRound() throws Exception {
        mockMvc.perform(post("/api/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRound\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));
    }
}
