package com.kraft.lotto.feature.winningnumber.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "first_accum_amount", nullable = false)
    private Long firstAccumAmount;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "raw_json")
    private String rawJson;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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
        this(round, drawDate, n1, n2, n3, n4, n5, n6, bonusNumber,
                firstPrize, firstWinners, totalSales, 0L, null, createdAt, createdAt, createdAt);
    }

    public WinningNumberEntity(Integer round,
                               LocalDate drawDate,
                               Integer n1, Integer n2, Integer n3,
                               Integer n4, Integer n5, Integer n6,
                               Integer bonusNumber,
                               Long firstPrize,
                               Integer firstWinners,
                               Long totalSales,
                               Long firstAccumAmount,
                               String rawJson,
                               LocalDateTime fetchedAt,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
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
        this.firstAccumAmount = firstAccumAmount;
        this.rawJson = rawJson;
        this.fetchedAt = fetchedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void updateFrom(WinningNumberEntity source, LocalDateTime updatedAt) {
        this.drawDate = source.drawDate;
        this.n1 = source.n1;
        this.n2 = source.n2;
        this.n3 = source.n3;
        this.n4 = source.n4;
        this.n5 = source.n5;
        this.n6 = source.n6;
        this.bonusNumber = source.bonusNumber;
        this.firstPrize = source.firstPrize;
        this.firstWinners = source.firstWinners;
        this.totalSales = source.totalSales;
        this.firstAccumAmount = source.firstAccumAmount;
        this.rawJson = source.rawJson;
        this.fetchedAt = source.fetchedAt;
        this.updatedAt = updatedAt;
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
    public Long getFirstAccumAmount() { return firstAccumAmount; }
    public String getRawJson() { return rawJson; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
