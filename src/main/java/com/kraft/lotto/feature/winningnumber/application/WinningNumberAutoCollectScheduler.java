package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.web.dto.CollectResponse;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "kraft.lotto.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "kraft.collect.auto", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WinningNumberAutoCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(WinningNumberAutoCollectScheduler.class);

    private final LottoCollectionService collectionService;
    private final MeterRegistry meterRegistry;

    public WinningNumberAutoCollectScheduler(LottoCollectionService collectionService,
                                             MeterRegistry meterRegistry) {
        this.collectionService = collectionService;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(
            cron = "${kraft.collect.auto.cron.saturday-21-10:0 10 21 ? * SAT}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    @SchedulerLock(name = "lotto_collect_next_sat_21_10",
            lockAtMostFor = "${kraft.lotto.scheduler.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${kraft.lotto.scheduler.lock-at-least-for:PT10S}")
    public void collectNextDrawOnSaturday2110() {
        runCollectNext("sat-21-10");
    }

    @Scheduled(
            cron = "${kraft.collect.auto.cron.saturday-21-retry:0 20,40 21 ? * SAT}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    @SchedulerLock(name = "lotto_collect_next_sat_21_retry",
            lockAtMostFor = "${kraft.lotto.scheduler.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${kraft.lotto.scheduler.lock-at-least-for:PT10S}")
    public void retryCollectNextDrawOnSaturday2120And2140() {
        runCollectNext("sat-21-retry");
    }

    @Scheduled(
            cron = "${kraft.collect.auto.cron.saturday-22-10:0 10 22 ? * SAT}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    @SchedulerLock(name = "lotto_collect_next_sat_22_10",
            lockAtMostFor = "${kraft.lotto.scheduler.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${kraft.lotto.scheduler.lock-at-least-for:PT10S}")
    public void retryCollectNextDrawOnSaturday2210() {
        runCollectNext("sat-22-10");
    }

    @Scheduled(
            cron = "${kraft.collect.auto.cron.sunday-06-10:0 10 6 ? * SUN}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    @SchedulerLock(name = "lotto_collect_missing_sun_06_10",
            lockAtMostFor = "${kraft.lotto.scheduler.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${kraft.lotto.scheduler.lock-at-least-for:PT10S}")
    public void collectMissingDrawsOnSunday0610() {
        runCollectMissing("sun-06-10");
    }

    @Scheduled(
            cron = "${kraft.collect.auto.cron.daily-09-00:0 0 9 * * *}",
            zone = "${kraft.collect.auto.zone:Asia/Seoul}"
    )
    @SchedulerLock(name = "lotto_collect_missing_daily_09_00",
            lockAtMostFor = "${kraft.lotto.scheduler.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${kraft.lotto.scheduler.lock-at-least-for:PT10S}")
    public void collectMissingDrawsDaily0900() {
        runCollectMissing("daily-09-00");
    }

    private void runCollectNext(String trigger) {
        try {
            log.info("lotto collect-next start trigger={}", trigger);
            CollectResponse response = collectionService.collectNextDraw();
            log.info("lotto collect-next done trigger={} collected={} skipped={} failed={} latestRound={} notDrawn={}",
                    trigger, response.collected(), response.skipped(), response.failed(), response.latestRound(), response.notDrawn());
        } catch (Exception ex) {
            meterRegistry.counter("kraft.scheduler.collect.failure", "trigger", trigger).increment();
            log.warn("lotto collect-next fail trigger={}", trigger, ex);
        }
    }

    private void runCollectMissing(String trigger) {
        try {
            log.info("lotto collect-missing start trigger={}", trigger);
            CollectResponse response = collectionService.collectMissingDraws();
            log.info("lotto collect-missing done trigger={} collected={} skipped={} failed={} latestRound={} failedRounds={}",
                    trigger, response.collected(), response.skipped(), response.failed(), response.latestRound(), response.failedRounds());
        } catch (Exception ex) {
            meterRegistry.counter("kraft.scheduler.collect.failure", "trigger", trigger).increment();
            log.warn("lotto collect-missing fail trigger={}", trigger, ex);
        }
    }
}
