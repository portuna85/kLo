package com.kraft.lotto.feature.winningnumber.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "backfill_jobs")
public class BackfillJobEntity {

    @Id
    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "from_round", nullable = false)
    private Integer fromRound;

    @Column(name = "to_round", nullable = false)
    private Integer toRound;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected BackfillJobEntity() {
    }

    public BackfillJobEntity(String jobId, String status, Integer fromRound, Integer toRound, LocalDateTime createdAt) {
        this.jobId = jobId;
        this.status = status;
        this.fromRound = fromRound;
        this.toRound = toRound;
        this.createdAt = createdAt;
    }

    public String getJobId() { return jobId; }
    public String getStatus() { return status; }
    public Integer getFromRound() { return fromRound; }
    public Integer getToRound() { return toRound; }
    public String getResultJson() { return resultJson; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }

    public void markRunning(LocalDateTime startedAt) {
        this.status = "RUNNING";
        this.startedAt = startedAt;
    }

    public void markSucceeded(String resultJson, LocalDateTime completedAt) {
        this.status = "SUCCEEDED";
        this.resultJson = resultJson;
        this.completedAt = completedAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage, LocalDateTime completedAt) {
        this.status = "FAILED";
        this.errorMessage = errorMessage == null ? "unknown error" : truncate(errorMessage, 500);
        this.completedAt = completedAt;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

