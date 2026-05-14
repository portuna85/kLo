package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class WinningNumberAutoCollectSchedulerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class,
                    TaskSchedulingAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class, SchedulerImportConfig.class);

    @Test
    void createsSchedulerBeanWhenBothFlagsAreEnabled() {
        contextRunner
                .withPropertyValues(
                        "kraft.lotto.scheduler.enabled=true",
                        "kraft.collect.auto.enabled=true"
                )
                .run(context -> assertThat(context).hasSingleBean(WinningNumberAutoCollectScheduler.class));
    }

    @Test
    void doesNotCreateSchedulerBeanWhenInfraSchedulerDisabled() {
        contextRunner
                .withPropertyValues(
                        "kraft.lotto.scheduler.enabled=false",
                        "kraft.collect.auto.enabled=true"
                )
                .run(context -> assertThat(context).doesNotHaveBean(WinningNumberAutoCollectScheduler.class));
    }

    @Test
    void doesNotCreateSchedulerBeanWhenAutoCollectDisabled() {
        contextRunner
                .withPropertyValues(
                        "kraft.lotto.scheduler.enabled=true",
                        "kraft.collect.auto.enabled=false"
                )
                .run(context -> assertThat(context).doesNotHaveBean(WinningNumberAutoCollectScheduler.class));
    }

    @Configuration
    static class TestConfig {

        @Bean
        LottoCollectionService lottoCollectionService() {
            return Mockito.mock(LottoCollectionService.class);
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration
    @Import(WinningNumberAutoCollectScheduler.class)
    static class SchedulerImportConfig {
    }
}
