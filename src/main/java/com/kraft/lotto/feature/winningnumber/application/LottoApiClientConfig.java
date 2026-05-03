package com.kraft.lotto.feature.winningnumber.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftApiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@link LottoApiClient} 구현 선택을 담당한다.
 * <ul>
 *   <li>{@code kraft.api.client=dhlottery} 또는 {@code real} → {@link DhLotteryApiClient}</li>
 *   <li>그 외(기본 포함) → {@link MockLottoApiClient}</li>
 * </ul>
 * 외부 표기({@code real})와 내부 구현체({@code dhlottery})를 동시에 허용하여
 * .env / 운영 매니페스트에서 직관적인 토큰을 그대로 사용할 수 있게 한다.
 */
@Configuration
public class LottoApiClientConfig {

    private static final Set<String> DHLOTTERY_TOKENS = Set.of("dhlottery", "real");

    /** 회차 1184 (2025년 12월 추첨)에 가까운 보수적 기본값. 운영 시에는 dhlottery 클라이언트가 사용된다. */
    static final int MOCK_DEFAULT_LATEST_ROUND = 1200;

    @Bean
    public RestClient lottoRestClient(KraftApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeoutMs());
        requestFactory.setReadTimeout(properties.readTimeoutMs());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public LottoApiClient lottoApiClient(KraftApiProperties properties,
                                         RestClient lottoRestClient,
                                         ObjectProvider<ObjectMapper> objectMapperProvider,
                                         ObjectProvider<MeterRegistry> meterRegistryProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        String client = properties.client() == null ? "" : properties.client().trim().toLowerCase();
        if (DHLOTTERY_TOKENS.contains(client)) {
            return new DhLotteryApiClient(
                    lottoRestClient,
                    objectMapper,
                    properties.url(),
                    properties.maxRetries(),
                    properties.retryBackoffMs(),
                    meterRegistryProvider.getIfAvailable()
            );
        }
        return new MockLottoApiClient(MOCK_DEFAULT_LATEST_ROUND);
    }
}
