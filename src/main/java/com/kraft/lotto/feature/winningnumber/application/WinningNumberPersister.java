package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 당첨번호 저장 책임을 캡슐화한다.
 *
 * <p>회차별 독립 트랜잭션으로 저장해 수집 루프에서 특정 회차 저장 실패가
 * 다른 회차 처리 결과까지 롤백하지 않도록 한다.</p>
 */
@Component
public class WinningNumberPersister {

    private final WinningNumberRepository repository;
    private final Clock clock;

    public WinningNumberPersister(WinningNumberRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    WinningNumberPersister(WinningNumberRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean saveIfAbsent(int round, WinningNumber winningNumber) {
        if (repository.existsByRound(round)) {
            return false;
        }
        repository.save(WinningNumberMapper.toEntity(winningNumber, LocalDateTime.now(clock)));
        return true;
    }
}
