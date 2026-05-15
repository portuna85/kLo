package com.kraft.lotto.infra.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API contract drift guard")
class ApiContractDriftTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("OpenAPI paths must include canonical /api/v1 endpoints")
    void openApiIncludesCanonicalV1Endpoints() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        JsonNode pathsNode = root.path("paths");
        Set<String> paths = new LinkedHashSet<>();
        Iterator<String> fields = pathsNode.fieldNames();
        while (fields.hasNext()) {
            paths.add(fields.next());
        }

        assertThat(paths).contains(
                "/api/v1/recommend",
                "/api/v1/recommend/rules",
                "/api/v1/winning-numbers/latest",
                "/api/v1/winning-numbers/{round}",
                "/api/v1/winning-numbers",
                "/api/v1/winning-numbers/stats/frequency",
                "/api/v1/winning-numbers/stats/frequency-summary",
                "/api/v1/winning-numbers/stats/combination-prize-history",
                "/api/v1/winning-numbers/refresh",
                "/admin/lotto/draws/collect-next",
                "/admin/lotto/draws/collect-missing",
                "/admin/lotto/draws/{drwNo}/refresh",
                "/admin/lotto/draws/backfill",
                "/admin/smoke-auth-check",
                "/admin/lotto/jobs/backfill",
                "/admin/lotto/jobs/{jobId}"
        );
    }
}
