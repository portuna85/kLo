package com.kraft.lotto.feature.recommend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

    @DisplayName("tests for ExclusionRulesTest")
class ExclusionRulesTest {

    @Nested
    @DisplayName("tests for BirthdayBias")
    class BirthdayBias {
        private final BirthdayBiasRule rule = new BirthdayBiasRule();

        @Test
    @DisplayName("excludes when all numbers are at most31")
        void excludesWhenAllNumbersAreAtMost31() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 31))).isTrue();
        }

        @Test
    @DisplayName("does not exclude when any number is at least32")
        void doesNotExcludeWhenAnyNumberIsAtLeast32() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 32))).isFalse();
        }

        @Test
    @DisplayName("does not exclude when contains boundary32")
        void doesNotExcludeWhenContainsBoundary32() {
            assertThat(rule.shouldExclude(LottoCombination.of(2, 5, 10, 20, 30, 32))).isFalse();
        }
    }

    @Nested
    @DisplayName("tests for Arithmetic")
    class Arithmetic {
        private final ArithmeticSequenceRule rule = new ArithmeticSequenceRule();

        @Test
    @DisplayName("excludes arithmetic sequence with diff7")
        void excludesArithmeticSequenceWithDiff7() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 36))).isTrue();
        }

        @Test
    @DisplayName("excludes arithmetic sequence with diff3")
        void excludesArithmeticSequenceWithDiff3() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 6, 9, 12, 15, 18))).isTrue();
        }

        @Test
    @DisplayName("does not exclude normal combination")
        void doesNotExcludeNormalCombination() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
    @DisplayName("does not exclude partial arithmetic")
        void doesNotExcludePartialArithmetic() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 37))).isFalse();
        }
    }

    @Nested
    @DisplayName("tests for LongRun")
    class LongRun {
        private final LongRunRule rule = new LongRunRule();

        @Test
    @DisplayName("excludes when five consecutive at start")
        void excludesWhenFiveConsecutiveAtStart() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 5, 20))).isTrue();
        }

        @Test
    @DisplayName("excludes when five consecutive in middle")
        void excludesWhenFiveConsecutiveInMiddle() {
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 12, 13, 14, 40))).isTrue();
        }

        @Test
    @DisplayName("does not exclude four consecutive")
        void doesNotExcludeFourConsecutive() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 10, 20))).isFalse();
        }

        @Test
    @DisplayName("does not exclude normal combination")
        void doesNotExcludeNormalCombination() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 9, 15, 21, 33, 45))).isFalse();
        }
    }

    @Nested
    @DisplayName("tests for SingleDecade")
    class SingleDecade {
        private final SingleDecadeRule rule = new SingleDecadeRule();

        @Test
    @DisplayName("excludes when five in one decade bucket")
        void excludesWhenFiveInOneDecadeBucket() {
            // 10~19 ?뺢퀗?득쾮??5??
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 13, 17, 19, 40))).isTrue();
        }

        @Test
    @DisplayName("excludes when five in ones decade")
        void excludesWhenFiveInOnesDecade() {
            // 1~9 ?뺢퀗?득쾮??5??(1,3,5,7,9) ????戮곕뇶
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 9, 40))).isTrue();
        }

        @Test
    @DisplayName("does not exclude when four in one decade")
        void doesNotExcludeWhenFourInOneDecade() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 20, 40))).isFalse();
        }

        @Test
    @DisplayName("does not exclude when decades are spread")
        void doesNotExcludeWhenDecadesAreSpread() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 11, 22, 33, 41, 45))).isFalse();
        }

        @Test
    @DisplayName("excludes when five in forties")
        void excludesWhenFiveInForties() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 40, 41, 42, 43, 44))).isTrue();
        }
    }

    @Nested
    @DisplayName("tests for PastWinning")
    class PastWinning {

        private static final LottoCombination KNOWN = LottoCombination.of(1, 7, 13, 22, 34, 45);

        private PastWinningRule ruleWith(LottoCombination... pastWinnings) {
            PastWinningCache cache = new PastWinningCache();
            cache.replace(java.util.List.of(pastWinnings));
            return new PastWinningRule(cache);
        }

        @Test
    @DisplayName("excludes combination in cache")
        void excludesCombinationInCache() {
            assertThat(ruleWith(KNOWN).shouldExclude(KNOWN)).isTrue();
        }

        @Test
    @DisplayName("does not exclude combination not in cache")
        void doesNotExcludeCombinationNotInCache() {
            assertThat(ruleWith(KNOWN).shouldExclude(LottoCombination.of(2, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
    @DisplayName("does not exclude when cache is empty")
        void doesNotExcludeWhenCacheIsEmpty() {
            PastWinningRule rule = new PastWinningRule(new PastWinningCache());
            assertThat(rule.shouldExclude(KNOWN)).isFalse();
        }
    }
}

