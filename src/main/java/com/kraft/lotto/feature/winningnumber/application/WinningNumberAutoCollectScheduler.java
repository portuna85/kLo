package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kraft.collect.auto", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WinningNumberAutoCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(WinningNumberAutoCollectScheduler.class);

    private final WinningNumberCollectService collectService;

    public WinningNumberAutoCollectScheduler(WinningNumberCollectService collectService) {
        this.collectService = collectService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStartup() {
        runCollect("startup");
    }

    @Scheduled(cron = "${kraft.collect.auto.cron.saturday-22:0 0 22 * * SAT}", zone = "${kraft.collect.auto.zone:Asia/Seoul}")
    public void collectOnSaturday2200() {
        runCollect("sat-22");
    }

    @Scheduled(cron = "${kraft.collect.auto.cron.sunday-21:0 0 21 * * SUN}", zone = "${kraft.collect.auto.zone:Asia/Seoul}")
    public void collectOnSunday2100() {
        runCollect("sun-21");
    }

    private void runCollect(String trigger) {
        try {
            log.info("auto-collect start trigger={}", trigger);
            CollectResponse response = collectService.collect(null);
            log.info("auto-collect done trigger={} collected={} skipped={} failed={} latestRound={} truncated={} nextRound={} notDrawn={}",
                    trigger,
                    response.collected(),
                    response.skipped(),
                    response.failed(),
                    response.latestRound(),
                    response.truncated(),
                    response.nextRound(),
                    response.notDrawn());
        } catch (Exception ex) {
            log.warn("auto-collect fail trigger={}", trigger, ex);
        }
    }
}
