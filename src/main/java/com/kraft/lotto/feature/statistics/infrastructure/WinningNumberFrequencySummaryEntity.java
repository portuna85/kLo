package com.kraft.lotto.feature.statistics.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "winning_number_frequency_summary")
public class WinningNumberFrequencySummaryEntity {

    @Id
    @Column(name = "ball", nullable = false)
    private Integer ball;

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    @Column(name = "last_calculated_round", nullable = false)
    private int lastCalculatedRound;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WinningNumberFrequencySummaryEntity() {
    }

    public WinningNumberFrequencySummaryEntity(Integer ball, long hitCount, int lastCalculatedRound) {
        this.ball = ball;
        this.hitCount = hitCount;
        this.lastCalculatedRound = lastCalculatedRound;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getBall() {
        return ball;
    }

    public long getHitCount() {
        return hitCount;
    }

    public int getLastCalculatedRound() {
        return lastCalculatedRound;
    }
}

