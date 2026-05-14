package com.kraft.lotto.feature.winningnumber.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WinningNumberRepository extends JpaRepository<WinningNumberEntity, Integer> {

    Optional<WinningNumberEntity> findTopByOrderByRoundDesc();

    @Query("select max(w.round) from WinningNumberEntity w")
    Optional<Integer> findMaxRound();

    @Query("select w.round from WinningNumberEntity w where w.round between :from and :to")
    Set<Integer> findRoundsBetween(int from, int to);

    boolean existsByRound(int round);

    Page<WinningNumberEntity> findAllByOrderByRoundDesc(Pageable pageable);

    @Query("select w from WinningNumberEntity w order by w.round asc")
    List<WinningNumberEntity> findAllOrderByRoundAsc();

    @Query("""
            select w.n1 as n1, w.n2 as n2, w.n3 as n3,
                   w.n4 as n4, w.n5 as n5, w.n6 as n6
            from WinningNumberEntity w
            order by w.round asc
            """)
    List<CombinationRow> findAllCombinationsOrderByRoundAsc();

    @Query("select w.n1, w.n2, w.n3, w.n4, w.n5, w.n6 from WinningNumberEntity w")
    List<Object[]> findAllNumbersForFrequency();

    @Query(value = """
            select sub.round as round, sub.draw_date as drawDate, sub.prize_rank as prizeRank
            from (
                select w.round, w.draw_date, 1 as prize_rank
                from winning_numbers w
                where w.n1 in (:n1, :n2, :n3, :n4, :n5, :n6)
                  and w.n2 in (:n1, :n2, :n3, :n4, :n5, :n6)
                  and w.n3 in (:n1, :n2, :n3, :n4, :n5, :n6)
                  and w.n4 in (:n1, :n2, :n3, :n4, :n5, :n6)
                  and w.n5 in (:n1, :n2, :n3, :n4, :n5, :n6)
                  and w.n6 in (:n1, :n2, :n3, :n4, :n5, :n6)
                union all
                select w.round, w.draw_date, 2 as prize_rank
                from winning_numbers w
                where (
                    (case when w.n1 in (:n1, :n2, :n3, :n4, :n5, :n6) then 1 else 0 end)
                  + (case when w.n2 in (:n1, :n2, :n3, :n4, :n5, :n6) then 1 else 0 end)
                  + (case when w.n3 in (:n1, :n2, :n3, :n4, :n5, :n6) then 1 else 0 end)
                  + (case when w.n4 in (:n1, :n2, :n3, :n4, :n5, :n6) then 1 else 0 end)
                  + (case when w.n5 in (:n1, :n2, :n3, :n4, :n5, :n6) then 1 else 0 end)
                  + (case when w.n6 in (:n1, :n2, :n3, :n4, :n5, :n6) then 1 else 0 end)
                ) = 5
                  and w.bonus_number in (:n1, :n2, :n3, :n4, :n5, :n6)
            ) sub
            order by sub.round desc
            """, nativeQuery = true)
    List<PrizeHitWithRankRow> findPrizeHitsByNumbers(
            Integer n1, Integer n2, Integer n3, Integer n4, Integer n5, Integer n6);

    interface CombinationRow {
        Integer getN1();
        Integer getN2();
        Integer getN3();
        Integer getN4();
        Integer getN5();
        Integer getN6();
    }

    interface PrizeHitWithRankRow {
        Integer getRound();
        LocalDate getDrawDate();
        Integer getPrizeRank();
    }
}
