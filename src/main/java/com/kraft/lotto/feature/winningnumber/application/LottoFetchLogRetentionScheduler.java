package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "kraft.collect.log-retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LottoFetchLogRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(LottoFetchLogRetentionScheduler.class);

    private final LottoFetchLogRepository fetchLogRepository;
    private final Clock clock;
    private final int retentionDays;
    private final int deleteBatchSize;

    @Autowired
    public LottoFetchLogRetentionScheduler(LottoFetchLogRepository fetchLogRepository,
                                           @Value("${kraft.collect.log-retention.days:90}") int retentionDays,
                                           @Value("${kraft.collect.log-retention.delete-batch-size:1000}") int deleteBatchSize) {
        this(fetchLogRepository, Clock.systemDefaultZone(), retentionDays, deleteBatchSize);
    }

    LottoFetchLogRetentionScheduler(LottoFetchLogRepository fetchLogRepository,
                                    Clock clock,
                                    int retentionDays,
                                    int deleteBatchSize) {
        this.fetchLogRepository = fetchLogRepository;
        this.clock = clock;
        this.retentionDays = Math.max(1, retentionDays);
        this.deleteBatchSize = Math.max(100, deleteBatchSize);
    }

    @Scheduled(
            cron = "${kraft.collect.log-retention.cron:0 30 3 * * *}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    @SchedulerLock(name = "lotto_fetch_log_retention_purge")
    @Transactional
    public void purgeExpiredLogs() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        long deleted = 0L;
        while (true) {
            List<Long> ids = fetchLogRepository.findIdsByFetchedAtBefore(cutoff, PageRequest.of(0, deleteBatchSize));
            if (ids.isEmpty()) {
                break;
            }
            fetchLogRepository.deleteAllByIdInBatch(ids);
            deleted += ids.size();
        }
        if (deleted > 0) {
            log.info("lotto_fetch_logs retention purge done: deleted={}, cutoff={}", deleted, cutoff);
        }
    }
}
