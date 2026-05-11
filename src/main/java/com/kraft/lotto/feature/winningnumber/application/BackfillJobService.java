package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.web.dto.BackfillJobStatusResponse;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class BackfillJobService {

    private final LottoCollectionService collectionService;
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    public BackfillJobService(LottoCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    public BackfillJobStatusResponse start(int from, int to) {
        String jobId = UUID.randomUUID().toString();
        JobState state = new JobState(jobId, from, to);
        jobs.put(jobId, state);

        CompletableFuture.runAsync(() -> runJob(state));
        return state.toResponse();
    }

    public BackfillJobStatusResponse get(String jobId) {
        JobState state = jobs.get(jobId);
        return state == null ? null : state.toResponse();
    }

    private void runJob(JobState state) {
        state.status = JobStatus.RUNNING;
        try {
            state.result = collectionService.backfill(state.from, state.to);
            state.status = JobStatus.SUCCEEDED;
        } catch (Exception ex) {
            state.status = JobStatus.FAILED;
            state.error = ex.getMessage();
        }
    }

    enum JobStatus {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    static final class JobState {
        final String jobId;
        final int from;
        final int to;
        volatile JobStatus status = JobStatus.QUEUED;
        volatile CollectResponse result;
        volatile String error;

        JobState(String jobId, int from, int to) {
            this.jobId = jobId;
            this.from = from;
            this.to = to;
        }

        BackfillJobStatusResponse toResponse() {
            return new BackfillJobStatusResponse(jobId, status.name(), from, to, result, error);
        }
    }
}
