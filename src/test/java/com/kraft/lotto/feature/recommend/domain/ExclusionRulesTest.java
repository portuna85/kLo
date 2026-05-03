package com.kraft.lotto.feature.recommend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExclusionRule 구현체")
class ExclusionRulesTest {

    @Nested
    @DisplayName("BirthdayBiasRule")
    class BirthdayBias {
        private final BirthdayBiasRule rule = new BirthdayBiasRule();

        @Test
        @DisplayName("모든 번호가 31 이하면 제외된다")
        void excludesWhenAllNumbersAreAtMost31() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 31))).isTrue();
        }

        @Test
        @DisplayName("한 개라도 32 이상이면 제외되지 않는다")
        void doesNotExcludeWhenAnyNumberIsAtLeast32() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 32))).isFalse();
        }

        @Test
        @DisplayName("경계값 32를 포함하면 제외되지 않는다")
        void doesNotExcludeWhenContainsBoundary32() {
            assertThat(rule.shouldExclude(LottoCombination.of(2, 5, 10, 20, 30, 32))).isFalse();
        }
    }

    @Nested
    @DisplayName("ArithmeticSequenceRule")
    class Arithmetic {
        private final ArithmeticSequenceRule rule = new ArithmeticSequenceRule();

        @Test
        @DisplayName("공차 7 등차수열이면 제외된다")
        void excludesArithmeticSequenceWithDiff7() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 36))).isTrue();
        }

        @Test
        @DisplayName("공차 3 등차수열이면 제외된다")
        void excludesArithmeticSequenceWithDiff3() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 6, 9, 12, 15, 18))).isTrue();
        }

        @Test
        @DisplayName("일반 조합은 제외되지 않는다")
        void doesNotExcludeNormalCombination() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
        @DisplayName("부분만 등차이면 제외되지 않는다")
        void doesNotExcludePartialArithmetic() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 37))).isFalse();
        }
    }

    @Nested
    @DisplayName("LongRunRule")
    class LongRun {
        private final LongRunRule rule = new LongRunRule();

        @Test
        @DisplayName("연속 5개가 시작 위치에 있으면 제외된다")
        void excludesWhenFiveConsecutiveAtStart() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 5, 20))).isTrue();
        }

        @Test
        @DisplayName("연속 5개가 중간에 있으면 제외된다")
        void excludesWhenFiveConsecutiveInMiddle() {
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 12, 13, 14, 40))).isTrue();
        }

        @Test
        @DisplayName("연속 4개는 제외되지 않는다")
        void doesNotExcludeFourConsecutive() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 10, 20))).isFalse();
        }

        @Test
        @DisplayName("일반 조합은 제외되지 않는다")
        void doesNotExcludeNormalCombination() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 9, 15, 21, 33, 45))).isFalse();
        }
    }

    @Nested
    @DisplayName("SingleDecadeRule")
    class SingleDecade {
        private final SingleDecadeRule rule = new SingleDecadeRule();

        @Test
        @DisplayName("한 십의자리 버킷에 5개가 몰리면 제외된다")
        void excludesWhenFiveInOneDecadeBucket() {
            // 10~19 버킷에 5개
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 13, 17, 19, 40))).isTrue();
        }

        @Test
        @DisplayName("한 십의자리에 5개이면 제외된다")
        void excludesWhenFiveInOnesDecade() {
            // 1~9 버킷에 5개 (1,3,5,7,9) → 제외
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 9, 40))).isTrue();
        }

        @Test
        @DisplayName("한 십의자리에 4개이면 제외되지 않는다")
        void doesNotExcludeWhenFourInOneDecade() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 20, 40))).isFalse();
        }

        @Test
        @DisplayName("십의자리가 분산되어 있으면 제외되지 않는다")
        void doesNotExcludeWhenDecadesAreSpread() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 11, 22, 33, 41, 45))).isFalse();
        }

        @Test
        @DisplayName("40대 버킷에 5개이면 제외된다")
        void excludesWhenFiveInForties() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 40, 41, 42, 43, 44))).isTrue();
        }
    }

    @Nested
    @DisplayName("PastWinningRule")
    class PastWinning {

        private static final LottoCombination KNOWN = LottoCombination.of(1, 7, 13, 22, 34, 45);

        private PastWinningRule ruleWith(LottoCombination... pastWinnings) {
            PastWinningCache cache = new PastWinningCache();
            cache.replace(java.util.List.of(pastWinnings));
            return new PastWinningRule(cache);
        }

        @Test
        @DisplayName("캐시에 있는 조합은 제외된다")
        void excludesCombinationInCache() {
            assertThat(ruleWith(KNOWN).shouldExclude(KNOWN)).isTrue();
        }

        @Test
        @DisplayName("캐시에 없는 조합은 제외되지 않는다")
        void doesNotExcludeCombinationNotInCache() {
            assertThat(ruleWith(KNOWN).shouldExclude(LottoCombination.of(2, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
        @DisplayName("빈 캐시면 항상 제외되지 않는다")
        void doesNotExcludeWhenCacheIsEmpty() {
            PastWinningRule rule = new PastWinningRule(new PastWinningCache());
            assertThat(rule.shouldExclude(KNOWN)).isFalse();
        }
    }
}

