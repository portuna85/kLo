package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.WinningNumberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
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
        repository.save(WinningNumberMapper.toEntity(winningNumber, LocalDateTime.now(clock)));
        recordDbSaveLatency(started, "save_if_absent");
        return true;
    }

    @Transactional
    public boolean upsert(WinningNumber winningNumber) {
        long started = System.nanoTime();
        LocalDateTime now = LocalDateTime.now(clock);
        boolean inserted = repository.findById(winningNumber.round())
                .map(existing -> {
                    existing.updateFrom(WinningNumberMapper.toEntity(winningNumber, now), now);
                    return false;
                })
                .orElseGet(() -> {
                    repository.save(WinningNumberMapper.toEntity(winningNumber, now));
                    return true;
                });
        recordDbSaveLatency(started, inserted ? "insert" : "update");
        return inserted;
    }

    private void recordDbSaveLatency(long started, String mode) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.timer("kraft.winningnumber.db.save.latency", "mode", mode)
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
    }
}
