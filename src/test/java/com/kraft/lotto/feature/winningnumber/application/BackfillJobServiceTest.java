package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.lotto.feature.winningnumber.web.dto.BackfillJobStatusResponse;
import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("백필 작업 서비스 테스트")
class BackfillJobServiceTest {

    private BackfillJobService service;

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
                1,
                1,
                Duration.ofHours(1),
                Duration.ofMinutes(10),
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
        when(collectionService.backfill(anyInt(), anyInt()))
                .thenReturn(CollectResponse.of(1, 0, 0, 1, List.of(), false, null, false));
        MutableClock clock = new MutableClock(Instant.parse("2026-05-12T00:00:00Z"));
        service = new BackfillJobService(
                collectionService,
                1,
                1,
                Duration.ofSeconds(1),
                Duration.ZERO,
                "test-backfill-",
                clock);

        BackfillJobStatusResponse started = service.start(1, 1);
        assertStatus(started.jobId(), "SUCCEEDED");

        clock.advance(Duration.ofSeconds(2));

        assertThat(service.get(started.jobId())).isNull();
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
