package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final MeterRegistry meterRegistry;

    @Autowired
    public WinningNumberPersister(WinningNumberRepository repository,
                                  ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(repository, Clock.systemDefaultZone(), meterRegistryProvider.getIfAvailable());
    }

    WinningNumberPersister(WinningNumberRepository repository, Clock clock) {
        this(repository, clock, null);
    }

    WinningNumberPersister(WinningNumberRepository repository, Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public boolean saveIfAbsent(int round, WinningNumber winningNumber) {
        long started = System.nanoTime();
        if (repository.existsByRound(round)) {
            return false;
        }
        try {
            repository.save(WinningNumberMapper.toEntity(winningNumber, LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
        recordDbSaveLatency(started, "save_if_absent");
        return true;
    }

    @Transactional
    public UpsertOutcome upsert(WinningNumber winningNumber) {
        long started = System.nanoTime();
        LocalDateTime now = LocalDateTime.now(clock);
        UpsertOutcome outcome = repository.findById(winningNumber.round())
                .map(existing -> {
                    var incoming = WinningNumberMapper.toEntity(winningNumber, now);
                    if (isSame(existing, incoming)) {
                        return UpsertOutcome.UNCHANGED;
                    }
                    existing.updateFrom(incoming, now);
                    return UpsertOutcome.UPDATED;
                })
                .orElseGet(() -> {
                    try {
                        repository.save(WinningNumberMapper.toEntity(winningNumber, now));
                        return UpsertOutcome.INSERTED;
                    } catch (DataIntegrityViolationException ex) {
                        return UpsertOutcome.UNCHANGED;
                    }
                });
        recordDbSaveLatency(started, switch (outcome) {
            case INSERTED -> "insert";
            case UPDATED -> "update";
            case UNCHANGED -> "unchanged";
        });
        return outcome;
    }

    private static boolean isSame(com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberEntity existing,
                                  com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberEntity incoming) {
        return Objects.equals(existing.getDrawDate(), incoming.getDrawDate())
                && Objects.equals(existing.getN1(), incoming.getN1())
                && Objects.equals(existing.getN2(), incoming.getN2())
                && Objects.equals(existing.getN3(), incoming.getN3())
                && Objects.equals(existing.getN4(), incoming.getN4())
                && Objects.equals(existing.getN5(), incoming.getN5())
                && Objects.equals(existing.getN6(), incoming.getN6())
                && Objects.equals(existing.getBonusNumber(), incoming.getBonusNumber())
                && Objects.equals(existing.getFirstPrize(), incoming.getFirstPrize())
                && Objects.equals(existing.getFirstWinners(), incoming.getFirstWinners())
                && Objects.equals(existing.getTotalSales(), incoming.getTotalSales())
                && Objects.equals(existing.getFirstAccumAmount(), incoming.getFirstAccumAmount());
    }

    private void recordDbSaveLatency(long started, String mode) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.timer("kraft.winningnumber.db.save.latency", "mode", mode)
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }
}
