package com.kraft.lotto.feature.winningnumber.application;

/**
 * 외부 로또 당첨번호 API 호출/파싱 단계의 시스템 예외.
 * 비즈니스 계층에서 {@code EXTERNAL_API_FAILURE}로 매핑된다.
 */
public class LottoApiClientException extends RuntimeException {

    private final Integer responseCode;
    private final String rawResponse;

    public LottoApiClientException(String message) {
        this(message, null, null, null);
    }

    public LottoApiClientException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    public LottoApiClientException(String message, Integer responseCode, String rawResponse) {
        this(message, null, responseCode, rawResponse);
    }

    public LottoApiClientException(String message, Throwable cause, Integer responseCode, String rawResponse) {
        super(message, cause);
        this.responseCode = responseCode;
        this.rawResponse = rawResponse;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
