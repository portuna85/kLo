package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.lotto.feature.winningnumber.infrastructure.BackfillJobEntity;
import com.kraft.lotto.feature.winningnumber.infrastructure.BackfillJobRepository;
import com.kraft.lotto.feature.winningnumber.web.dto.BackfillJobStatusResponse;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("백필 작업 서비스 테스트")
class BackfillJobServiceTest {

    private BackfillJobService service;
    private final Map<String, BackfillJobEntity> jobStore = new HashMap<>();

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdownExecutor();
        }
    }

    @Test
    @DisplayName("유한 실행자를 사용하여 동시 작업 수를 제한한다")
    void startLimitsConcurrentJobsWithBoundedExecutor() throws Exception {
        LottoCollectionService collectionService = mock(LottoCollectionService.class);
        BackfillJobRepository backfillJobRepository = mock(BackfillJobRepository.class);
        stubBackfillRepository(backfillJobRepository);
        CountDownLatch firstJobStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstJob = new CountDownLatch(1);
        when(collectionService.backfill(anyInt(), anyInt())).thenAnswer(invocation -> {
            int from = invocation.getArgument(0, Integer.class);
            if (from == 1) {
                firstJobStarted.countDown();
                assertThat(releaseFirstJob.await(1, TimeUnit.SECONDS)).isTrue();
            }
            return CollectResponse.of(1, 0, 0, invocation.getArgument(1, Integer.class), List.of(), false, null, false);
        });
        service = new BackfillJobService(
                collectionService,
                backfillJobRepository,
                new ObjectMapper(),
                1,
                1,
                Duration.ofHours(1),
                Duration.ofMinutes(10),
                1000,
                "test-backfill-",
                Clock.systemUTC());

        BackfillJobStatusResponse first = service.start(1, 1);
        assertThat(firstJobStarted.await(1, TimeUnit.SECONDS)).isTrue();

        BackfillJobStatusResponse second = service.start(2, 2);
        BackfillJobStatusResponse rejected = service.start(3, 3);

        assertThat(service.get(first.jobId()).status()).isEqualTo("RUNNING");
        assertThat(service.get(second.jobId()).status()).isEqualTo("QUEUED");
        assertThat(rejected.status()).isEqualTo("FAILED");
        assertThat(rejected.error()).isEqualTo("backfill job queue is full");

        releaseFirstJob.countDown();

        assertStatus(first.jobId(), "SUCCEEDED");
        assertStatus(second.jobId(), "SUCCEEDED");
        verify(collectionService).backfill(1, 1);
        verify(collectionService).backfill(2, 2);
        verify(collectionService, Mockito.never()).backfill(3, 3);
    }

    @Test
    @DisplayName("만료된 완료 작업을 정리한다")
    void getCleansUpExpiredCompletedJobs() {
        LottoCollectionService collectionService = mock(LottoCollectionService.class);
        BackfillJobRepository repository = mock(BackfillJobRepository.class);
        stubBackfillRepository(repository);
        when(collectionService.backfill(anyInt(), anyInt()))
                .thenReturn(CollectResponse.of(1, 0, 0, 1, List.of(), false, null, false));
        MutableClock clock = new MutableClock(Instant.parse("2026-05-12T00:00:00Z"));
        service = new BackfillJobService(
                collectionService,
                repository,
                new ObjectMapper(),
                1,
                1,
                Duration.ofSeconds(1),
                Duration.ZERO,
                1000,
                "test-backfill-",
                clock);

        BackfillJobStatusResponse started = service.start(1, 1);
        assertStatus(started.jobId(), "SUCCEEDED");

        clock.advance(Duration.ofSeconds(2));

        service.get(started.jobId());
        verify(repository, Mockito.atLeastOnce()).deleteByCompletedAtBeforeAndStatusIn(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                anyList()
        );
    }

    @Test
    @DisplayName("완료 작업 보관 상한을 넘기면 오래된 작업부터 정리한다")
    void trimsOldTerminalJobsByCountLimit() {
        LottoCollectionService collectionService = mock(LottoCollectionService.class);
        BackfillJobRepository backfillJobRepository = mock(BackfillJobRepository.class);
        stubBackfillRepository(backfillJobRepository);
        when(collectionService.backfill(anyInt(), anyInt()))
                .thenReturn(CollectResponse.of(1, 0, 0, 1, List.of(), false, null, false));
        service = new BackfillJobService(
                collectionService,
                backfillJobRepository,
                new ObjectMapper(),
                1,
                1,
                Duration.ofHours(1),
                Duration.ZERO,
                10,
                "test-backfill-",
                Clock.systemUTC());

        BackfillJobStatusResponse first = service.start(1, 1);
        assertStatus(first.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse second = service.start(2, 2);
        assertStatus(second.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse third = service.start(3, 3);
        assertStatus(third.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse fourth = service.start(4, 4);
        assertStatus(fourth.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse fifth = service.start(5, 5);
        assertStatus(fifth.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse sixth = service.start(6, 6);
        assertStatus(sixth.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse seventh = service.start(7, 7);
        assertStatus(seventh.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse eighth = service.start(8, 8);
        assertStatus(eighth.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse ninth = service.start(9, 9);
        assertStatus(ninth.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse tenth = service.start(10, 10);
        assertStatus(tenth.jobId(), "SUCCEEDED");
        BackfillJobStatusResponse eleventh = service.start(11, 11);
        assertStatus(eleventh.jobId(), "SUCCEEDED");

        assertThat(service.get(first.jobId())).isNull();
        assertThat(service.get(eleventh.jobId())).isNotNull();
    }

    private void assertStatus(String jobId, String expectedStatus) {
        Instant deadline = Instant.now().plusSeconds(2);
        BackfillJobStatusResponse response;
        do {
            response = service.get(jobId);
            if (response != null && expectedStatus.equals(response.status())) {
                return;
            }
            Thread.onSpinWait();
        } while (Instant.now().isBefore(deadline));

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(expectedStatus);
    }

    private void stubBackfillRepository(BackfillJobRepository repository) {
        when(repository.save(org.mockito.ArgumentMatchers.any(BackfillJobEntity.class))).thenAnswer(invocation -> {
            BackfillJobEntity entity = invocation.getArgument(0, BackfillJobEntity.class);
            jobStore.put(entity.getJobId(), entity);
            return entity;
        });
        when(repository.findById(anyString())).thenAnswer(invocation -> {
            String jobId = invocation.getArgument(0, String.class);
            return Optional.ofNullable(jobStore.get(jobId));
        });
        when(repository.deleteByCompletedAtBeforeAndStatusIn(org.mockito.ArgumentMatchers.any(LocalDateTime.class), anyList()))
                .thenAnswer(invocation -> {
                    LocalDateTime cutoff = invocation.getArgument(0, LocalDateTime.class);
                    @SuppressWarnings("unchecked")
                    List<String> statuses = invocation.getArgument(1, List.class);
                    List<String> toDelete = new ArrayList<>();
                    for (BackfillJobEntity entity : jobStore.values()) {
                        if (entity.getCompletedAt() != null
                                && !entity.getCompletedAt().isAfter(cutoff)
                                && statuses.contains(entity.getStatus())) {
                            toDelete.add(entity.getJobId());
                        }
                    }
                    toDelete.forEach(jobStore::remove);
                    return (long) toDelete.size();
                });
        when(repository.findTerminalJobIds(anyList(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<String> statuses = invocation.getArgument(0, List.class);
                    org.springframework.data.domain.Pageable pageable = invocation.getArgument(1, org.springframework.data.domain.Pageable.class);
                    return jobStore.values().stream()
                            .filter(e -> statuses.contains(e.getStatus()))
                            .sorted(Comparator.comparing(BackfillJobEntity::getCompletedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                    .thenComparing(BackfillJobEntity::getCreatedAt))
                            .map(BackfillJobEntity::getJobId)
                            .limit(pageable.getPageSize())
                            .toList();
                });
        Mockito.doAnswer(invocation -> {
            Iterable<String> ids = invocation.getArgument(0, Iterable.class);
            for (String id : ids) {
                jobStore.remove(id);
            }
            return null;
        }).when(repository).deleteAllById(org.mockito.ArgumentMatchers.anyIterable());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
