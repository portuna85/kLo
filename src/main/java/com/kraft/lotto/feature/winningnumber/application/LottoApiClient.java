package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 로또 당첨번호 API에 대한 어댑터.
 * 구현체는 회차(round)에 해당하는 {@link WinningNumber}를 반환하거나,
 * 아직 추첨되지 않은 회차이면 {@link Optional#empty()}를 반환한다.
 *
 * 네트워크/파싱 실패 등 시스템 오류는 {@link LottoApiClientException}로 통일하여 던진다.
 */
public interface LottoApiClient {

    Optional<WinningNumber> fetch(int round);
}

/**
 * 외부 로또 API 상태를 확인하는 커스텀 actuator-style endpoint.
 */
@RestController
class LottoApiHealthController {
    private final LottoApiClient lottoApiClient;
    public LottoApiHealthController(LottoApiClient lottoApiClient) {
        this.lottoApiClient = lottoApiClient;
    }
    @GetMapping("/actuator/lottoApiHealth")
    public String health() {
        try {
            lottoApiClient.fetch(1);
            return "UP";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }
}
