package com.kraft.lotto.feature.recommend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExclusionRulesTest {

    @Nested
    @DisplayName("BirthdayBiasRule")
    class BirthdayBias {
        private final BirthdayBiasRule rule = new BirthdayBiasRule();

        @Test
        void 모든_번호가_31이하면_제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 31))).isTrue();
        }

        @Test
        void 한_개라도_32이상이면_미제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 29, 32))).isFalse();
        }

        @Test
        void 경계값_32_포함_미제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(2, 5, 10, 20, 30, 32))).isFalse();
        }
    }

    @Nested
    @DisplayName("ArithmeticSequenceRule")
    class Arithmetic {
        private final ArithmeticSequenceRule rule = new ArithmeticSequenceRule();

        @Test
        void 공차_7_등차수열_제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 36))).isTrue();
        }

        @Test
        void 공차_3_등차수열_제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 6, 9, 12, 15, 18))).isTrue();
        }

        @Test
        void 일반_조합_미제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
        void 부분만_등차이면_미제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 8, 15, 22, 29, 37))).isFalse();
        }
    }

    @Nested
    @DisplayName("LongRunRule")
    class LongRun {
        private final LongRunRule rule = new LongRunRule();

        @Test
        void 연속_5개_시작에_있으면_제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 5, 20))).isTrue();
        }

        @Test
        void 연속_5개_중간에_있으면_제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 12, 13, 14, 40))).isTrue();
        }

        @Test
        void 연속_4개는_미제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 2, 3, 4, 10, 20))).isFalse();
        }

        @Test
        void 일반_조합_미제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 9, 15, 21, 33, 45))).isFalse();
        }
    }

    @Nested
    @DisplayName("SingleDecadeRule")
    class SingleDecade {
        private final SingleDecadeRule rule = new SingleDecadeRule();

        @Test
        void 한_십의자리에_5개_몰리면_제외() {
            // 10~19 버킷에 5개
            assertThat(rule.shouldExclude(LottoCombination.of(10, 11, 13, 17, 19, 40))).isTrue();
        }

        @Test
        void 한_십의자리_5개이면_제외() {
            // 1~9 버킷에 5개 (1,3,5,7,9) → 제외
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 9, 40))).isTrue();
        }

        @Test
        void 한_십의자리_4개이면_미제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 3, 5, 7, 20, 40))).isFalse();
        }

        @Test
        void 십의자리_분산이면_미제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(3, 11, 22, 33, 41, 45))).isFalse();
        }

        @Test
        void _40대_버킷_5개이면_제외() {
            assertThat(rule.shouldExclude(LottoCombination.of(1, 40, 41, 42, 43, 44))).isTrue();
        }
    }

    @Nested
    @DisplayName("PastWinningRule")
    class PastWinning {

        @Test
        void 캐시에_있는_조합은_제외() {
            PastWinningCache cache = new PastWinningCache();
            cache.replace(java.util.List.of(LottoCombination.of(1, 7, 13, 22, 34, 45)));
            PastWinningRule rule = new PastWinningRule(cache);
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 34, 45))).isTrue();
        }

        @Test
        void 캐시에_없는_조합은_미제외() {
            PastWinningCache cache = new PastWinningCache();
            cache.replace(java.util.List.of(LottoCombination.of(1, 7, 13, 22, 34, 45)));
            PastWinningRule rule = new PastWinningRule(cache);
            assertThat(rule.shouldExclude(LottoCombination.of(2, 7, 13, 22, 34, 45))).isFalse();
        }

        @Test
        void 빈_캐시면_항상_미제외() {
            PastWinningRule rule = new PastWinningRule(new PastWinningCache());
            assertThat(rule.shouldExclude(LottoCombination.of(1, 7, 13, 22, 34, 45))).isFalse();
        }
    }
}
