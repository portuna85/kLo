package com.kraft.lotto.feature.recommend.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ConstraintAwareLottoNumberGeneratorTest {

    @Test
    void throwsTimeoutWhenConstraintsAreImpossible() {
        var generator = new ConstraintAwareLottoNumberGenerator(
                new Random(42L),
                45,
                2,
                1
        );

        assertThatThrownBy(generator::generate)
                .isInstanceOf(RecommendGenerationTimeoutException.class);
    }
}
