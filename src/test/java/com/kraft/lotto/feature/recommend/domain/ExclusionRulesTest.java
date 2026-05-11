package com.kraft.lotto.feature.recommend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

    @DisplayName("테스트")
class ExclusionRulesTest {

    @Nested
    @DisplayName("테스트")
    class BirthdayBias {
        private final BirthdayBiasRule rule = new BirthdayBiasRule();

        @Test
    @DisplayName("테스트")
        void excludesWhenAllNumbersAreAtMost31() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 31))).isTrue();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeWhenAnyNumberIsAtLeast32() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 32))).isFalse();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeWhenContainsBoundary32() {
            assertThat(rule.shouldExclude(LottoCombination.of(2, 5, 10, 20, 30, 32))).isFalse();
        }
    }

    @Nested
    @DisplayName("테스트")
    class Arithmetic {
        private final ArithmeticSequenceRule rule = new ArithmeticSequenceRule();

        @Test
    @DisplayName("테스트")
        void excludesArithmeticSequenceWithDiff7() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 36))).isTrue();
        }

        @Test
    @DisplayName("테스트")
        void excludesArithmeticSequenceWithDiff3() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 6, 9, 12, 15, 18))).isTrue();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeNormalCombination() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludePartialArithmetic() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 37))).isFalse();
        }
    }

    @Nested
    @DisplayName("테스트")
    class LongRun {
        private final LongRunRule rule = new LongRunRule();

        @Test
    @DisplayName("테스트")
        void excludesWhenFiveConsecutiveAtStart() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 5, 20))).isTrue();
        }

        @Test
    @DisplayName("테스트")
        void excludesWhenFiveConsecutiveInMiddle() {
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 12, 13, 14, 40))).isTrue();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeFourConsecutive() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 10, 20))).isFalse();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeNormalCombination() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 9, 15, 21, 33, 45))).isFalse();
        }
    }

    @Nested
    @DisplayName("테스트")
    class SingleDecade {
        private final SingleDecadeRule rule = new SingleDecadeRule();

        @Test
    @DisplayName("테스트")
        void excludesWhenFiveInOneDecadeBucket() {
            // 10~19 甕곌쑵沅??5揶?
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 13, 17, 19, 40))).isTrue();
        }

        @Test
    @DisplayName("테스트")
        void excludesWhenFiveInOnesDecade() {
            // 1~9 甕곌쑵沅??5揶?(1,3,5,7,9) ????뽰뇚
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 9, 40))).isTrue();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeWhenFourInOneDecade() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 20, 40))).isFalse();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeWhenDecadesAreSpread() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 11, 22, 33, 41, 45))).isFalse();
        }

        @Test
    @DisplayName("테스트")
        void excludesWhenFiveInForties() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 40, 41, 42, 43, 44))).isTrue();
        }
    }

    @Nested
    @DisplayName("테스트")
    class PastWinning {

        private static final LottoCombination KNOWN = LottoCombination.of(1, 7, 13, 22, 34, 45);

        private PastWinningRule ruleWith(LottoCombination... pastWinnings) {
            PastWinningCache cache = new PastWinningCache();
            cache.replace(java.util.List.of(pastWinnings));
            return new PastWinningRule(cache);
        }

        @Test
    @DisplayName("테스트")
        void excludesCombinationInCache() {
            assertThat(ruleWith(KNOWN).shouldExclude(KNOWN)).isTrue();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeCombinationNotInCache() {
            assertThat(ruleWith(KNOWN).shouldExclude(LottoCombination.of(2, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
    @DisplayName("테스트")
        void doesNotExcludeWhenCacheIsEmpty() {
            PastWinningRule rule = new PastWinningRule(new PastWinningCache());
            assertThat(rule.shouldExclude(KNOWN)).isFalse();
        }
    }
}

