package com.kraft.lotto.feature.winningnumber.infrastructure;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LottoFetchLogRepository extends JpaRepository<LottoFetchLogEntity, Long> {
    long deleteByFetchedAtBefore(LocalDateTime cutoff);
}
