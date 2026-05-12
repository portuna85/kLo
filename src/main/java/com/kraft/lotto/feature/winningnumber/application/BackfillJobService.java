package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.web.dto.BackfillJobStatusResponse;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BackfillJobService {

    private final LottoCollectionService collectionService;
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final Duration jobRetention;
    private final Duration cleanupInterval;
    private final Clock clock;
    private final AtomicReference<Instant> nextCleanupAt;

    @Autowired
    public BackfillJobService(
            LottoCollectionService collectionService,
            @Value("${kraft.collect.backfill.max-concurrent-jobs:2}") int maxConcurrentJobs,
            @Value("${kraft.collect.backfill.queue-capacity:10}") int queueCapacity,
            @Value("${kraft.collect.backfill.job-retention:PT6H}") Duration jobRetention,
            @Value("${kraft.collect.backfill.cleanup-interval:PT10M}") Duration cleanupInterval,
            @Value("${kraft.collect.backfill.thread-name-prefix:backfill-job-}") String threadNamePrefix) {
        this(
                collectionService,
                maxConcurrentJobs,
                queueCapacity,
                jobRetention,
                cleanupInterval,
                threadNamePrefix,
                Clock.systemUTC());
    }

    BackfillJobService(
            LottoCollectionService collectionService,
            int maxConcurrentJobs,
            int queueCapacity,
            Duration jobRetention,
            Duration cleanupInterval,
            String threadNamePrefix,
            Clock clock) {
        if (maxConcurrentJobs < 1) {
            throw new IllegalArgumentException("maxConcurrentJobs must be at least 1");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("queueCapacity must be zero or positive");
        }
        if (jobRetention.isNegative() || jobRetention.isZero()) {
            throw new IllegalArgumentException("jobRetention must be positive");
        }
        if (cleanupInterval.isNegative()) {
            throw new IllegalArgumentException("cleanupInterval must be zero or positive");
        }
        this.collectionService = collectionService;
        this.jobRetention = jobRetention;
        this.cleanupInterval = cleanupInterval;
        this.clock = clock;
        this.nextCleanupAt = new AtomicReference<>(clock.instant());
        this.executor = new ThreadPoolExecutor(
                maxConcurrentJobs,
                maxConcurrentJobs,
                0L,
                TimeUnit.MILLISECONDS,
                workQueue(queueCapacity),
                namedThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public BackfillJobStatusResponse start(int from, int to) {
        cleanupOldJobsIfDue();

        String jobId = UUID.randomUUID().toString();
        JobState state = new JobState(jobId, from, to, clock.instant());
        jobs.put(jobId, state);

        try {
            executor.execute(() -> runJob(state));
        } catch (RejectedExecutionException ex) {
            state.markFailed("backfill job queue is full", clock.instant());
        }
        return state.toResponse();
    }

    public BackfillJobStatusResponse get(String jobId) {
        cleanupOldJobsIfDue();

        JobState state = jobs.get(jobId);
        return state == null ? null : state.toResponse();
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }

    private void runJob(JobState state) {
        state.markRunning(clock.instant());
        try {
            state.result = collectionService.backfill(state.from, state.to);
            state.markSucceeded(clock.instant());
        } catch (Exception ex) {
            state.markFailed(ex.getMessage(), clock.instant());
        }
    }

    private void cleanupOldJobsIfDue() {
        Instant now = clock.instant();
        Instant dueAt = nextCleanupAt.get();
        if (now.isBefore(dueAt) || !nextCleanupAt.compareAndSet(dueAt, now.plus(cleanupInterval))) {
            return;
        }

        Instant expiresBefore = now.minus(jobRetention);
        jobs.entrySet().removeIf(entry -> entry.getValue().isExpiredTerminalState(expiresBefore));
    }

    private static BlockingQueue<Runnable> workQueue(int queueCapacity) {
        if (queueCapacity == 0) {
            return new SynchronousQueue<>();
        }
        return new ArrayBlockingQueue<>(queueCapacity);
    }

    private static ThreadFactory namedThreadFactory(String threadNamePrefix) {
        String prefix = (threadNamePrefix == null || threadNamePrefix.isBlank()) ? "backfill-job-" : threadNamePrefix;
        AtomicInteger threadNumber = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        };
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
        final Instant createdAt;
        volatile JobStatus status = JobStatus.QUEUED;
        volatile Instant startedAt;
        volatile Instant completedAt;
        volatile CollectResponse result;
        volatile String error;

        JobState(String jobId, int from, int to, Instant createdAt) {
            this.jobId = jobId;
            this.from = from;
            this.to = to;
            this.createdAt = createdAt;
        }

        void markRunning(Instant now) {
            startedAt = now;
            status = JobStatus.RUNNING;
        }

        void markSucceeded(Instant now) {
            completedAt = now;
            status = JobStatus.SUCCEEDED;
        }

        void markFailed(String message, Instant now) {
            error = message;
            completedAt = now;
            status = JobStatus.FAILED;
        }

        boolean isExpiredTerminalState(Instant expiresBefore) {
            return completedAt != null
                    && (status == JobStatus.SUCCEEDED || status == JobStatus.FAILED)
                    && !completedAt.isAfter(expiresBefore);
        }

        BackfillJobStatusResponse toResponse() {
            return new BackfillJobStatusResponse(jobId, status.name(), from, to, result, error);
        }
    }
}
