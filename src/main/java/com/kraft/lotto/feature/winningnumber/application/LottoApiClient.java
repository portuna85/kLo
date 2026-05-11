package com.kraft.lotto.feature.winningnumber.application;

import com.kraft.lotto.feature.winningnumber.domain.WinningNumber;
import java.util.Optional;

/**
 * External lottery winning-number API client.
 */
public interface LottoApiClient {

    Optional<WinningNumber> fetch(int round);
}
