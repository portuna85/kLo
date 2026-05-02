package com.kraft.lotto.feature.winningnumber.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "winning_numbers")
public class WinningNumberEntity {

    @Id
    @Column(name = "round", nullable = false)
    private Integer round;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "n1", nullable = false)
    private Integer n1;

    @Column(name = "n2", nullable = false)
    private Integer n2;

    @Column(name = "n3", nullable = false)
    private Integer n3;

    @Column(name = "n4", nullable = false)
    private Integer n4;

    @Column(name = "n5", nullable = false)
    private Integer n5;

    @Column(name = "n6", nullable = false)
    private Integer n6;

    @Column(name = "bonus_number", nullable = false)
    private Integer bonusNumber;

    @Column(name = "first_prize", nullable = false)
    private Long firstPrize;

    @Column(name = "first_winners", nullable = false)
    private Integer firstWinners;

    @Column(name = "total_sales", nullable = false)
    private Long totalSales;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected WinningNumberEntity() {
    }

    public WinningNumberEntity(Integer round,
                               LocalDate drawDate,
                               Integer n1, Integer n2, Integer n3,
                               Integer n4, Integer n5, Integer n6,
                               Integer bonusNumber,
                               Long firstPrize,
                               Integer firstWinners,
                               Long totalSales,
                               LocalDateTime createdAt) {
        this.round = round;
        this.drawDate = drawDate;
        this.n1 = n1;
        this.n2 = n2;
        this.n3 = n3;
        this.n4 = n4;
        this.n5 = n5;
        this.n6 = n6;
        this.bonusNumber = bonusNumber;
        this.firstPrize = firstPrize;
        this.firstWinners = firstWinners;
        this.totalSales = totalSales;
        this.createdAt = createdAt;
    }

    public Integer getRound() { return round; }
    public LocalDate getDrawDate() { return drawDate; }
    public Integer getN1() { return n1; }
    public Integer getN2() { return n2; }
    public Integer getN3() { return n3; }
    public Integer getN4() { return n4; }
    public Integer getN5() { return n5; }
    public Integer getN6() { return n6; }
    public Integer getBonusNumber() { return bonusNumber; }
    public Long getFirstPrize() { return firstPrize; }
    public Integer getFirstWinners() { return firstWinners; }
    public Long getTotalSales() { return totalSales; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
