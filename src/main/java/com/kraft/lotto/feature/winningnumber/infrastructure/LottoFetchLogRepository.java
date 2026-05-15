package com.kraft.lotto.feature.winningnumber.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LottoFetchLogRepository extends JpaRepository<LottoFetchLogEntity, Long> {
    long deleteByFetchedAtBefore(LocalDateTime cutoff);

    @Query("select l.id from LottoFetchLogEntity l where l.fetchedAt < :cutoff order by l.id")
    List<Long> findIdsByFetchedAtBefore(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
