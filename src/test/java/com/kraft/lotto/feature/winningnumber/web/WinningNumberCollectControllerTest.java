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
import com.kraft.lotto.infra.web.LegacyApiDeprecationHeaderFilter;
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
@DisplayName("WinningNumberCollectController")
class WinningNumberCollectControllerTest {

    @Mock
    LottoCollectionService collectService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WinningNumberCollectController(collectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new LegacyApiDeprecationHeaderFilter(
                        "@1785542399",
                        "Fri, 31 Jul 2026 23:59:59 GMT",
                        true
                ))
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void postCollectDelegatesNullTargetRoundWhenBodyAbsent() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenReturn(new CollectResponse(3, 0, 0, 0, 1103, List.of(), true, 2000, false, true));

        mockMvc.perform(post("/api/winning-numbers/refresh"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "@1785542399"))
                .andExpect(header().string("Sunset", "Fri, 31 Jul 2026 23:59:59 GMT"))
                .andExpect(header().string("Link", "</api/v1/winning-numbers/refresh>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collected").value(3))
                .andExpect(jsonPath("$.data.updated").value(0))
                .andExpect(jsonPath("$.data.latestRound").value(1103))
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andExpect(jsonPath("$.data.nextRound").value(2000));
    }

    @Test
    void postCollectDelegatesSpecifiedTargetRound() throws Exception {
        Mockito.when(collectService.collect(1103))
                .thenReturn(new CollectResponse(2, 1, 0, 0, 1103, List.of(), false, null, false, true));

        mockMvc.perform(post("/api/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRound\":1103}"))
                .andDo(document("winning-numbers-refresh",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("targetRound").type(JsonFieldType.NUMBER).description("Target round (optional)")
                        ),
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("Success"),
                                fieldWithPath("data").type(JsonFieldType.OBJECT).description("Collect result"),
                                fieldWithPath("data.collected").type(JsonFieldType.NUMBER).description("Inserted rounds"),
                                fieldWithPath("data.updated").type(JsonFieldType.NUMBER).description("Updated rounds"),
                                fieldWithPath("data.skipped").type(JsonFieldType.NUMBER).description("Skipped rounds"),
                                fieldWithPath("data.failed").type(JsonFieldType.NUMBER).description("Failed rounds"),
                                fieldWithPath("data.latestRound").type(JsonFieldType.NUMBER).description("Latest round"),
                                fieldWithPath("data.failedRounds").type(JsonFieldType.ARRAY).description("Failed round list"),
                                fieldWithPath("data.truncated").type(JsonFieldType.BOOLEAN).description("Truncated by limit"),
                                fieldWithPath("data.nextRound").type(JsonFieldType.NULL).optional().description("Next round for follow-up"),
                                fieldWithPath("data.notDrawn").type(JsonFieldType.BOOLEAN).description("Not drawn flag"),
                                fieldWithPath("data.dataChanged").type(JsonFieldType.BOOLEAN).description("Data changed flag"),
                                fieldWithPath("error").type(JsonFieldType.NULL).optional().description("Error")
                        )))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "@1785542399"))
                .andExpect(jsonPath("$.data.collected").value(2))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.nextRound").doesNotExist());
    }

    @Test
    void postCollectReturns502OnExternalApiFailure() throws Exception {
        Mockito.when(collectService.collect(isNull()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_FAILURE));

        mockMvc.perform(post("/api/winning-numbers/refresh"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_API_FAILURE"));
    }

    @Test
    void postCollectReturns400OnInvalidTargetRound() throws Exception {
        mockMvc.perform(post("/api/winning-numbers/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetRound\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LOTTO_INVALID_TARGET_ROUND"));
    }
}
