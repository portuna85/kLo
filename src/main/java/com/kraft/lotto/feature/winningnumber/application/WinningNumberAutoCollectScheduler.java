package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${kraft.lotto.scheduler.enabled:true} and ${kraft.collect.auto.enabled:true}")
public class WinningNumberAutoCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(WinningNumberAutoCollectScheduler.class);

    private final LottoCollectionService collectionService;

    public WinningNumberAutoCollectScheduler(LottoCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @Scheduled(
            cron = "${kraft.collect.auto.cron.saturday-21-10:0 10 21 ? * SAT}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    public void collectSaturday2110() {
        runCollectNext("sat-21-10");
    }

    @Scheduled(
            cron = "${kraft.collect.auto.cron.saturday-22-10:0 10 22 ? * SAT}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    public void collectSaturday2210() {
        runCollectNext("sat-22-10");
    }

    @Scheduled(
            cron = "${kraft.collect.auto.cron.sunday-06-10:0 10 6 ? * SUN}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    public void collectSunday0610() {
        runCollectNext("sun-06-10");
    }

    private void runCollectNext(String trigger) {
        try {
            log.info("lotto collect-next start trigger={}", trigger);
            CollectResponse response = collectionService.collectNextIfNeeded();
            log.info("lotto collect-next done trigger={} collected={} skipped={} failed={} latestRound={} notDrawn={}",
                    trigger, response.collected(), response.skipped(), response.failed(), response.latestRound(), response.notDrawn());
        } catch (Exception ex) {
            log.warn("lotto collect-next fail trigger={}", trigger, ex);
        }
    }

}
