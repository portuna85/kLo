package com.kraft.lotto.feature.winningnumber.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.infra.config.KraftApiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * {@link LottoApiClient} 구현 선택을 담당한다.
 * - {@code kraft.api.client=dhlottery} → {@link DhLotteryApiClient}
 * - 그 외(기본 포함) → {@link MockLottoApiClient}
 */
@Configuration
public class LottoApiClientConfig {

    private static final String CLIENT_DHLOTTERY = "dhlottery";

    /** 회차 1184 (2025년 12월 추첨)에 가까운 보수적 기본값. 운영 시에는 dhlottery 클라이언트가 사용된다. */
    static final int MOCK_DEFAULT_LATEST_ROUND = 1200;

    @Bean
    public LottoApiClient lottoApiClient(KraftApiProperties properties) {
        String client = properties.client() == null ? "" : properties.client().trim().toLowerCase();
        if (CLIENT_DHLOTTERY.equals(client)) {
            return new DhLotteryApiClient(RestClient.builder().build(), new ObjectMapper(), properties.url());
        }
        return new MockLottoApiClient(MOCK_DEFAULT_LATEST_ROUND);
    }
}
