package com.kraft.lotto.feature.winningnumber.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.BackfillJobEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.BackfillJobRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.BackfillJobStatusResponse;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
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

    private static final List<String> TERMINAL_STATUSES = List.of("SUCCEEDED", "FAILED");

    private final LottoCollectionService collectionService;
    private final BackfillJobRepository backfillJobRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final Duration jobRetention;
    private final Duration cleanupInterval;
    private final int maxRetainedJobs;
    private final Clock clock;
    private final AtomicReference<Instant> nextCleanupAt;

    @Autowired
    public BackfillJobService(
            LottoCollectionService collectionService,
            BackfillJobRepository backfillJobRepository,
            ObjectMapper objectMapper,
            @Value("${kraft.collect.backfill.max-concurrent-jobs:2}") int maxConcurrentJobs,
            @Value("${kraft.collect.backfill.queue-capacity:10}") int queueCapacity,
            @Value("${kraft.collect.backfill.job-retention:PT6H}") Duration jobRetention,
            @Value("${kraft.collect.backfill.cleanup-interval:PT10M}") Duration cleanupInterval,
            @Value("${kraft.collect.backfill.max-retained-jobs:1000}") int maxRetainedJobs,
            @Value("${kraft.collect.backfill.thread-name-prefix:backfill-job-}") String threadNamePrefix) {
        this(
                collectionService,
                backfillJobRepository,
                objectMapper,
                maxConcurrentJobs,
                queueCapacity,
                jobRetention,
                cleanupInterval,
                maxRetainedJobs,
                threadNamePrefix,
                Clock.systemUTC());
    }

    BackfillJobService(
            LottoCollectionService collectionService,
            BackfillJobRepository backfillJobRepository,
            ObjectMapper objectMapper,
            int maxConcurrentJobs,
            int queueCapacity,
            Duration jobRetention,
            Duration cleanupInterval,
            int maxRetainedJobs,
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
        if (maxRetainedJobs < 10) {
            throw new IllegalArgumentException("maxRetainedJobs must be at least 10");
        }
        this.collectionService = collectionService;
        this.backfillJobRepository = backfillJobRepository;
        this.objectMapper = objectMapper;
        this.jobRetention = jobRetention;
        this.cleanupInterval = cleanupInterval;
        this.maxRetainedJobs = maxRetainedJobs;
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
        backfillJobRepository.save(new BackfillJobEntity(
                jobId,
                JobStatus.QUEUED.name(),
                from,
                to,
                toLocalDateTime(state.createdAt)
        ));

        try {
            executor.execute(() -> runJob(state));
        } catch (RejectedExecutionException ex) {
            state.markFailed("backfill job queue is full", clock.instant());
            persistFailed(state, state.error);
        }
        return state.toResponse();
    }

    public BackfillJobStatusResponse get(String jobId) {
        cleanupOldJobsIfDue();

        JobState state = jobs.get(jobId);
        if (state != null) {
            return state.toResponse();
        }
        Instant expiresBefore = clock.instant().minus(jobRetention);
        return backfillJobRepository.findById(jobId)
                .map(entity -> {
                    if (isExpiredTerminal(entity, expiresBefore)) {
                        backfillJobRepository.deleteById(jobId);
                        return null;
                    }
                    return toResponse(entity);
                })
                .orElse(null);
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }

    private void runJob(JobState state) {
        state.markRunning(clock.instant());
        persistRunning(state);
        try {
            state.result = collectionService.backfill(state.from, state.to);
            state.markSucceeded(clock.instant());
            persistSucceeded(state);
        } catch (Exception ex) {
            state.markFailed(ex.getMessage(), clock.instant());
            persistFailed(state, ex.getMessage());
        } finally {
            trimTerminalJobsByCount();
        }
    }

    private void cleanupOldJobsIfDue() {
        Instant now = clock.instant();
        if (cleanupInterval.isZero()) {
            cleanupOldJobs(now);
            return;
        }
        Instant dueAt = nextCleanupAt.get();
        if (now.isBefore(dueAt) || !nextCleanupAt.compareAndSet(dueAt, now.plus(cleanupInterval))) {
            return;
        }
        cleanupOldJobs(now);
    }

    private void cleanupOldJobs(Instant now) {
        Instant expiresBefore = now.minus(jobRetention);
        jobs.entrySet().removeIf(entry -> entry.getValue().isExpiredTerminalState(expiresBefore));
        backfillJobRepository.deleteByCompletedAtBeforeAndStatusIn(
                toLocalDateTime(expiresBefore),
                TERMINAL_STATUSES
        );
        trimTerminalJobsByCount();
    }

    private void trimTerminalJobsByCount() {
        List<JobState> terminalJobs = jobs.values().stream()
                .filter(JobState::isTerminal)
                .sorted(Comparator.comparing(JobState::completedAtOrCreatedAt))
                .toList();
        int overflow = terminalJobs.size() - maxRetainedJobs;
        if (overflow <= 0) {
            return;
        }
        for (int i = 0; i < overflow; i++) {
            jobs.remove(terminalJobs.get(i).jobId);
        }
        List<String> overflowIds = backfillJobRepository.findTerminalJobIds(
                TERMINAL_STATUSES,
                org.springframework.data.domain.PageRequest.of(0, overflow)
        );
        if (!overflowIds.isEmpty()) {
            backfillJobRepository.deleteAllById(overflowIds);
        }
    }

    private BackfillJobStatusResponse toResponse(BackfillJobEntity entity) {
        return new BackfillJobStatusResponse(
                entity.getJobId(),
                entity.getStatus(),
                entity.getFromRound(),
                entity.getToRound(),
                deserializeResult(entity.getResultJson()),
                entity.getErrorMessage()
        );
    }

    private static boolean isExpiredTerminal(BackfillJobEntity entity, Instant expiresBefore) {
        if (entity.getCompletedAt() == null || !TERMINAL_STATUSES.contains(entity.getStatus())) {
            return false;
        }
        Instant completedAt = entity.getCompletedAt().toInstant(ZoneOffset.UTC);
        return !completedAt.isAfter(expiresBefore);
    }

    private CollectResponse deserializeResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(resultJson, CollectResponse.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String serializeResult(CollectResponse result) {
        if (result == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void persistRunning(JobState state) {
        backfillJobRepository.findById(state.jobId).ifPresent(entity -> {
            entity.markRunning(toLocalDateTime(state.startedAt));
            backfillJobRepository.save(entity);
        });
    }

    private void persistSucceeded(JobState state) {
        backfillJobRepository.findById(state.jobId).ifPresent(entity -> {
            entity.markSucceeded(serializeResult(state.result), toLocalDateTime(state.completedAt));
            backfillJobRepository.save(entity);
        });
    }

    private void persistFailed(JobState state, String error) {
        backfillJobRepository.findById(state.jobId).ifPresent(entity -> {
            entity.markFailed(error, toLocalDateTime(state.completedAt == null ? clock.instant() : state.completedAt));
            backfillJobRepository.save(entity);
        });
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
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
                    && isTerminal()
                    && !completedAt.isAfter(expiresBefore);
        }

        boolean isTerminal() {
            return status == JobStatus.SUCCEEDED || status == JobStatus.FAILED;
        }

        Instant completedAtOrCreatedAt() {
            return completedAt == null ? createdAt : completedAt;
        }

        BackfillJobStatusResponse toResponse() {
            return new BackfillJobStatusResponse(jobId, status.name(), from, to, result, error);
        }
    }
}
