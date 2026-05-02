package com.kraft.lotto.feature.winningnumber.application;

/**
 * 외부 로또 당첨번호 API 호출/파싱 단계의 시스템 예외.
 * 비즈니스 계층에서 {@code EXTERNAL_API_FAILURE}로 매핑된다.
 */
public class LottoApiClientException extends RuntimeException {

    public LottoApiClientException(String message) {
        super(message);
    }

    public LottoApiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
