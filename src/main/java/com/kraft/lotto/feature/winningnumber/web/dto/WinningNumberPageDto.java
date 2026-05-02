package com.kraft.lotto.feature.winningnumber.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지네이션 응답 공통 포맷.
 * Spring Data {@link Page}의 직렬화 형태가 향후 변경될 수 있으므로 명시적 DTO로 감싼다.
 */
public record WinningNumberPageDto(
        List<WinningNumberDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static WinningNumberPageDto from(Page<WinningNumberDto> page) {
        return new WinningNumberPageDto(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
