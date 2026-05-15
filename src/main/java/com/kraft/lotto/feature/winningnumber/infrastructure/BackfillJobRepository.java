package com.kraft.lotto.feature.winningnumber.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackfillJobRepository extends JpaRepository<BackfillJobEntity, String> {

    long deleteByCompletedAtBeforeAndStatusIn(LocalDateTime cutoff, List<String> terminalStatuses);

    @Query("""
            select b.jobId
            from BackfillJobEntity b
            where b.status in :terminalStatuses
            order by b.completedAt asc, b.createdAt asc
            """)
    List<String> findTerminalJobIds(@Param("terminalStatuses") List<String> terminalStatuses, Pageable pageable);
}

