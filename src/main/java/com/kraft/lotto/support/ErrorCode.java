package com.kraft.lotto.support;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    LOTTO_INVALID_COUNT(HttpStatus.BAD_REQUEST, "추천 개수는 1~10 사이여야 합니다."),
    LOTTO_INVALID_NUMBER(HttpStatus.BAD_REQUEST, "유효하지 않은 로또 번호입니다."),
    LOTTO_INVALID_TARGET_ROUND(HttpStatus.BAD_REQUEST, "targetRound는 1 이상이어야 합니다."),
    LOTTO_INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "페이지 파라미터가 유효하지 않습니다."),
    REQUEST_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다."),
    LOTTO_GENERATION_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "추천 조합 생성 시도 한도를 초과했습니다."),
    WINNING_NUMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 회차의 당첨번호를 찾을 수 없습니다."),
    EXTERNAL_API_FAILURE(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다."),
    COLLECT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "당첨번호 수집에 실패했습니다."),
    UNAUTHORIZED_ADMIN(HttpStatus.UNAUTHORIZED, "관리자 인증이 필요합니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
