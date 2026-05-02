package com.kraft.lotto.feature.winningnumber.web.dto;

/**
 * 번호별 출현 빈도 응답 DTO. {@code number}는 1~45 범위의 본번호이며 보너스 번호는 집계에서 제외된다.
 */
public record NumberFrequencyDto(int number, long count) {
}
