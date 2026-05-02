package com.kraft.lotto.feature.winningnumber.web.dto;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.time.LocalDate;
import java.util.List;

public record WinningNumberDto(
        int round,
        LocalDate drawDate,
        List<Integer> numbers,
        int bonusNumber,
        long firstPrize,
        int firstWinners,
        long totalSales
) {

    public static WinningNumberDto from(WinningNumber wn) {
        return new WinningNumberDto(
                wn.round(),
                wn.drawDate(),
                wn.combination().numbers(),
                wn.bonusNumber(),
                wn.firstPrize(),
                wn.firstWinners(),
                wn.totalSales()
        );
    }
}
