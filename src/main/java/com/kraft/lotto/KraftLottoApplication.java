package com.kraft.lotto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KraftLottoApplication {

    public static void main(String[] args) {
        SpringApplication.run(KraftLottoApplication.class, args);
    }
}
