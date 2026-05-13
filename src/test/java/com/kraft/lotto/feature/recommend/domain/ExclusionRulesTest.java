package com.kraft.lotto.feature.recommend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("제외 규칙 테스트")
class ExclusionRulesTest {

    @Nested
    @DisplayName("생일 편향 규칙")
    class BirthdayBias {
        private final BirthdayBiasRule rule = new BirthdayBiasRule();

        @Test
        @DisplayName("모든 번호가 31 이하인 경우 제외한다")
        void excludesWhenAllNumbersAreAtMost31() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 31))).isTrue();
        }

        @Test
        @DisplayName("하나라도 32 이상인 번호가 있으면 제외하지 않는다")
        void doesNotExcludeWhenAnyNumberIsAtLeast32() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 32))).isFalse();
        }

        @Test
        @DisplayName("경계값인 32를 포함하면 제외하지 않는다")
        void doesNotExcludeWhenContainsBoundary32() {
            assertThat(rule.shouldExclude(LottoCombination.of(2, 5, 10, 20, 30, 32))).isFalse();
        }
    }

    @Nested
    @DisplayName("산술 수열 규칙")
    class Arithmetic {
        private final ArithmeticSequenceRule rule = new ArithmeticSequenceRule();

        @Test
        @DisplayName("공차가 7인 산술 수열을 제외한다")
        void excludesArithmeticSequenceWithDiff7() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 36))).isTrue();
        }

        @Test
        @DisplayName("공차가 3인 산술 수열을 제외한다")
        void excludesArithmeticSequenceWithDiff3() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 6, 9, 12, 15, 18))).isTrue();
        }

        @Test
        @DisplayName("일반적인 조합은 제외하지 않는다")
        void doesNotExcludeNormalCombination() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
        @DisplayName("일부분만 산술 수열인 경우 제외하지 않는다")
        void doesNotExcludePartialArithmetic() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 37))).isFalse();
        }
    }

    @Nested
    @DisplayName("연속 번호 규칙")
    class LongRun {
        private final LongRunRule rule = new LongRunRule();

        @Test
        @DisplayName("시작부터 5개 번호가 연속된 경우 제외한다")
        void excludesWhenFiveConsecutiveAtStart() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 5, 20))).isTrue();
        }

        @Test
        @DisplayName("중간에 5개 번호가 연속된 경우 제외한다")
        void excludesWhenFiveConsecutiveInMiddle() {
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 12, 13, 14, 40))).isTrue();
        }

        @Test
        @DisplayName("4개 번호만 연속된 경우 제외하지 않는다")
        void doesNotExcludeFourConsecutive() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 10, 20))).isFalse();
        }

        @Test
        @DisplayName("일반적인 조합은 제외하지 않는다")
        void doesNotExcludeNormalCombination() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 9, 15, 21, 33, 45))).isFalse();
        }
    }

    @Nested
    @DisplayName("단일 10단위 편향 규칙")
    class SingleDecade {
        private final SingleDecadeRule rule = new SingleDecadeRule();

        @Test
        @DisplayName("한 10단위 영역에 5개 번호가 몰린 경우 제외한다")
        void excludesWhenFiveInOneDecadeBucket() {
            // 10~19 ?뺢퀗?득쾮??5??
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 13, 17, 19, 40))).isTrue();
        }

        @Test
        @DisplayName("1~9 영역에 5개 번호가 몰린 경우 제외한다")
        void excludesWhenFiveInOnesDecade() {
            // 1~9 ?뺢퀗?득쾮??5??(1,3,5,7,9) ????戮곕뇶
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 9, 40))).isTrue();
        }

        @Test
        @DisplayName("한 영역에 4개 번호가 있는 경우 제외하지 않는다")
        void doesNotExcludeWhenFourInOneDecade() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 20, 40))).isFalse();
        }

        @Test
        @DisplayName("여러 10단위에 번호가 분산된 경우 제외하지 않는다")
        void doesNotExcludeWhenDecadesAreSpread() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 11, 22, 33, 41, 45))).isFalse();
        }

        @Test
        @DisplayName("40번대 영역에 5개 번호가 몰린 경우 제외한다")
        void excludesWhenFiveInForties() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 40, 41, 42, 43, 44))).isTrue();
        }
    }

    @Nested
    @DisplayName("기존 당첨 번호 규칙")
    class PastWinning {

        private static final LottoCombination KNOWN = LottoCombination.of(1, 7, 13, 22, 34, 45);

        private PastWinningRule ruleWith(LottoCombination... pastWinnings) {
            PastWinningCache cache = new PastWinningCache();
            cache.replace(java.util.List.of(pastWinnings));
            return new PastWinningRule(cache);
        }

        @Test
        @DisplayName("캐시에 있는 당첨 조합이면 제외한다")
        void excludesCombinationInCache() {
            assertThat(ruleWith(KNOWN).shouldExclude(KNOWN)).isTrue();
        }

        @Test
        @DisplayName("캐시에 없는 조합이면 제외하지 않는다")
        void doesNotExcludeCombinationNotInCache() {
            assertThat(ruleWith(KNOWN).shouldExclude(LottoCombination.of(2, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
        @DisplayName("캐시가 비어 있으면 제외하지 않는다")
        void doesNotExcludeWhenCacheIsEmpty() {
            PastWinningRule rule = new PastWinningRule(new PastWinningCache());
            assertThat(rule.shouldExclude(KNOWN)).isFalse();
        }
    }
}

