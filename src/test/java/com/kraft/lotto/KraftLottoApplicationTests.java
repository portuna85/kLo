package com.kraft.lotto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("애플리케이션 컨텍스트 로드 테스트")
class KraftLottoApplicationTests {

    @Test
    @DisplayName("컨텍스트가 정상적으로 로드된다")
    void contextLoads() {
    }
}

