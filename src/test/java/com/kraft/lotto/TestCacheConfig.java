package com.kraft.lotto;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestCacheConfig {

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("winningNumberFrequency", "combinationPrizeHistory");
    }
}
