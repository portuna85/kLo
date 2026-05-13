package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("모의 로또 API 클라이언트 테스트")
class MockLottoApiClientTest {

    @Test
    @DisplayName("추첨되지 않은 회차에 대해서는 빈 값을 반환한다")
    void fetchReturnsEmptyForUndrawnRound() {
        MockLottoApiClient client = new MockLottoApiClient(1100);

        assertThat(client.fetch(0)).isEmpty();
        assertThat(client.fetch(1101)).isEmpty();
        assertThat(client.fetch(-1)).isEmpty();
    }

    @Test
    @DisplayName("동일한 회차에 대해서는 동일한 조합을 반환한다")
    void fetchReturnsSameCombinationForSameRound() {
        MockLottoApiClient client = new MockLottoApiClient(1100);

        Optional<WinningNumber> a = client.fetch(1100);
        Optional<WinningNumber> b = client.fetch(1100);

        assertThat(a).isPresent();
        assertThat(b).isPresent();
        assertThat(a.get().combination().numbers()).isEqualTo(b.get().combination().numbers());
        assertThat(a.get().bonusNumber()).isEqualTo(b.get().bonusNumber());
    }

    @Test
    @DisplayName("보너스 번호는 당첨 번호와 겹치지 않는다")
    void fetchBonusDoesNotOverlapWithMainNumbers() {
        MockLottoApiClient client = new MockLottoApiClient(2000);

        for (int round = 1; round <= 2000; round++) {
            WinningNumber wn = client.fetch(round).orElseThrow();
            Set<Integer> mains = new HashSet<>(wn.combination().numbers());
            assertThat(mains).hasSize(LottoCombination.SIZE);
            assertThat(mains).doesNotContain(wn.bonusNumber());
        }
    }
}

