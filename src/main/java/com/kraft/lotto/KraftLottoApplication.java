package com.kraft.lotto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration")
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
@EnableCaching
public class KraftLottoApplication {

    public static void main(String[] args) {
        SpringApplication.run(KraftLottoApplication.class, args);
    }
}
