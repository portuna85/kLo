package com.kraft.lotto.feature.winningnumber.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.lotto.feature.winningnumber.domain.LottoCombination;
import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MockLottoApiClientTest {

    @Test
    void fetch_미추첨_회차는_empty() {
        MockLottoApiClient client = new MockLottoApiClient(1100);

        assertThat(client.fetch(0)).isEmpty();
        assertThat(client.fetch(1101)).isEmpty();
        assertThat(client.fetch(-1)).isEmpty();
    }

    @Test
    void fetch_같은_round는_같은_조합을_반환() {
        MockLottoApiClient client = new MockLottoApiClient(1100);

        Optional<WinningNumber> a = client.fetch(1100);
        Optional<WinningNumber> b = client.fetch(1100);

        assertThat(a).isPresent();
        assertThat(b).isPresent();
        assertThat(a.get().combination().numbers()).isEqualTo(b.get().combination().numbers());
        assertThat(a.get().bonusNumber()).isEqualTo(b.get().bonusNumber());
    }

    @Test
    void fetch_보너스는_본번호와_겹치지_않는다() {
        MockLottoApiClient client = new MockLottoApiClient(2000);

        for (int round = 1; round <= 2000; round++) {
            WinningNumber wn = client.fetch(round).orElseThrow();
            Set<Integer> mains = new HashSet<>(wn.combination().numbers());
            assertThat(mains).hasSize(LottoCombination.SIZE);
            assertThat(mains).doesNotContain(wn.bonusNumber());
        }
    }
}
