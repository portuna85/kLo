package com.kraft.lotto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
    @DisplayName("tests for KraftLottoApplicationTests")
class KraftLottoApplicationTests {

    @Test
    @DisplayName("context loads")
    void contextLoads() {
    }
}

