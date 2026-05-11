package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.infrastructure.LottoFetchLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    @Autowired
    public LottoFetchLogRetentionScheduler(LottoFetchLogRepository fetchLogRepository,
                                           @Value("${kraft.collect.log-retention.days:90}") int retentionDays) {
        this(fetchLogRepository, Clock.systemDefaultZone(), retentionDays);
    }

    LottoFetchLogRetentionScheduler(LottoFetchLogRepository fetchLogRepository,
                                    Clock clock,
                                    int retentionDays) {
        this.fetchLogRepository = fetchLogRepository;
        this.clock = clock;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(
            cron = "${kraft.collect.log-retention.cron:0 30 3 * * *}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    @Transactional
    public void purgeExpiredLogs() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        long deleted = fetchLogRepository.deleteByFetchedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("lotto_fetch_logs retention purge done: deleted={}, cutoff={}", deleted, cutoff);
        }
    }
}
