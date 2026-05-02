package com.kraft.lotto.feature.winningnumber.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WinningNumberRepository extends JpaRepository<WinningNumberEntity, Integer> {

    Optional<WinningNumberEntity> findTopByOrderByRoundDesc();

    @Query("select max(w.round) from WinningNumberEntity w")
    Optional<Integer> findMaxRound();

    boolean existsByRound(int round);

    Page<WinningNumberEntity> findAllByOrderByRoundDesc(Pageable pageable);

    @Query("select w from WinningNumberEntity w order by w.round asc")
    List<WinningNumberEntity> findAllOrderByRoundAsc();
}
