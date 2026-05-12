package com.kraft.lotto;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration")
@ConfigurationPropertiesScan
@EnableScheduling
@EnableCaching
public class KraftLottoApplication {

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("winningNumberFrequency");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)
                .recordStats()
                .expireAfterWrite(Duration.ofMinutes(45)));
        return cacheManager;
    }

    public static void main(String[] args) {
        SpringApplication.run(KraftLottoApplication.class, args);
    }
}
