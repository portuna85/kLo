package com.kraft.lotto.feature.winningnumber.infrastructure;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.time.LocalDateTime;
import java.util.List;

public final class WinningNumberMapper {

    private WinningNumberMapper() {
    }

    public static WinningNumber toDomain(WinningNumberEntity entity) {
        LottoCombination combination = new LottoCombination(List.of(
                entity.getN1(), entity.getN2(), entity.getN3(),
                entity.getN4(), entity.getN5(), entity.getN6()
        ));
        return new WinningNumber(
                entity.getRound(),
                entity.getDrawDate(),
                combination,
                entity.getBonusNumber(),
                entity.getFirstPrize(),
                entity.getFirstWinners(),
                entity.getTotalSales(),
                entity.getFirstAccumAmount(),
                entity.getRawJson(),
                entity.getFetchedAt()
        );
    }

    public static WinningNumberEntity toEntity(WinningNumber domain, LocalDateTime createdAt) {
        LocalDateTime fetchedAt = domain.fetchedAt() == null ? createdAt : domain.fetchedAt();
        return toEntity(domain, fetchedAt, createdAt, createdAt);
    }

    public static WinningNumberEntity toEntity(WinningNumber domain,
                                               LocalDateTime fetchedAt,
                                               LocalDateTime createdAt,
                                               LocalDateTime updatedAt) {
        List<Integer> nums = domain.combination().numbers();
        return new WinningNumberEntity(
                domain.round(),
                domain.drawDate(),
                nums.get(0), nums.get(1), nums.get(2),
                nums.get(3), nums.get(4), nums.get(5),
                domain.bonusNumber(),
                domain.firstPrize(),
                domain.firstWinners(),
                domain.totalSales(),
                domain.firstAccumAmount(),
                domain.rawJson(),
                fetchedAt,
                createdAt,
                updatedAt
        );
    }
}
