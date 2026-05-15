package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberDto;
import com.kraft.lotto.feature.winningnumber.web.dto.WinningNumberPageDto;
import com.kraft.lotto.support.BusinessException;
import com.kraft.lotto.support.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WinningNumberQueryService {

    public static final int MAX_ROUND = 3000;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final WinningNumberRepository repository;

    public WinningNumberQueryService(WinningNumberRepository repository) {
        this.repository = repository;
    }

    public WinningNumberDto getLatest() {
        return repository.findTopByOrderByRoundDesc()
                .map(WinningNumberMapper::toDomain)
                .map(WinningNumberDto::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.WINNING_NUMBER_NOT_FOUND));
    }

    public WinningNumberDto getByRound(int round) {
        if (round <= 0 || round > MAX_ROUND) {
            throw new BusinessException(ErrorCode.LOTTO_INVALID_TARGET_ROUND);
        }
        return repository.findById(round)
                .map(WinningNumberMapper::toDomain)
                .map(WinningNumberDto::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.WINNING_NUMBER_NOT_FOUND));
    }

    public WinningNumberPageDto list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        var entities = repository.findAllByOrderByRoundDesc(PageRequest.of(safePage, safeSize));
        var mapped = entities
                .map(WinningNumberMapper::toDomain)
                .map(WinningNumberDto::from);
        return WinningNumberPageDto.from(mapped);
    }
}
