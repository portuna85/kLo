package com.kraft.lotto.feature.winningnumber.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftApiProperties;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

    /** mock latest round를 계산할 수 없는 경우의 보수적 fallback. */
    static final int MOCK_DEFAULT_LATEST_ROUND = 1200;

    @Bean
    public RestClient lottoRestClient(KraftApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "kraft-lotto/0.0.1 dhlottery-collector")
                .build();
    }

    @Bean
    public LottoApiClient lottoApiClient(KraftApiProperties properties,
                                         RestClient lottoRestClient,
                                         ObjectProvider<ObjectMapper> objectMapperProvider,
                                         ObjectProvider<MeterRegistry> meterRegistryProvider,
                                         ObjectProvider<WinningNumberRepository> winningNumberRepositoryProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        String client = properties.client() == null ? "" : properties.client().trim().toLowerCase();
        int resolvedMockLatestRound = resolveMockLatestRound(properties, winningNumberRepositoryProvider.getIfAvailable());
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
        return new MockLottoApiClient(resolvedMockLatestRound);
    }

    private static int resolveMockLatestRound(KraftApiProperties properties, WinningNumberRepository repository) {
        if (properties.mockLatestRound() > 0) {
            return properties.mockLatestRound();
        }
        if (repository != null) {
            int latestStoredRound = repository.findMaxRound().orElse(0);
            if (latestStoredRound > 0) {
                return latestStoredRound + 1;
            }
        }
        return MOCK_DEFAULT_LATEST_ROUND;
    }
}
