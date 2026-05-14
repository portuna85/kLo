package com.kraft.lotto.feature.statistics.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WinningNumberFrequencySummaryRepository
        extends JpaRepository<WinningNumberFrequencySummaryEntity, Integer> {

    List<WinningNumberFrequencySummaryEntity> findAllByOrderByBallAsc();
}

